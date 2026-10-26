package de.elbracht.smp.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.assertj.core.api.Assertions.*;

/** Opt-in ausschließlich für infra/test-server/compose.yml, kein Standard-CI-/Produktionszugriff. */
@EnabledIfEnvironmentVariable(named = "SMP_LIVE_TEST", matches = "true")
class AgentLiveProbeTest {
    @Test void inspectTheIsolatedDockerTarget() throws Exception {
        var probe = new DockerContainerProbe(StatusServiceTest.properties(25585, "docker-read-only"), new ObjectMapper());
        var result = probe.inspect();
        assertThat(result.targetMatches()).isTrue(); assertThat(result.portMatches()).isTrue(); assertThat(result.running()).isTrue();
    }
    @Test void queryTheIsolatedMinecraftServer() throws Exception {
        try (var probe = new MinecraftStatusProbe(StatusServiceTest.properties(25585, "docker-read-only"), new ObjectMapper())) {
            var result = probe.query();
            assertThat(result.minecraftVersion()).isEqualTo("26.3"); assertThat(result.playersOnline()).isGreaterThanOrEqualTo(0);
        }
    }
    @Test void verifyLifecycleGuardOfTheIsolatedContainerWithoutMutatingIt() throws Exception {
        var control = new TestDockerControl(StatusServiceTest.properties(25585, "docker-test-control"), new ObjectMapper());
        control.verifyTarget();
    }
    @Test
    @EnabledIfEnvironmentVariable(named = "SMP_LIVE_RCON", matches = "true")
    void readRealRconAndFixedPlayerFilesWithoutMutation() throws Exception {
        var mapper = new ObjectMapper();
        var control = new TestDockerControl(StatusServiceTest.properties(25585, "docker-test-control"), mapper);
        control.verifyRconTarget(25595);
        var passwordFile = java.nio.file.Path.of(System.getenv("SMP_LIVE_RCON_PASSWORD_FILE"));
        var reader = new RconPlayerReader(new RconProperties("read-only", "127.0.0.1", 25595, passwordFile, java.time.Duration.ofSeconds(5)));
        assertThat(reader.readOnlineNames()).allMatch(name -> name.matches("[A-Za-z0-9_]{3,16}"));
        for (var file : DockerControl.PlayerFile.values())
            assertThat(mapper.readTree(control.readTestPlayerFile(file)).isArray()).isTrue();
    }
}
