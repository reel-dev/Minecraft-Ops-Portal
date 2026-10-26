package de.elbracht.smp.servers;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class HttpOnlinePlayersTest {
    @TempDir Path directory;
    private HttpServer server;
    private final AtomicReference<String> body = new AtomicReference<>();
    private final AtomicInteger hits = new AtomicInteger();
    private final Instant now = Instant.parse("2026-10-25T12:00:00Z");
    private int status = 200;
    private long delay;
    private String contentType = "application/json";
    @BeforeEach void start() throws Exception {
        Files.writeString(directory.resolve("token"), "test-only-online-token-0123456789abcdef");
        body.set(snapshot("main", "[\"Player_A\"]", now));
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/agent/v1/servers/main/players/online", exchange -> {
            hits.incrementAndGet();
            assertThat(exchange.getRequestMethod()).isEqualTo("GET");
            assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer test-only-online-token-0123456789abcdef");
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.getResponseHeaders().set("Location", "/must-not-follow");
            exchange.sendResponseHeaders(status, 0);
            try { if (delay > 0) Thread.sleep(delay); exchange.getResponseBody().write(body.get().getBytes(StandardCharsets.UTF_8)); }
            catch (InterruptedException error) { Thread.currentThread().interrupt(); }
            catch (java.io.IOException ignored) { }
            finally { exchange.close(); }
        });
        server.createContext("/must-not-follow", exchange -> { hits.incrementAndGet(); exchange.sendResponseHeaders(500, -1); exchange.close(); });
        server.start();
    }
    @AfterEach void stop() { server.stop(0); }
    private String snapshot(String id, String names, Instant time) {
        return "{\"serverId\":\"" + id + "\",\"names\":" + names + ",\"observedAt\":\"" + time + "\"}";
    }
    private HttpStatusHostAgent adapter(Duration timeout) {
        var properties = new AgentProperties("http-read-only", URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
            directory.resolve("token"), "main", Duration.ofSeconds(1), timeout, Duration.ofSeconds(30));
        return new HttpStatusHostAgent(properties, JsonMapper.builder().addModule(new JavaTimeModule()).build(), Clock.fixed(now, ZoneOffset.UTC), true);
    }
    @Test void validEmptyIsDistinctFromMissingAndForeignServerDoesNotSendRequest() {
        try (var agent = adapter(Duration.ofSeconds(1))) {
            assertThat(agent.onlinePlayers("other").names()).isNull(); assertThat(hits.get()).isZero();
            assertThat(agent.onlinePlayers("main").names()).containsExactly("Player_A");
            body.set(snapshot("main", "[]", now)); assertThat(agent.onlinePlayers("main").names()).isEmpty();
            body.set(snapshot("main", "null", now)); assertThat(agent.onlinePlayers("main").names()).isNull();
            assertThat(agent.operationsAvailable()).isFalse();
        }
    }
    @Test void invalidIdentityNamesOrTimestampNeverBecomeCurrentPlayers() {
        try (var agent = adapter(Duration.ofSeconds(1))) {
            for (String invalid : new String[]{snapshot("other", "[]", now), snapshot("main", "[\"bad<script>\"]", now),
                    snapshot("main", "[\"Steve\",\"steve\"]", now), snapshot("main", "[123]", now), snapshot("main", "[]", now.minusSeconds(31)),
                    snapshot("main", "[]", now.plusSeconds(6)), "{}", snapshot("main", "[]", now) + "{}"}) {
                body.set(invalid); assertThat(agent.onlinePlayers("main").names()).isNull();
            }
        }
    }
    @Test void redirectsErrorsWrongMediaAndOversizeAreBoundedAndRedacted() {
        try (var agent = adapter(Duration.ofSeconds(1))) {
            body.set("private-upstream-error"); status = 302;
            var redirected = agent.onlinePlayers("main");
            assertThat(redirected.names()).isNull(); assertThat(redirected.toString()).doesNotContain("private-upstream-error", "0123456789abcdef");
            assertThat(hits.get()).isEqualTo(1);
            status = 503; assertThat(agent.onlinePlayers("main").names()).isNull();
            status = 200; body.set("x".repeat(20_000)); assertThat(agent.onlinePlayers("main").names()).isNull();
            body.set(snapshot("main", "[]", now)); contentType = "text/html"; assertThat(agent.onlinePlayers("main").names()).isNull();
        }
    }
    @Test void slowBodyCannotExceedTotalDeadline() {
        delay = 500;
        try (var agent = adapter(Duration.ofMillis(150))) {
            org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(Duration.ofSeconds(2), () -> assertThat(agent.onlinePlayers("main").names()).isNull());
        }
    }
}
