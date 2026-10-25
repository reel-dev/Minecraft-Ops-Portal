package de.elbracht.smp.operations;

import java.time.Instant;
import java.util.UUID;

public record Job(UUID jobId, String serverId, String actor, OperationType type, String reason,
                  State state, Instant createdAt, Instant updatedAt, String errorCode) {
    public enum State { QUEUED, RUNNING, SUCCEEDED, FAILED, UNKNOWN }
}
