package de.elbracht.smp.agent;

import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class StatusServiceTest {
    static AgentProperties properties(int port, String mode) {
        return new AgentProperties(mode, "main", Path.of("unused"), "elbracht-smp-control-test-minecraft", "desktop-linux", port,
            Duration.ofSeconds(1), Duration.ofMillis(300));
    }
    @Test void disconnectedNeverInvokesDockerOrMinecraft() {
        var service = new StatusService(properties(25585, "disconnected"), () -> { throw new AssertionError(); }, () -> { throw new AssertionError(); });
        assertThat(service.observe().lifecycle()).isEqualTo("UNKNOWN");
    }
    @Test void wrongTargetAndWrongPortNeverProbeMinecraft() {
        for (var container : new ContainerProbe.State[] {
            new ContainerProbe.State("running", true, false, false, false, false, true),
            new ContainerProbe.State("running", true, false, false, false, true, false) }) {
            var service = new StatusService(properties(25585, "docker-read-only"), () -> container, () -> { throw new AssertionError(); });
            assertThat(service.observe().lifecycle()).isEqualTo("UNKNOWN");
        }
    }
    @Test void stoppedContainerDoesNotProbeMinecraft() {
        var service = new StatusService(properties(25585, "docker-read-only"),
            () -> new ContainerProbe.State("exited", false, false, false, false, true, false), () -> { throw new AssertionError(); });
        assertThat(service.observe().lifecycle()).isEqualTo("OFFLINE");
    }
    @Test void runningDockerWithoutMinecraftReplyIsUnknown() {
        var service = new StatusService(properties(25585, "docker-read-only"),
            () -> new ContainerProbe.State("running", true, false, false, false, true, true), () -> { throw new java.io.IOException("private-error"); });
        var result = service.observe();
        assertThat(result.lifecycle()).isEqualTo("UNKNOWN"); assertThat(result.health()).isEqualTo("DEGRADED");
        assertThat(result.minecraftReady()).isFalse(); assertThat(result.toString()).doesNotContain("private-error");
    }
    @Test void onlyMinecraftReadinessProducesRunningWithMeasuredValues() {
        var service = new StatusService(properties(25585, "docker-read-only"),
            () -> new ContainerProbe.State("running", true, false, false, false, true, true), () -> new MinecraftProbe.Result("test-version", 3));
        var result = service.observe();
        assertThat(result.lifecycle()).isEqualTo("RUNNING"); assertThat(result.minecraftReady()).isTrue();
        assertThat(result.minecraftVersion()).isEqualTo("test-version"); assertThat(result.playersOnline()).isEqualTo(3);
        assertThat(result.loader()).isNull(); assertThat(result.loaderVersion()).isNull();
    }
    @Test void concurrentStatusRequestsDoNotRunMultipleProbes() {
        var calls = new AtomicInteger(); var serviceRef = new java.util.concurrent.atomic.AtomicReference<StatusService>();
        var service = new StatusService(properties(25585, "docker-read-only"), () -> {
            calls.incrementAndGet(); assertThat(serviceRef.get().observe().lifecycle()).isEqualTo("UNKNOWN");
            return new ContainerProbe.State("exited", false, false, false, false, true, false);
        }, () -> { throw new AssertionError(); });
        serviceRef.set(service); service.observe(); assertThat(calls.get()).isEqualTo(1);
    }
}
