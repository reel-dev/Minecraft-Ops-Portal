package de.elbracht.smp.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
public class DockerContainerProbe implements ContainerProbe {
    // Gezielt keine Containerumgebung, Healthcheck-Logs oder beliebigen Dateipfade auslesen.
    static final String FORMAT = """
        {{json .State.Status}}
        {{json .State.Running}}
        {{json .State.Paused}}
        {{json .State.Restarting}}
        {{json .State.Dead}}
        {{json .NetworkSettings.Ports}}
        {{json (index .Config.Labels `de.elbracht.smp.environment`)}}
        {{json (index .Config.Labels `de.elbracht.smp.server-id`)}}
        """.strip();
    private final AgentProperties properties;
    private final ObjectMapper mapper;
    public DockerContainerProbe(AgentProperties properties, ObjectMapper mapper) { this.properties = properties; this.mapper = mapper; }
    List<String> command() {
        return List.of("docker", "--context", properties.dockerContext(), "container", "inspect", "--format", FORMAT, "--", properties.container());
    }
    @Override public State inspect() throws Exception {
        Process process = new ProcessBuilder(command()).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        // Maximal 16 KiB und Gesamtlaufzeit, auch wenn stdout nie geschlossen wird.
        try (var reader = Executors.newVirtualThreadPerTaskExecutor()) {
            var output = reader.submit(() -> process.getInputStream().readNBytes(16_385));
            long deadline = System.nanoTime() + properties.dockerTimeout().toNanos();
            try {
                byte[] bytes = output.get(properties.dockerTimeout().toMillis(), TimeUnit.MILLISECONDS);
                long remaining = deadline - System.nanoTime();
                if (bytes.length > 16_384 || remaining <= 0 || !process.waitFor(remaining, TimeUnit.NANOSECONDS) || process.exitValue() != 0) throw new IOException("Docker inspection failed");
                return parseOutput(bytes);
            } finally {
                process.destroyForcibly(); process.getInputStream().close(); output.cancel(true);
            }
        }
    }
    State parseOutput(byte[] bytes) throws IOException {
        // Einzelne JSON-Werte vermeiden eingebettete Anführungszeichen in Windows-Prozessargumenten.
        var lines = new String(bytes, java.nio.charset.StandardCharsets.UTF_8).strip().lines().toList();
        var keys = List.of("status", "running", "paused", "restarting", "dead", "ports", "environment", "serverId");
        if (lines.size() != keys.size()) throw new IOException("Invalid Docker output");
        var json = mapper.createObjectNode();
        for (int index = 0; index < keys.size(); index++) json.set(keys.get(index), mapper.readTree(lines.get(index)));
        return parse(json);
    }
    State parse(JsonNode json) throws IOException {
        for (String flag : List.of("running", "paused", "restarting", "dead")) if (!json.path(flag).isBoolean()) throw new IOException("Invalid Docker state");
        String status = json.path("status").asText("");
        if (!List.of("created", "running", "paused", "restarting", "removing", "exited", "dead").contains(status)) throw new IOException("Invalid Docker state");
        boolean target = "test".equals(json.path("environment").asText()) && properties.serverId().equals(json.path("serverId").asText());
        boolean matches = false;
        for (JsonNode binding : json.path("ports").path("25565/tcp")) {
            // Ausschließlich die veröffentlichte Loopbackadresse des getrennten Testcontainers.
            if ("127.0.0.1".equals(binding.path("HostIp").asText()) && Integer.toString(properties.minecraftPort()).equals(binding.path("HostPort").asText())) matches = true;
        }
        return new State(status, json.path("running").asBoolean(), json.path("paused").asBoolean(), json.path("restarting").asBoolean(), json.path("dead").asBoolean(), target, matches);
    }
}
