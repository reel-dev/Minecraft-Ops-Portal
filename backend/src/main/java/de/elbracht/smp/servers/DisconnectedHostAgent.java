package de.elbracht.smp.servers;

import de.elbracht.smp.operations.OperationType;

/** Sicherer Standard ohne Netzwerk- oder Dockerzugriff. */
public class DisconnectedHostAgent implements HostAgent {
    public boolean operationsAvailable() { return false; }
    public Status observe(String serverId) {
        return new Status(serverId, Lifecycle.UNKNOWN, Health.UNKNOWN, Activity.NONE,
            null, null, null, null, "HOST_AGENT_NOT_CONFIGURED", null);
    }
    public void execute(String serverId, OperationType operation) {
        throw new IllegalStateException("Host-Agent ist nicht angebunden");
    }
}
