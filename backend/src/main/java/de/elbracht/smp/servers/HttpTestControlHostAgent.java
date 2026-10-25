package de.elbracht.smp.servers;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.elbracht.smp.operations.OperationType;
import java.net.*;
import java.net.http.*;
import java.nio.file.Files;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Nur für das explizite lokale Testserverprofil, niemals für Produktion. */
public final class HttpTestControlHostAgent implements HostAgent, AutoCloseable {
    private final AgentProperties properties;
    private final ObjectMapper mapper;
    private final HttpStatusHostAgent reader;
    private final HttpClient client;
    private final String authorization;
    public HttpTestControlHostAgent(AgentProperties properties, ObjectMapper mapper) {
        this.properties = properties; this.mapper = mapper;
        if (properties.baseUrl() == null || !"127.0.0.1".equals(properties.baseUrl().getHost())) throw new IllegalStateException("Teststeuerung nur über Loopback");
        this.reader = new HttpStatusHostAgent(properties, mapper, Clock.systemUTC(), true);
        try { this.authorization = "Bearer " + Files.readString(properties.tokenFile()).strip(); }
        catch (Exception error) { throw new IllegalStateException("Agent-Secret nicht verfügbar"); }
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).followRedirects(HttpClient.Redirect.NEVER)
            .proxy(new ProxySelector() {
                public List<Proxy> select(URI uri) { return List.of(Proxy.NO_PROXY); }
                public void connectFailed(URI uri, SocketAddress address, java.io.IOException error) { }
            }).build();
    }
    public boolean operationsAvailable() { return true; }
    public String statusSource() { return "HOST_AGENT_TEST_CONTROL"; }
    public Status observe(String server) { return reader.observe(server); }
    public void execute(String server, OperationType operation) { throw new IllegalStateException("Persistente Job-ID erforderlich"); }
    public void execute(String server, OperationType operation, UUID id) {
        if (!properties.serverId().equals(server)) throw new IllegalStateException("Falscher Zielserver");
        java.util.concurrent.CompletableFuture<HttpResponse<byte[]>> pending = null;
        try {
            var uri = properties.baseUrl().resolve("/agent/v1/servers/" + server + "/operations");
            var request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(55)).header("Authorization", authorization)
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofByteArray(mapper.writeValueAsBytes(Map.of("operationId", id, "type", operation)))).build();
            pending = client.sendAsync(request, info -> new LimitedBodySubscriber(16_384));
            var result = pending.get(55, TimeUnit.SECONDS);
            var json = mapper.readTree(result.body());
            if (result.statusCode() != 200 || !id.toString().equals(json.path("operationId").asText())
                || !operation.name().equals(json.path("type").asText()) || !"SUCCEEDED".equals(json.path("state").asText())) throw new IllegalStateException();
            var status = reader.observe(server);
            if (status.lifecycle() != (operation == OperationType.STOP ? Lifecycle.OFFLINE : Lifecycle.RUNNING)) throw new IllegalStateException();
        } catch (Exception error) {
            if (error instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new IllegalStateException("Testergebnis konnte nicht bestätigt werden");
        } finally { if (pending != null && !pending.isDone()) pending.cancel(true); }
    }
    public void close() { client.close(); reader.close(); }
}
