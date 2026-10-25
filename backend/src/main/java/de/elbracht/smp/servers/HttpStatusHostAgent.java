package de.elbracht.smp.servers;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.elbracht.smp.operations.OperationType;
import java.io.IOException;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Ausschließlich lesender Transport. Auch bei gültiger Konfiguration keine Mutation. */
public final class HttpStatusHostAgent implements HostAgent, AutoCloseable {
    private static final int MAX_BODY_BYTES = 16_384;
    private final AgentProperties properties;
    private final ObjectMapper mapper;
    private final HttpClient client;
    private final Clock clock;
    private final URI statusUri;
    private final String authorization;

    public HttpStatusHostAgent(AgentProperties properties, ObjectMapper mapper, Clock clock, boolean local) {
        validate(properties, local);
        this.properties = properties;
        this.mapper = mapper.copy().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        this.clock = clock;
        this.statusUri = properties.baseUrl().resolve("/agent/v1/servers/" + properties.serverId() + "/status");
        this.authorization = "Bearer " + readToken(properties);
        this.client = HttpClient.newBuilder()
            .connectTimeout(properties.connectTimeout())
            .followRedirects(HttpClient.Redirect.NEVER)
            .proxy(new ProxySelector() {
                @Override public List<Proxy> select(URI uri) { return List.of(Proxy.NO_PROXY); }
                @Override public void connectFailed(URI uri, SocketAddress address, IOException error) { }
            }).build();
    }

    @Override public boolean operationsAvailable() { return false; }
    @Override public String statusSource() { return "HOST_AGENT_READ_ONLY"; }
    @Override public void close() { client.close(); }
    @Override public void execute(String serverId, OperationType operation) {
        throw new IllegalStateException("Lesender Agentadapter erlaubt keine Serveraktionen");
    }

    @Override public Status observe(String serverId) {
        if (!properties.serverId().equals(serverId)) return unknown(serverId, "AGENT_SERVER_NOT_CONFIGURED", null);
        HttpRequest request = HttpRequest.newBuilder(statusUri).timeout(properties.requestTimeout())
            .header("Authorization", authorization).header("Accept", "application/json").GET().build();
        var pending = client.sendAsync(request, info -> new LimitedBodySubscriber(MAX_BODY_BYTES));
        try {
            // Explizite Gesamtdeadline schließt einen langsamen/chunked Response-Body ein.
            var response = pending.get(properties.requestTimeout().toMillis(), TimeUnit.MILLISECONDS);
            if (response.statusCode() == 401 || response.statusCode() == 403)
                return unknown(serverId, "AGENT_AUTH_FAILED", null);
            if (response.statusCode() != 200) return unknown(serverId, "AGENT_HTTP_ERROR", null);
            String contentType = response.headers().firstValue("Content-Type").orElse("");
            if (!contentType.split(";", 2)[0].trim().equalsIgnoreCase("application/json"))
                return unknown(serverId, "AGENT_INVALID_RESPONSE", null);
            var snapshot = mapper.readValue(response.body(), Snapshot.class);
            if (!valid(snapshot, serverId)) return unknown(serverId, "AGENT_INVALID_RESPONSE", null);
            Instant observedAt = snapshot.observedAt();
            if (observedAt.isBefore(clock.instant().minus(properties.maxStatusAge()))
                || observedAt.isAfter(clock.instant().plusSeconds(5)))
                return unknown(serverId, "AGENT_STALE_STATUS", observedAt);
            return new Status(serverId, snapshot.lifecycle(), snapshot.health(), snapshot.activity(),
                snapshot.minecraftVersion(), snapshot.loader(), snapshot.loaderVersion(), observedAt,
                null, snapshot.playersOnline());
        } catch (TimeoutException error) {
            return unknown(serverId, "AGENT_TIMEOUT", null);
        } catch (ExecutionException error) {
            return unknown(serverId, error.getCause() instanceof HttpTimeoutException ? "AGENT_TIMEOUT" : "AGENT_UNAVAILABLE", null);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return unknown(serverId, "AGENT_INTERRUPTED", null);
        } catch (Exception error) {
            // Kein Remote-Body, Token, Pfad, Hostname oder Exceptiontext in API/Logs.
            return unknown(serverId, "AGENT_UNAVAILABLE", null);
        } finally {
            if (!pending.isDone()) pending.cancel(true);
        }
    }

    public record Snapshot(String serverId, Lifecycle lifecycle, Health health, Activity activity,
        String minecraftVersion, String loader, String loaderVersion, Instant observedAt,
        Integer playersOnline, Boolean minecraftReady) {}

    private boolean valid(Snapshot value, String serverId) {
        if (value == null || !serverId.equals(value.serverId()) || value.lifecycle() == null
            || value.health() == null || value.activity() == null || value.observedAt() == null
            || value.minecraftReady() == null || (value.playersOnline() != null && value.playersOnline() < 0)) return false;
        if (value.lifecycle() == Lifecycle.RUNNING && !value.minecraftReady()) return false;
        if (value.minecraftReady() && value.lifecycle() != Lifecycle.RUNNING) return false;
        if (value.lifecycle() == Lifecycle.RUNNING && value.health() == Health.UNKNOWN) return false;
        return bounded(value.minecraftVersion()) && bounded(value.loader()) && bounded(value.loaderVersion());
    }
    private boolean bounded(String value) { return value == null || (value.length() <= 64 && value.matches("[A-Za-z0-9._+ -]+")); }
    private static Status unknown(String server, String code, Instant observedAt) {
        return new Status(server, Lifecycle.UNKNOWN, Health.UNKNOWN, Activity.NONE, null, null, null, observedAt, code, null);
    }
    private static void validate(AgentProperties properties, boolean local) {
        URI uri = properties.baseUrl();
        boolean loopback = uri != null && ("127.0.0.1".equals(uri.getHost()) || "[::1]".equals(uri.getHost()));
        if (uri == null || uri.getHost() == null || uri.getUserInfo() != null || uri.getRawQuery() != null
            || uri.getRawFragment() != null || !(uri.getPath().isEmpty() || "/".equals(uri.getPath()))
            || !("https".equals(uri.getScheme()) || (local && loopback && "http".equals(uri.getScheme()))))
            throw new IllegalStateException("Agent benötigt eine HTTPS-Origin ohne Pfad oder Zugangsdaten; lokal ist HTTP nur auf Loopback erlaubt.");
        if (properties.serverId() == null || !properties.serverId().matches("[A-Za-z0-9_-]{1,64}") || properties.tokenFile() == null)
            throw new IllegalStateException("Agent benötigt feste Server-ID und Token-Datei.");
        for (Duration timeout : List.of(properties.connectTimeout(), properties.requestTimeout(), properties.maxStatusAge())) {
            if (timeout.compareTo(Duration.ofMillis(1)) < 0 || timeout.compareTo(Duration.ofSeconds(60)) > 0)
                throw new IllegalStateException("Agent-Zeitgrenzen müssen zwischen 1 Millisekunde und 60 Sekunden liegen.");
        }
    }
    private static String readToken(AgentProperties properties) {
        try (var input = Files.newInputStream(properties.tokenFile())) {
            byte[] bytes = input.readNBytes(4097);
            if (bytes.length > 4096) throw new IOException();
            String token = new String(bytes, StandardCharsets.UTF_8).strip();
            if (!token.matches("[A-Za-z0-9._~-]{32,4096}")) throw new IOException();
            return token;
        } catch (IOException error) {
            throw new IllegalStateException("Agent-Token-Datei fehlt oder enthält kein gültiges Token.");
        }
    }
}
