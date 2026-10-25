package de.elbracht.smp.servers;

import java.time.Instant;
import de.elbracht.smp.operations.OperationType;

/** Begrenzter Port: keine URLs, Container-IDs, Pfade oder Shellbefehle aus dem Client. */
public interface HostAgent {
    boolean operationsAvailable();
    default String statusSource() { return "DISCONNECTED"; }
    Status observe(String serverId);
    /** Erst zurückkehren, wenn der Zielzustand einschließlich Minecraft-Readiness geprüft ist. */
    void execute(String serverId, OperationType operation);

    enum Lifecycle { UNKNOWN, OFFLINE, STARTING, RUNNING, STOPPING, ERROR }
    enum Health { OK, DEGRADED, FAILED, UNKNOWN }
    enum Activity { NONE, BACKUP, RESTORE, MOD_CHANGE }
    record Status(String serverId, Lifecycle lifecycle, Health health, Activity activity,
                  String minecraftVersion, String loader, String loaderVersion,
                  Instant observedAt, String reasonCode, Integer playersOnline) {}
}
