package de.elbracht.smp.agent;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.Semaphore;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class PlayersController {
    public record Snapshot(String serverId, List<String> names, Instant observedAt) {}
    private final AgentProperties properties;
    private final ContainerProbe docker;
    private final RconPlayerReader reader;
    private final Semaphore singleProbe = new Semaphore(1);
    public PlayersController(AgentProperties properties, ContainerProbe docker, RconPlayerReader reader) {
        this.properties = properties; this.docker = docker; this.reader = reader;
    }
    @GetMapping("/agent/v1/servers/{serverId}/players/online")
    public ResponseEntity<Snapshot> online(@PathVariable String serverId) {
        if (!properties.serverId().equals(serverId)) return ResponseEntity.notFound().build();
        if ("disconnected".equals(properties.mode()) || !reader.available() || !singleProbe.tryAcquire())
            return ResponseEntity.status(503).build();
        try {
            var target = docker.inspect();
            if (!target.targetMatches() || !target.running() || target.paused() || target.restarting()
                    || target.dead() || !target.portMatches()) return ResponseEntity.status(503).build();
            return ResponseEntity.ok(new Snapshot(serverId, reader.readOnlineNames(), Instant.now()));
        } catch (Exception error) {
            if (error instanceof InterruptedException) Thread.currentThread().interrupt();
            return ResponseEntity.status(503).build();
        } finally { singleProbe.release(); }
    }
}
