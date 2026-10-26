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
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class HttpTestControlHostAgentTest {
    @TempDir Path directory;
    private HttpServer server;
    private final AtomicInteger posts = new AtomicInteger();
    private boolean wrongId, wrongTarget, unknownStatus, redirect;
    private Path secret;
    private final JsonMapper mapper = JsonMapper.builder().addModule(new JavaTimeModule()).build();
    @BeforeEach void start() throws Exception {
        secret = directory.resolve("token"); Files.writeString(secret, "test-control-token-0123456789abcdef");
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/agent/v1/servers/main/operations", exchange -> {
            posts.incrementAndGet();
            assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer test-control-token-0123456789abcdef");
            var json = mapper.readTree(exchange.getRequestBody().readAllBytes());
            if (redirect) { exchange.getResponseHeaders().set("Location", "/should-not-follow"); exchange.sendResponseHeaders(302, -1); exchange.close(); return; }
            String response = "{\"operationId\":\"" + (wrongId ? UUID.randomUUID() : json.path("operationId").asText()) + "\",\"type\":\"" + json.path("type").asText() + "\",\"state\":\"SUCCEEDED\""
                + (json.hasNonNull("targetName") ? ",\"targetName\":\"" + (wrongTarget ? "OtherPlayer" : json.path("targetName").asText()) + "\"" : "") + "}";
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8); exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.createContext("/agent/v1/servers/main/status", exchange -> {
            String response = "{\"serverId\":\"main\",\"lifecycle\":\"" + (unknownStatus ? "UNKNOWN" : "RUNNING") + "\",\"health\":\"OK\",\"activity\":\"NONE\",\"observedAt\":\"" + Instant.now() + "\",\"minecraftReady\":" + !unknownStatus + "}";
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8); exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.start();
    }
    @AfterEach void stop() { server.stop(0); }
    private HttpTestControlHostAgent adapter() {
        return adapter(false);
    }
    private HttpTestControlHostAgent adapter(boolean whitelistEnabled) {
        return new HttpTestControlHostAgent(new AgentProperties("http-test-control", URI.create("http://127.0.0.1:" + server.getAddress().getPort()), secret,
            "main", Duration.ofSeconds(1), Duration.ofSeconds(1), Duration.ofSeconds(30)), mapper, whitelistEnabled);
    }
    @Test void correlatesPersistentJobIdAndVerifiesReadinessAfterSuccess() {
        try (var agent = adapter()) {
            assertThat(agent.operationsAvailable()).isTrue(); agent.execute("main", OperationType.RESTART, UUID.randomUUID());
            assertThat(posts.get()).isEqualTo(1);
            assertThatThrownBy(() -> agent.execute("main", OperationType.START)).isInstanceOf(IllegalStateException.class);
        }
    }
    @Test void mismatchedResultAndUnconfirmedTargetAreNotSuccess() {
        try (var agent = adapter()) {
            wrongId = true;
            assertThatThrownBy(() -> agent.execute("main", OperationType.RESTART, UUID.randomUUID())).isInstanceOf(IllegalStateException.class);
            wrongId = false; unknownStatus = true;
            assertThatThrownBy(() -> agent.execute("main", OperationType.RESTART, UUID.randomUUID())).isInstanceOf(IllegalStateException.class);
        }
    }
    @Test void foreignServerAndRedirectCannotWidenTarget() {
        try (var agent = adapter()) {
            assertThatThrownBy(() -> agent.execute("other", OperationType.START, UUID.randomUUID())).isInstanceOf(IllegalStateException.class);
            assertThat(posts.get()).isZero(); redirect = true;
            assertThatThrownBy(() -> agent.execute("main", OperationType.START, UUID.randomUUID())).isInstanceOf(IllegalStateException.class);
            assertThat(posts.get()).isEqualTo(1);
        }
    }
    @Test void whitelistNeedsExplicitCapabilityAndCorrelatesTheExactTarget() {
        try (var disabled = adapter()) {
            assertThat(disabled.playerOperationsAvailable()).isFalse();
            assertThatThrownBy(() -> disabled.execute("main", OperationType.WHITELIST_ADD, UUID.randomUUID(), "TestPlayer")).isInstanceOf(IllegalStateException.class);
            assertThat(posts.get()).isZero();
        }
        try (var enabled = adapter(true)) {
            assertThat(enabled.playerOperationsAvailable()).isTrue();
            enabled.execute("main", OperationType.WHITELIST_ADD, UUID.randomUUID(), "TestPlayer");
            assertThat(posts.get()).isEqualTo(1);
            wrongTarget = true;
            assertThatThrownBy(() -> enabled.execute("main", OperationType.WHITELIST_REMOVE, UUID.randomUUID(), "TestPlayer")).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> enabled.execute("main", OperationType.WHITELIST_ADD, UUID.randomUUID(), "bad;op")).isInstanceOf(IllegalStateException.class);
            assertThat(posts.get()).isEqualTo(2);
        }
    }
}
