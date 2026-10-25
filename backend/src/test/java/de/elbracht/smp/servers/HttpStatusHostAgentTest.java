package de.elbracht.smp.servers;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sun.net.httpserver.HttpServer;
import de.elbracht.smp.operations.OperationType;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class HttpStatusHostAgentTest {
    @TempDir Path temporary;
    private HttpServer server;
    private Path tokenFile;
    private final Instant now = Instant.parse("2026-10-24T10:00:00Z");
    private final AtomicReference<String> body = new AtomicReference<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicInteger requests = new AtomicInteger();
    private int status = 200;
    private String contentType = "application/json";
    private long delayMs;

    @BeforeEach void setup() throws Exception {
        tokenFile = temporary.resolve("agent-token");
        Files.writeString(tokenFile, "test-only-agent-token-0123456789abcdef\n");
        body.set(snapshot("main", "RUNNING", true, now));
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/agent/v1/servers/main/status", exchange -> {
            requests.incrementAndGet();
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.getResponseHeaders().set("Location", "/must-not-follow");
            byte[] bytes = body.get().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, 0); // Auch Chunked-Encoding testen.
            try {
                if (delayMs > 0) Thread.sleep(delayMs);
                exchange.getResponseBody().write(bytes);
            } catch (InterruptedException error) { Thread.currentThread().interrupt(); }
            catch (java.io.IOException ignored) { /* Timeout darf Verbindung schließen. */ }
            finally { exchange.close(); }
        });
        server.createContext("/must-not-follow", exchange -> {
            requests.incrementAndGet(); exchange.sendResponseHeaders(500, -1); exchange.close();
        });
        server.start();
    }
    @AfterEach void stop() { server.stop(0); }

    private AgentProperties properties(Duration timeout) {
        return new AgentProperties("http-read-only", URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
            tokenFile, "main", Duration.ofSeconds(1), timeout, Duration.ofSeconds(30));
    }
    private HttpStatusHostAgent adapter(Duration timeout) {
        return new HttpStatusHostAgent(properties(timeout), JsonMapper.builder().addModule(new JavaTimeModule()).build(),
            Clock.fixed(now, ZoneOffset.UTC), true);
    }
    private String snapshot(String id, String lifecycle, boolean ready, Instant time) {
        return """
            {"serverId":"%s","lifecycle":"%s","health":"OK","activity":"NONE",
             "minecraftVersion":"test-version","loader":"FABRIC","loaderVersion":"test-loader",
             "observedAt":"%s","playersOnline":2,"minecraftReady":%s}
            """.formatted(id, lifecycle, time, ready);
    }

    @Test void authenticatedStatusDoesNotEnableOperations() {
        try (var adapter = adapter(Duration.ofSeconds(1))) {
            var result = adapter.observe("main");
            assertThat(result.lifecycle()).isEqualTo(HostAgent.Lifecycle.RUNNING);
            assertThat(result.playersOnline()).isEqualTo(2);
            assertThat(result.observedAt()).isEqualTo(now);
            assertThat(authorization.get()).isEqualTo("Bearer test-only-agent-token-0123456789abcdef");
            assertThat(adapter.operationsAvailable()).isFalse();
            assertThatThrownBy(() -> adapter.execute("main", OperationType.RESTART)).isInstanceOf(IllegalStateException.class);
            assertThat(requests.get()).isEqualTo(1);
        }
    }

    @Test void foreignServerNeverMakesNetworkRequest() {
        try (var adapter = adapter(Duration.ofSeconds(1))) {
            assertThat(adapter.observe("other").reasonCode()).isEqualTo("AGENT_SERVER_NOT_CONFIGURED");
            assertThat(requests.get()).isZero();
        }
    }

    @Test void rejectsWrongServerMissingReadinessAndContradictoryReadiness() {
        try (var adapter = adapter(Duration.ofSeconds(1))) {
            for (String invalid : new String[] { snapshot("other", "RUNNING", true, now),
                snapshot("main", "RUNNING", false, now), snapshot("main", "OFFLINE", true, now),
                snapshot("main", "RUNNING", true, now).replace(",\"minecraftReady\":true", "") }) {
                body.set(invalid);
                assertThat(adapter.observe("main").reasonCode()).isEqualTo("AGENT_INVALID_RESPONSE");
            }
        }
    }

    @Test void staleAndFutureMeasurementsNeverAppearOnline() {
        try (var adapter = adapter(Duration.ofSeconds(1))) {
            for (Instant timestamp : new Instant[] { now.minusSeconds(31), now.plusSeconds(6) }) {
                body.set(snapshot("main", "RUNNING", true, timestamp));
                var result = adapter.observe("main");
                assertThat(result.reasonCode()).isEqualTo("AGENT_STALE_STATUS");
                assertThat(result.lifecycle()).isEqualTo(HostAgent.Lifecycle.UNKNOWN);
                assertThat(result.playersOnline()).isNull();
            }
        }
    }

    @Test void authenticationErrorsAndRedirectsDoNotLeakOrFollowRemoteContent() {
        body.set("sensitive-agent-error-body");
        try (var adapter = adapter(Duration.ofSeconds(1))) {
            status = 401;
            assertThat(adapter.observe("main").reasonCode()).isEqualTo("AGENT_AUTH_FAILED");
            status = 302;
            var result = adapter.observe("main");
            assertThat(result.reasonCode()).isEqualTo("AGENT_HTTP_ERROR");
            assertThat(result.toString()).doesNotContain("sensitive-agent-error-body", "0123456789abcdef");
            assertThat(requests.get()).isEqualTo(2);
        }
    }

    @Test void boundsChunkedBodyAndRejectsMalformedData() {
        try (var adapter = adapter(Duration.ofSeconds(1))) {
            body.set("x".repeat(20_000));
            assertThat(adapter.observe("main").lifecycle()).isEqualTo(HostAgent.Lifecycle.UNKNOWN);
            body.set("{broken-json");
            assertThat(adapter.observe("main").lifecycle()).isEqualTo(HostAgent.Lifecycle.UNKNOWN);
            body.set(snapshot("main", "RUNNING", true, now)); contentType = "text/html";
            assertThat(adapter.observe("main").reasonCode()).isEqualTo("AGENT_INVALID_RESPONSE");
        }
    }

    @Test void totalDeadlineIncludesSlowResponseBody() {
        delayMs = 400;
        try (var adapter = adapter(Duration.ofMillis(100))) {
            assertThat(adapter.observe("main").reasonCode()).isEqualTo("AGENT_TIMEOUT");
        }
    }

    @Test void insecureProductionOrUnboundedConfigFailsAtStartup() {
        var mapper = JsonMapper.builder().addModule(new JavaTimeModule()).build();
        assertThatThrownBy(() -> new HttpStatusHostAgent(properties(Duration.ofSeconds(1)), mapper, Clock.systemUTC(), false))
            .isInstanceOf(IllegalStateException.class);
        for (String address : new String[] { "http://example.com", "https://user:password@example.com", "https://example.com/path", "https://example.com?token=bad", "https://example.com#fragment" }) {
            var config = new AgentProperties("http-read-only", URI.create(address), tokenFile, "main", Duration.ofSeconds(1), Duration.ofSeconds(1), Duration.ofSeconds(30));
            assertThatThrownBy(() -> new HttpStatusHostAgent(config, mapper, Clock.systemUTC(), true)).isInstanceOf(IllegalStateException.class);
        }
        assertThatThrownBy(() -> adapter(Duration.ofSeconds(61))).isInstanceOf(IllegalStateException.class);
    }

    @Test void invalidSecretFileFailsWithoutExposingContents() throws Exception {
        Files.writeString(tokenFile, "secret-with-embedded\nnewline");
        assertThatThrownBy(() -> adapter(Duration.ofSeconds(1))).isInstanceOf(IllegalStateException.class)
            .hasMessageNotContaining("embedded").hasMessageNotContaining(tokenFile.toString());
    }
}
