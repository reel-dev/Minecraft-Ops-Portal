package de.elbracht.smp.agent;

import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class OperationService {
    private final AgentProperties properties;
    private final OperationStore store;
    private final DockerControl control;
    private final ContainerProbe docker;
    private final MinecraftProbe minecraft;
    public OperationService(AgentProperties properties, OperationStore store, DockerControl control, ContainerProbe docker, MinecraftProbe minecraft) {
        this.properties = properties; this.store = store; this.control = control; this.docker = docker; this.minecraft = minecraft;
    }
    /** Genau eine Agentinstanz pro Ledger/Testserver. Keine automatische Wiederholung unbekannter Ergebnisse. */
    public synchronized OperationStore.Result execute(UUID id, OperationStore.Type type) {
        if (!"docker-test-control".equals(properties.mode())) throw new OperationRejected(403, "TEST_CONTROL_DISABLED");
        var existing = store.find(id);
        if (existing != null) {
            if (existing.type() != type) throw new OperationRejected(409, "OPERATION_ID_CONFLICT");
            return existing;
        }
        store.begin(id, type);
        try {
            control.verifyTarget();
            if (type == OperationStore.Type.STOP || type == OperationStore.Type.RESTART) {
                control.stop();
                var stopped = docker.inspect();
                if (!stopped.targetMatches() || stopped.running() || !stopped.status().equals("exited")) throw new IllegalStateException();
            }
            if (type != OperationStore.Type.STOP) {
                control.start();
                long deadline = System.nanoTime() + java.time.Duration.ofSeconds(12).toNanos();
                boolean ready = false;
                while (System.nanoTime() < deadline) {
                    try {
                        var current = docker.inspect();
                        if (current.targetMatches() && current.running() && !current.paused() && !current.restarting() && !current.dead() && current.portMatches()) {
                            minecraft.query(); ready = true; break;
                        }
                    } catch (java.io.IOException ignored) { }
                    Thread.sleep(250);
                }
                if (!ready) throw new IllegalStateException();
            }
            return store.finish(id, type, "SUCCEEDED");
        } catch (Exception error) {
            if (error instanceof InterruptedException) Thread.currentThread().interrupt();
            return store.finish(id, type, "UNKNOWN");
        }
    }
}
