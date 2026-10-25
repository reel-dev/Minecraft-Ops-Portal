package de.elbracht.smp.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class HostReadOnlyProbeTest {
    private static final String IMAGE = "sha256:" + "a".repeat(64);
    private final ObjectMapper mapper = new ObjectMapper();
    static AgentProperties properties(String image) {
        return new AgentProperties("docker-host-read-only", "main", Path.of("unused"), "minecraft-server", "default",
            25565, Duration.ofSeconds(1), Duration.ofMillis(300), image);
    }
    private com.fasterxml.jackson.databind.JsonNode metadata(String image, String address, String port) throws Exception {
        return mapper.readTree("""
            {"status":"running","running":true,"paused":false,"restarting":false,"dead":false,
             "ports":{"25565/tcp":[{"HostIp":"%s","HostPort":"%s"}]},"imageId":"%s"}
            """.formatted(address, port, image));
    }
    @Test void hostModeCannotStartWithoutAnExactImageConfigurationId() {
        for (String image : new String[] {null, "", "stable", "sha256:short", "sha256:" + "A".repeat(64)})
            assertThatThrownBy(() -> properties(image)).isInstanceOf(IllegalStateException.class);
    }
    @Test void hostModeRequiresImageMatchWithoutRelabelingProduction() throws Exception {
        var probe = new DockerContainerProbe(properties(IMAGE), mapper);
        var state = probe.parse(metadata(IMAGE, "0.0.0.0", "25565"));
        assertThat(state.targetMatches()).isTrue();
        assertThat(state.portMatches()).isTrue();
        var service = new StatusService(properties(IMAGE), () -> state, () -> new MinecraftProbe.Result("26.3", 0));
        assertThat(service.observe().lifecycle()).isEqualTo("RUNNING");
        var wrong = probe.parse(metadata("sha256:" + "b".repeat(64), "0.0.0.0", "25565"));
        var rejected = new StatusService(properties(IMAGE), () -> wrong, () -> { throw new AssertionError(); });
        assertThat(rejected.observe().lifecycle()).isEqualTo("UNKNOWN");
        assertThat(probe.parse(metadata("", "0.0.0.0", "25565")).targetMatches()).isFalse();
    }
    @Test void onlyLocallyReachableBindingsAndTheConfiguredPortAreAccepted() throws Exception {
        var probe = new DockerContainerProbe(properties(IMAGE), mapper);
        assertThat(probe.parse(metadata(IMAGE, "127.0.0.1", "25565")).portMatches()).isTrue();
        for (String address : new String[] {"192.0.2.1", "::", "", "::1"})
            assertThat(probe.parse(metadata(IMAGE, address, "25565")).portMatches()).isFalse();
        assertThat(probe.parse(metadata(IMAGE, "0.0.0.0", "25585")).portMatches()).isFalse();
    }
    @Test void inspectionSelectsOnlyMetadataAndParsesImageAsNinthValue() throws Exception {
        var probe = new DockerContainerProbe(properties(IMAGE), mapper);
        assertThat(probe.command()).containsExactly("docker", "--context", "default", "container", "inspect", "--format",
            DockerContainerProbe.FORMAT + "\n{{json .Image}}", "--", "minecraft-server");
        assertThat(probe.command().get(6)).doesNotContain(".Config.Env", ".Mounts", ".State.Health");
        var output = ("\"running\"\ntrue\nfalse\nfalse\nfalse\n{\"25565/tcp\":[{\"HostIp\":\"0.0.0.0\",\"HostPort\":\"25565\"}]}\nnull\nnull\n\"" + IMAGE + "\"\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(probe.parseOutput(output).targetMatches()).isTrue();
        assertThatThrownBy(() -> probe.parseOutput("{}".getBytes())).isInstanceOf(java.io.IOException.class);
    }
}
