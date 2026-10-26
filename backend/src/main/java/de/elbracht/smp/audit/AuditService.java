package de.elbracht.smp.audit;

import de.elbracht.smp.api.ApiException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {
    private final JdbcTemplate jdbc;
    public AuditService(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public void recordPlan(String server,String actor,String action,UUID plan){
        if(!java.util.Set.of("RESTORE_PLAN_CREATED","RESTORE_PLAN_CANCELLED","MOD_PLAN_CREATED","MOD_PLAN_CANCELLED","MOD_IMPORT_PLAN_CREATED").contains(action))throw new IllegalArgumentException();
        jdbc.update("INSERT INTO audit_event (id,actor,server_id,action,outcome,occurred_at,subject_key) VALUES (?,?,?,?,'RECORDED',?,?)",UUID.randomUUID(),actor,server,action,java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC),plan.toString());
    }
    public record Event(UUID id, String actor, String serverId, UUID jobId, String action, String outcome, Instant occurredAt,
                        String subjectKey, Long revision) {}
    public record Page(List<Event> items, UUID nextCursor) {}

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Page list(String server, UUID jobId, int limit, UUID before) {
        if (limit < 1 || limit > 50) throw new ApiException(422, "INVALID_REQUEST", "Seitengröße muss zwischen 1 und 50 liegen.");
        if (jdbc.queryForObject("SELECT COUNT(*) FROM managed_server WHERE id = ?", Integer.class, server) == 0)
            throw new ApiException(404, "SERVER_NOT_FOUND", "Server nicht registriert.");
        if (jobId != null && jdbc.queryForObject("SELECT COUNT(*) FROM operation_job WHERE server_id = ? AND id = ?", Integer.class, server, jobId) == 0)
            throw new ApiException(404, "JOB_NOT_FOUND", "Job nicht gefunden.");
        String sql = "SELECT * FROM audit_event WHERE server_id = ?";
        List<Object> args = new ArrayList<>(); args.add(server);
        if (jobId != null) { sql += " AND job_id = ?"; args.add(jobId); }
        if (before != null) {
            Event cursor = jdbc.query("SELECT * FROM audit_event WHERE server_id = ? AND id = ?", AuditService::map, server, before)
                .stream().findFirst().filter(event -> jobId == null || jobId.equals(event.jobId()))
                .orElseThrow(() -> new ApiException(404, "AUDIT_CURSOR_NOT_FOUND", "Audit-Cursor nicht gefunden."));
            sql += " AND (occurred_at < ? OR (occurred_at = ? AND id < ?))";
            var timestamp = cursor.occurredAt().atOffset(ZoneOffset.UTC);
            args.add(timestamp); args.add(timestamp); args.add(before);
        }
        sql += " ORDER BY occurred_at DESC, id DESC LIMIT ?"; args.add(limit + 1);
        List<Event> rows = jdbc.query(sql, AuditService::map, args.toArray());
        List<Event> items = List.copyOf(rows.subList(0, Math.min(limit, rows.size())));
        return new Page(items, rows.size() > limit ? items.getLast().id() : null);
    }

    private static Event map(ResultSet rs, int row) throws SQLException {
        return new Event(rs.getObject("id", UUID.class), rs.getString("actor"), rs.getString("server_id"),
            rs.getObject("job_id", UUID.class), rs.getString("action"), rs.getString("outcome"), rs.getTimestamp("occurred_at").toInstant(),
            rs.getString("subject_key"), rs.getObject("revision", Long.class));
    }
}
