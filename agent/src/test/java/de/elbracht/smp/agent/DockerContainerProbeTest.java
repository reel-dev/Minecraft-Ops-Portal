package de.elbracht.smp.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class DockerContainerProbeTest {
    private final ObjectMapper mapper = new ObjectMapper();
    @Test void commandHasOnlyFixedInspectionAndNoShellOrEnvironmentDump() {
        var probe = new DockerContainerProbe(StatusServiceTest.properties(25585, "docker-read-only"), mapper);
        assertThat(probe.command()).containsExactly("docker", "--context", "desktop-linux", "container", "inspect", "--format",
            DockerContainerProbe.FORMAT, "--", "elbracht-smp-control-test-minecraft");
        assertThat(DockerContainerProbe.FORMAT).doesNotContain(".Config.Env", ".State.Health", "exec", "RCON");
    }
    @Test void requiresBothTestLabelsAndExactLoopbackPortBinding() throws Exception {
        var probe = new DockerContainerProbe(StatusServiceTest.properties(25585, "docker-read-only"), mapper);
        String json = """
            {"status":"running","running":true,"paused":false,"restarting":false,"dead":false,
             "environment":"test","serverId":"main","ports":{"25565/tcp":[{"HostIp":"127.0.0.1","HostPort":"25585"}]}}
            """;
        assertThat(probe.parse(mapper.readTree(json)).testTarget()).isTrue();
        assertThat(probe.parse(mapper.readTree(json)).portMatches()).isTrue();
        assertThat(probe.parse(mapper.readTree(json.replace("127.0.0.1", "0.0.0.0"))).portMatches()).isFalse();
        assertThat(probe.parse(mapper.readTree(json.replace("\"test\"", "\"production\""))).testTarget()).isFalse();
        assertThat(probe.parse(mapper.readTree(json.replace("\"main\"", "\"other\""))).testTarget()).isFalse();
        assertThatThrownBy(() -> probe.parse(mapper.readTree(json.replace("\"running\":true", "\"running\":\"true\"")))).isInstanceOf(java.io.IOException.class);
    }
    @Test void unsafeConfigurationIsRejected() {
        assertThatThrownBy(() -> new AgentProperties("docker-read-only", "main", java.nio.file.Path.of("unused"),
            "server;rm", "default", 25585, java.time.Duration.ofSeconds(1), java.time.Duration.ofSeconds(1)))
            .isInstanceOf(IllegalStateException.class);
    }
    @Test void parsesOnlyTheEightSelectedDockerValues() throws Exception {
        var probe = new DockerContainerProbe(StatusServiceTest.properties(25585, "docker-read-only"), mapper);
        byte[] output = "\"running\"\ntrue\nfalse\nfalse\nfalse\n{\"25565/tcp\":[{\"HostIp\":\"127.0.0.1\",\"HostPort\":\"25585\"}]}\n\"test\"\n\"main\"\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(probe.parseOutput(output).portMatches()).isTrue();
        assertThat(probe.parseOutput(output).testTarget()).isTrue();
        assertThatThrownBy(() -> probe.parseOutput("{}".getBytes())).isInstanceOf(java.io.IOException.class);
    }
}
