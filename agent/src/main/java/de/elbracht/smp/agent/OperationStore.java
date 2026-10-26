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
    public enum Type {
        START, STOP, RESTART, WHITELIST_ADD, WHITELIST_REMOVE, OP_GRANT, OP_REVOKE, CONSOLE, BACKUP, RESTORE_TEST, CONFIG_APPLY, RESTORE_APPLY, MOD_APPLY;
        public boolean operatorOperation() { return this == OP_GRANT || this == OP_REVOKE; }
        public boolean playerOperation() { return this == WHITELIST_ADD || this == WHITELIST_REMOVE || operatorOperation(); }
    }
    public record Result(UUID operationId, Type type, String state, String targetName, String resultText) {
        public Result(UUID operationId, Type type, String state) { this(operationId, type, state, null, null); }
    }
    private final JdbcTemplate jdbc;
    public OperationStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @PostConstruct void recover() { jdbc.update("UPDATE agent_operation SET state = 'UNKNOWN', updated_at = ? WHERE state = 'RUNNING'", now()); }
    public Result find(UUID id) {
        return jdbc.query("SELECT * FROM agent_operation WHERE id = ?", (rs, row) -> new Result(rs.getObject("id", UUID.class), Type.valueOf(rs.getString("operation_type")), rs.getString("state"), rs.getString("target_name"), rs.getString("result_text")), id)
            .stream().findFirst().orElse(null);
    }
    @Transactional public void begin(UUID id, Type type) {
        begin(id, type, null);
    }
    @Transactional public void begin(UUID id, Type type, String targetName) {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM agent_operation WHERE state IN ('RUNNING','UNKNOWN')", Integer.class) > 0)
            throw new OperationRejected(409, "AGENT_BUSY");
        jdbc.update("INSERT INTO agent_operation (id,operation_type,state,updated_at,target_name) VALUES (?,?, 'RUNNING', ?,?)", id, type.name(), now(), targetName);
    }
    @Transactional public Result finish(UUID id, Type type, String state) {
        jdbc.update("UPDATE agent_operation SET state = ?, updated_at = ? WHERE id = ? AND state = 'RUNNING'", state, now(), id);
        return find(id);
    }
    public String command(UUID id) { return jdbc.queryForObject("SELECT command_text FROM agent_operation WHERE id = ?", String.class, id); }
    @Transactional public void consolePayload(UUID id, String command) { jdbc.update("UPDATE agent_operation SET command_text = ? WHERE id = ?", command, id); }
    @Transactional public void consoleResult(UUID id, String output) { jdbc.update("UPDATE agent_operation SET result_text = ? WHERE id = ? AND state = 'RUNNING'", output, id); }
    public String snapshot(UUID id) { return jdbc.queryForObject("SELECT snapshot_id FROM agent_operation WHERE id = ?",String.class,id); }
    @Transactional public void snapshotTarget(UUID id,String snapshot) { jdbc.update("UPDATE agent_operation SET snapshot_id=? WHERE id=?",snapshot,id); }
    public String configuration(UUID id){return jdbc.queryForObject("SELECT configuration_payload FROM agent_operation WHERE id=?",String.class,id);}
    @Transactional public void configurationTarget(UUID id,String payload){jdbc.update("UPDATE agent_operation SET configuration_payload=? WHERE id=?",payload,id);}
    @Transactional public Result reconcile(UUID id,String state,String result){
        if(!java.util.Set.of("SUCCEEDED","FAILED").contains(state))throw new IllegalArgumentException();
        jdbc.update("UPDATE agent_operation SET state=?,result_text=?,updated_at=? WHERE id=? AND state='UNKNOWN'",state,result,now(),id);return find(id);
    }
    public UUID restoreTarget(UUID id){return jdbc.queryForObject("SELECT restore_plan_id FROM agent_operation WHERE id=?",UUID.class,id);}
    @Transactional public void restoreTarget(UUID id,UUID plan){jdbc.update("UPDATE agent_operation SET restore_plan_id=? WHERE id=?",plan,id);}
    public UUID modTarget(UUID id){return jdbc.queryForObject("SELECT mod_plan_id FROM agent_operation WHERE id=?",UUID.class,id);}
    @Transactional public void modTarget(UUID id,UUID plan){jdbc.update("UPDATE agent_operation SET mod_plan_id=? WHERE id=?",plan,id);}
    private static OffsetDateTime now() { return OffsetDateTime.now(ZoneOffset.UTC); }
}
