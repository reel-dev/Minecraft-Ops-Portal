package de.elbracht.smp.agent;

import jakarta.annotation.PostConstruct;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@DependsOnDatabaseInitialization
public class OperationStore {
    public enum Type { START, STOP, RESTART }
    public record Result(UUID operationId, Type type, String state) {}
    private final JdbcTemplate jdbc;
    public OperationStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @PostConstruct void recover() { jdbc.update("UPDATE agent_operation SET state = 'UNKNOWN', updated_at = ? WHERE state = 'RUNNING'", now()); }
    public Result find(UUID id) {
        return jdbc.query("SELECT * FROM agent_operation WHERE id = ?", (rs, row) -> new Result(rs.getObject("id", UUID.class), Type.valueOf(rs.getString("operation_type")), rs.getString("state")), id)
            .stream().findFirst().orElse(null);
    }
    @Transactional public void begin(UUID id, Type type) {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM agent_operation WHERE state IN ('RUNNING','UNKNOWN')", Integer.class) > 0)
            throw new OperationRejected(409, "AGENT_BUSY");
        jdbc.update("INSERT INTO agent_operation VALUES (?,?, 'RUNNING', ?)", id, type.name(), now());
    }
    @Transactional public Result finish(UUID id, Type type, String state) {
        jdbc.update("UPDATE agent_operation SET state = ?, updated_at = ? WHERE id = ? AND state = 'RUNNING'", state, now(), id);
        return find(id);
    }
    private static OffsetDateTime now() { return OffsetDateTime.now(ZoneOffset.UTC); }
}
