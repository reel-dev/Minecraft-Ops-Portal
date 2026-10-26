package de.elbracht.smp.operations;

import java.time.Instant;
import java.util.UUID;

public record Job(UUID jobId, String serverId, String actor, OperationType type, String reason,
                  State state, Instant createdAt, Instant updatedAt, String errorCode, String targetName, String commandText, String resultText, String snapshotId, String configurationJson, String configurationRevision,UUID restorePlanId,UUID modPlanId) {
    public enum State { QUEUED, RUNNING, SUCCEEDED, FAILED, UNKNOWN }
}
