package de.elbracht.smp.operations;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import de.elbracht.smp.api.ApiException;
import de.elbracht.smp.servers.HostAgent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

@Service
public class JobService {
    private final JdbcTemplate jdbc;
    private final HostAgent agent;
    public JobService(JdbcTemplate jdbc, HostAgent agent) { this.jdbc = jdbc; this.agent = agent; }

    @Transactional
    public Job submit(String server, String actor, String key, OperationType type, String reason) {
        lockServer(server);
        List<Job> existing = jdbc.query("SELECT * FROM operation_job WHERE server_id = ? AND actor = ? AND idempotency_key = ?",
            JobService::map, server, actor, key);
        if (!existing.isEmpty()) {
            Job job = existing.getFirst();
            if (job.type() != type || !job.reason().equals(reason))
                throw new ApiException(409, "IDEMPOTENCY_CONFLICT", "Schlüssel gehört zu einer anderen Anfrage.");
            return job;
        }
        if (!agent.operationsAvailable())
            throw new ApiException(503, "HOST_AGENT_NOT_CONFIGURED", "Host-Agent nicht angebunden. Keine Aktion ausgeführt.");
        if (jdbc.queryForObject("SELECT COUNT(*) FROM operation_job WHERE server_id = ? AND state IN ('QUEUED','RUNNING','UNKNOWN')", Integer.class, server) > 0)
            throw new ApiException(409, "SERVER_BUSY", "Server ist durch einen laufenden oder ungeklärten Job gesperrt.");
        UUID id = UUID.randomUUID();
        OffsetDateTime now = now();
        jdbc.update("INSERT INTO operation_job (id,server_id,actor,idempotency_key,operation_type,reason,state,created_at,updated_at) VALUES (?,?,?,?,?,?,'QUEUED',?,?)",
            id, server, actor, key, type.name(), reason, now, now);
        audit(id, actor, server, type.name(), "QUEUED");
        return find(server, id);
    }

    public Job find(String server, UUID id) {
        return jdbc.query("SELECT * FROM operation_job WHERE server_id = ? AND id = ?", JobService::map, server, id)
            .stream().findFirst().orElseThrow(() -> new ApiException(404, "JOB_NOT_FOUND", "Job nicht gefunden."));
    }

    public record Page(List<Job> items, UUID nextCursor, Job blockingJob) {}

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Page history(String server, int limit, UUID before) {
        if (limit < 1 || limit > 50) throw new ApiException(422, "INVALID_REQUEST", "Seitengröße muss zwischen 1 und 50 liegen.");
        if (jdbc.queryForObject("SELECT COUNT(*) FROM managed_server WHERE id = ?", Integer.class, server) == 0)
            throw new ApiException(404, "SERVER_NOT_FOUND", "Server nicht registriert.");
        List<Job> rows;
        if (before == null) {
            rows = jdbc.query("SELECT * FROM operation_job WHERE server_id = ? ORDER BY created_at DESC, id DESC LIMIT ?",
                JobService::map, server, limit + 1);
        } else {
            Job cursor = find(server, before);
            OffsetDateTime timestamp = cursor.createdAt().atOffset(ZoneOffset.UTC);
            rows = jdbc.query("SELECT * FROM operation_job WHERE server_id = ? AND (created_at < ? OR (created_at = ? AND id < ?)) ORDER BY created_at DESC, id DESC LIMIT ?",
                JobService::map, server, timestamp, timestamp, before, limit + 1);
        }
        List<Job> items = List.copyOf(rows.subList(0, Math.min(limit, rows.size())));
        Job blocking = jdbc.query("SELECT * FROM operation_job WHERE server_id = ? AND state IN ('QUEUED','RUNNING','UNKNOWN') ORDER BY created_at DESC, id DESC LIMIT 1",
            JobService::map, server).stream().findFirst().orElse(null);
        return new Page(items, rows.size() > limit ? items.getLast().jobId() : null, blocking);
    }

    public List<Job> queued() {
        return jdbc.query("SELECT * FROM operation_job WHERE state = 'QUEUED' ORDER BY created_at LIMIT 20", JobService::map);
    }

    @Transactional
    public boolean claim(Job job) {
        lockServer(job.serverId());
        int changed = jdbc.update("UPDATE operation_job SET state = 'RUNNING', updated_at = ? WHERE id = ? AND state = 'QUEUED'", now(), job.jobId());
        if (changed == 1) audit(job.jobId(), job.actor(), job.serverId(), job.type().name(), "RUNNING");
        return changed == 1;
    }

    @Transactional
    public void finish(Job job, Job.State state, String code) {
        if (state != Job.State.SUCCEEDED && state != Job.State.FAILED && state != Job.State.UNKNOWN)
            throw new ApiException(422, "INVALID_JOB_TRANSITION", "Ungültiger Jobabschluss.");
        int changed = jdbc.update("UPDATE operation_job SET state = ?, error_code = ?, updated_at = ? WHERE id = ? AND state = 'RUNNING'",
            state.name(), code, now(), job.jobId());
        if (changed == 1) audit(job.jobId(), job.actor(), job.serverId(), job.type().name(), state.name());
    }

    /** Single-instance MVP: nach Prozessabbruch nicht automatisch erneut ausführen. */
    @Transactional
    public void recover() {
        List<Job> unresolved = jdbc.query("SELECT * FROM operation_job WHERE state IN ('QUEUED','RUNNING')", JobService::map);
        for (Job job : unresolved) {
            jdbc.update("UPDATE operation_job SET state = 'UNKNOWN', error_code = 'RECONCILIATION_REQUIRED', updated_at = ? WHERE id = ?", now(), job.jobId());
            audit(job.jobId(), job.actor(), job.serverId(), job.type().name(), "UNKNOWN");
        }
    }

    private void lockServer(String server) {
        if (jdbc.query("SELECT id FROM managed_server WHERE id = ? FOR UPDATE", (rs, row) -> rs.getString(1), server).isEmpty())
            throw new ApiException(404, "SERVER_NOT_FOUND", "Server nicht registriert.");
    }
    private void audit(UUID job, String actor, String server, String action, String outcome) {
        // Freitextbegründung bewusst nicht ins Audit übernehmen.
        jdbc.update("INSERT INTO audit_event (id,actor,server_id,job_id,action,outcome,occurred_at) VALUES (?,?,?,?,?,?,?)",
            UUID.randomUUID(), actor, server, job, action, outcome, now());
    }
    private static OffsetDateTime now() { return OffsetDateTime.now(ZoneOffset.UTC); }
    private static Job map(ResultSet rs, int row) throws SQLException {
        return new Job(rs.getObject("id", UUID.class), rs.getString("server_id"), rs.getString("actor"),
            OperationType.valueOf(rs.getString("operation_type")), rs.getString("reason"), Job.State.valueOf(rs.getString("state")),
            rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant(), rs.getString("error_code"));
    }
}
