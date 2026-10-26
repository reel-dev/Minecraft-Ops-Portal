package de.elbracht.smp.servers;

import java.time.Instant;
import de.elbracht.smp.operations.OperationType;

/** Begrenzter Port: keine URLs, Container-IDs, Pfade oder Shellbefehle aus dem Client. */
public interface HostAgent {
    default boolean modsAvailable(){return false;}
    default com.fasterxml.jackson.databind.JsonNode modRpc(String server,String path,String method,java.util.Map<String,?> body,java.time.Duration timeout){throw new IllegalStateException("Modverwaltung nicht angebunden");}
    default String applyMods(String server,java.util.UUID operation,java.util.UUID plan){throw new IllegalStateException("Modverwaltung nicht angebunden");}
    boolean operationsAvailable();
    default boolean playerOperationsAvailable() { return false; }
    default boolean operatorOperationsAvailable() { return false; }
    default boolean consoleAvailable() { return false; }
    default boolean backupsAvailable() { return false; }
    default boolean configurationAvailable(){return false;}
    default boolean restoreAvailable(){return false;}
    record RestorePlan(java.util.UUID planId,String snapshotId,String createdAt,String expiresAt,String minecraftVersion,String loaderVersion,java.util.List<String> components,boolean startAfterRestore,String state){}
    default RestorePlan prepareRestore(String serverId,String snapshot,boolean startAfter){throw new IllegalStateException("Wiederherstellung nicht angebunden");}
    default RestorePlan restorePlan(String serverId,java.util.UUID plan){throw new IllegalStateException("Wiederherstellung nicht angebunden");}
    default void cancelRestore(String serverId,java.util.UUID plan){throw new IllegalStateException("Wiederherstellung nicht angebunden");}
    default String restore(String serverId,java.util.UUID operation,java.util.UUID plan){throw new IllegalStateException("Wiederherstellung nicht angebunden");}
    final class KnownFailure extends RuntimeException {
        public final String code;public final String result;
        public KnownFailure(String code,String result){super(code);this.code=code;this.result=result;}
    }
    record Reconciled(java.util.UUID operationId,OperationType type,String state,String targetName,String resultText){}
    default Reconciled reconcile(String serverId,java.util.UUID operationId){throw new IllegalStateException("Abgleich nicht angebunden");}
    default String configuration(String serverId,java.util.UUID operationId,String revision,String configurationJson){throw new IllegalStateException("Konfiguration nicht angebunden");}
    default String backup(String serverId, java.util.UUID operationId, OperationType type, String snapshotId) { throw new IllegalStateException("Backup nicht angebunden"); }
    default String console(String serverId, java.util.UUID operationId, String command) { throw new IllegalStateException("Konsole nicht angebunden"); }
    default String statusSource() { return "DISCONNECTED"; }
    Status observe(String serverId);
    default OnlinePlayers onlinePlayers(String serverId) {
        return new OnlinePlayers(serverId, null, null, "PLAYERS_NOT_CONNECTED");
    }
    record OnlinePlayers(String serverId, java.util.List<String> names, Instant observedAt, String reasonCode) {}
    record Diagnostics(String serverId, Instant observedAt, com.fasterxml.jackson.databind.JsonNode data, String reasonCode) {}
    default Diagnostics diagnostics(String serverId, String kind) { return new Diagnostics(serverId, null, null, "SOURCE_NOT_CONFIGURED"); }
    /** Erst zurückkehren, wenn der Zielzustand einschließlich Minecraft-Readiness geprüft ist. */
    void execute(String serverId, OperationType operation);
    default void execute(String serverId, OperationType operation, java.util.UUID operationId) { execute(serverId, operation); }
    default void execute(String serverId, OperationType operation, java.util.UUID operationId, String targetName) {
        throw new IllegalStateException("Spieleränderungen nicht angebunden");
    }

    enum Lifecycle { UNKNOWN, OFFLINE, STARTING, RUNNING, STOPPING, ERROR }
    enum Health { OK, DEGRADED, FAILED, UNKNOWN }
    enum Activity { NONE, BACKUP, RESTORE, MOD_CHANGE }
    record Status(String serverId, Lifecycle lifecycle, Health health, Activity activity,
                  String minecraftVersion, String loader, String loaderVersion,
                  Instant observedAt, String reasonCode, Integer playersOnline) {}
}
