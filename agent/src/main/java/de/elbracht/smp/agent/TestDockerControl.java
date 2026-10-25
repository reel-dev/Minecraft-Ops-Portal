package de.elbracht.smp.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/** Kein frei parametrierbarer Dockeraufruf. Mutationen ausschließlich für unsere Test-Compose. */
@Component
public class TestDockerControl implements DockerControl {
    private static final String GUARD = "{{index .Config.Labels `de.elbracht.smp.environment`}}|{{index .Config.Labels `de.elbracht.smp.server-id`}}|{{index .Config.Labels `com.docker.compose.project`}}|{{range .Mounts}}{{.Type}}:{{.Name}}:{{.Destination}};{{end}}|{{json .HostConfig.PortBindings}}";
    private final AgentProperties properties;
    private final ObjectMapper mapper;
    public TestDockerControl(AgentProperties properties, ObjectMapper mapper) { this.properties = properties; this.mapper = mapper; }
    private List<String> prefix() {
        if (!"docker-test-control".equals(properties.mode()) || !"elbracht-smp-control-test-minecraft".equals(properties.container()))
            throw new IllegalStateException("Teststeuerung deaktiviert");
        return new ArrayList<>(List.of("docker", "--context", properties.dockerContext(), "container"));
    }
    @Override public void verifyTarget() throws Exception {
        var command = prefix(); command.addAll(List.of("inspect", "--format", GUARD, "--", properties.container()));
        String output = run(command, Duration.ofSeconds(3));
        var pieces = output.strip().split("\\|", 5);
        if (pieces.length != 5 || !"test".equals(pieces[0]) || !properties.serverId().equals(pieces[1])
            || !"elbracht-smp-control-test".equals(pieces[2]) || !"volume:elbracht-smp-control-test_test-world:/data;".equals(pieces[3]))
            throw new IOException("Test target identity rejected");
        boolean allowed = false;
        for (var port : mapper.readTree(pieces[4]).path("25565/tcp"))
            if ("127.0.0.1".equals(port.path("HostIp").asText()) && Integer.toString(properties.minecraftPort()).equals(port.path("HostPort").asText())) allowed = true;
        if (!allowed) throw new IOException("Test target port rejected");
    }
    @Override public void start() throws Exception {
        verifyTarget(); var command = prefix(); command.addAll(List.of("start", "--", properties.container())); run(command, Duration.ofSeconds(10));
    }
    @Override public void stop() throws Exception {
        verifyTarget(); var command = prefix();
        // Docker wartet ohne SIGKILL-Fallback. Bei Agenttimeout ist das Ergebnis UNKNOWN.
        command.addAll(List.of("stop", "--timeout=-1", "--", properties.container())); run(command, Duration.ofSeconds(35));
    }
    private String run(List<String> command, Duration timeout) throws Exception {
        Process process = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        try (var readers = Executors.newVirtualThreadPerTaskExecutor()) {
            var result = readers.submit(() -> process.getInputStream().readNBytes(4097));
            long deadline = System.nanoTime() + timeout.toNanos();
            try {
                byte[] bytes = result.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
                long left = deadline - System.nanoTime();
                if (bytes.length > 4096 || left <= 0 || !process.waitFor(left, TimeUnit.NANOSECONDS) || process.exitValue() != 0) throw new IOException("Test Docker operation failed");
                return new String(bytes, StandardCharsets.UTF_8);
            } finally { process.destroyForcibly(); process.getInputStream().close(); result.cancel(true); }
        }
    }
}
