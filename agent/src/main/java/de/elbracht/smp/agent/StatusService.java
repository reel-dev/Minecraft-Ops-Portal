package de.elbracht.smp.agent;

import java.time.Instant;
import java.util.concurrent.Semaphore;
import org.springframework.stereotype.Service;

@Service
public class StatusService {
    public record Snapshot(String serverId, String lifecycle, String health, String activity, String minecraftVersion,
                           String loader, String loaderVersion, Instant observedAt, Integer playersOnline, boolean minecraftReady) {}
    private final AgentProperties properties;
    private final ContainerProbe docker;
    private final MinecraftProbe minecraft;
    private final Semaphore singleProbe = new Semaphore(1);
    public StatusService(AgentProperties properties, ContainerProbe docker, MinecraftProbe minecraft) {
        this.properties = properties; this.docker = docker; this.minecraft = minecraft;
    }
    public Snapshot observe() {
        if ("disconnected".equals(properties.mode()) || !singleProbe.tryAcquire()) return state("UNKNOWN", "UNKNOWN");
        try {
            var container = docker.inspect();
            if (!container.targetMatches()) return state("UNKNOWN", "FAILED");
            if (container.dead()) return state("ERROR", "FAILED");
            if (container.paused()) return state("UNKNOWN", "DEGRADED");
            if (container.restarting()) return state("STARTING", "DEGRADED");
            if (!container.running() && (container.status().equals("exited") || container.status().equals("created"))) return state("OFFLINE", "OK");
            if (!container.running() || !container.portMatches()) return state("UNKNOWN", "DEGRADED");
            var result = minecraft.query();
            return new Snapshot(properties.serverId(), "RUNNING", "OK", "NONE", result.minecraftVersion(), null, null, Instant.now(), result.playersOnline(), true);
        } catch (Exception error) {
            if (error instanceof InterruptedException) Thread.currentThread().interrupt();
            // Ein fehlender Minecraft-Ping ist kein Beleg für Offline oder Startphase.
            return state("UNKNOWN", "DEGRADED");
        } finally { singleProbe.release(); }
    }
    private Snapshot state(String lifecycle, String health) {
        return new Snapshot(properties.serverId(), lifecycle, health, "NONE", null, null, null, Instant.now(), null, false);
    }
}
