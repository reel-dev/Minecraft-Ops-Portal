package de.elbracht.smp.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class TestDockerPlayerFilesTest {
    @Test void fileCommandsAreFixedAndUnavailableOutsideTheTestController() {
        var mapper = new ObjectMapper();
        var control = new TestDockerControl(StatusServiceTest.properties(25585, "docker-test-control"), mapper);
        assertThat(control.playerFileCommand(DockerControl.PlayerFile.WHITELIST)).containsExactly("docker", "--context", "desktop-linux",
            "container", "exec", "--user", "1000", "--", "elbracht-smp-control-test-minecraft", "/bin/cat", "/data/whitelist.json");
        assertThat(control.playerFileCommand(DockerControl.PlayerFile.OPERATORS)).endsWith("/data/ops.json");
        assertThatThrownBy(() -> control.playerFileCommand(null)).isInstanceOf(IllegalArgumentException.class);
        var production = new TestDockerControl(HostReadOnlyProbeTest.properties("sha256:" + "a".repeat(64)), mapper);
        assertThatThrownBy(() -> production.playerFileCommand(DockerControl.PlayerFile.WHITELIST)).isInstanceOf(IllegalStateException.class);
    }
}
