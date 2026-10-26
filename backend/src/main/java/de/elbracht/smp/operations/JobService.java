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
        return submit(server, actor, key, type, reason, null);
    }
    @Transactional
    public Job submit(String server, String actor, String key, OperationType type, String reason, String targetName) {
        return submit(server, actor, key, type, reason, targetName, null);
    }
    @Transactional
    public Job submit(String server, String actor, String key, OperationType type, String reason, String targetName, String commandText) {
        return submit(server,actor,key,type,reason,targetName,commandText,null);
    }
    @Transactional
    public Job submit(String server,String actor,String key,OperationType type,String reason,String targetName,String commandText,String snapshotId) {
        return submitInternal(server,actor,key,type,reason,targetName,commandText,snapshotId,null,null,null);
    }
    @Transactional public Job submitConfiguration(String server,String actor,String key,String reason,String revision,java.util.Map<String,String> values) {
        ConfigurationPolicy.validate(revision,values);
        try {String json=new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(new java.util.TreeMap<>(values));return submitInternal(server,actor,key,OperationType.CONFIG_APPLY,reason,null,null,null,json,revision,null);}
        catch(com.fasterxml.jackson.core.JsonProcessingException error){throw new ApiException(422,"INVALID_CONFIGURATION","Konfigurationswerte ungültig.");}
    }
    public Job replayConfiguration(String server,String actor,String key,String reason,String revision,java.util.Map<String,String> values){
        var found=jdbc.query("SELECT * FROM operation_job WHERE server_id=? AND actor=? AND idempotency_key=?",JobService::map,server,actor,key);if(found.isEmpty())return null;
        try{String json=new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(new java.util.TreeMap<>(values));Job job=found.getFirst();
            if(job.type()!=OperationType.CONFIG_APPLY||!job.reason().equals(reason)||!revision.equals(job.configurationRevision())||!json.equals(job.configurationJson()))throw new ApiException(409,"IDEMPOTENCY_CONFLICT","Schlüssel gehört zu einer anderen Anfrage.");return job;
        }catch(com.fasterxml.jackson.core.JsonProcessingException error){throw new ApiException(422,"INVALID_CONFIGURATION","Konfiguration ungültig.");}
    }
    @Transactional public Job submitRestore(String server,String actor,String key,String reason,java.util.UUID plan,String snapshot){return submitInternal(server,actor,key,OperationType.RESTORE_APPLY,reason,null,null,snapshot,null,null,plan);}
    public Job replayRestore(String server,String actor,String key,String reason,java.util.UUID plan){
        var rows=jdbc.query("SELECT * FROM operation_job WHERE server_id=? AND actor=? AND idempotency_key=?",JobService::map,server,actor,key);if(rows.isEmpty())return null;var job=rows.getFirst();
        if(job.type()!=OperationType.RESTORE_APPLY||!plan.equals(job.restorePlanId())||!reason.equals(job.reason()))throw new ApiException(409,"IDEMPOTENCY_CONFLICT","Schlüssel gehört zu einer anderen Anfrage.");return job;
    }
    private Job submitInternal(String server,String actor,String key,OperationType type,String reason,String targetName,String commandText,String snapshotId,String configurationJson,String configurationRevision,java.util.UUID restorePlanId) {
        return submitInternal(server,actor,key,type,reason,targetName,commandText,snapshotId,configurationJson,configurationRevision,restorePlanId,null);
    }
    @Transactional public Job submitMods(String server,String actor,String key,String reason,UUID plan){return submitInternal(server,actor,key,OperationType.MOD_APPLY,reason,null,null,null,null,null,null,plan);}
    public Job replayMods(String server,String actor,String key,String reason,UUID plan){var found=jdbc.query("SELECT * FROM operation_job WHERE server_id=? AND actor=? AND idempotency_key=?",JobService::map,server,actor,key);if(found.isEmpty())return null;var job=found.getFirst();if(job.type()!=OperationType.MOD_APPLY||!job.reason().equals(reason)||!plan.equals(job.modPlanId()))throw new ApiException(409,"IDEMPOTENCY_CONFLICT","Schlüssel gehört zu einer anderen Anfrage.");return job;}
    private Job submitInternal(String server,String actor,String key,OperationType type,String reason,String targetName,String commandText,String snapshotId,String configurationJson,String configurationRevision,UUID restorePlanId,UUID modPlanId) {
        if(type==OperationType.MOD_APPLY?modPlanId==null:modPlanId!=null)throw new ApiException(422,"MOD_PLAN_REQUIRED","Modänderung benötigt einen bestätigten Plan.");
        if(type==OperationType.RESTORE_APPLY ? restorePlanId==null : restorePlanId!=null)throw new ApiException(422,"RESTORE_PLAN_REQUIRED","Wiederherstellung benötigt einen bestätigten Plan.");
        if(type==OperationType.CONFIG_APPLY ? configurationJson==null || configurationRevision==null : configurationJson!=null || configurationRevision!=null)
            throw new ApiException(422,"CONFIGURATION_PAYLOAD_REQUIRED","Konfiguration benötigt ihren eigenen geprüften Ablauf.");
        if(type == OperationType.RESTORE_TEST || type==OperationType.RESTORE_APPLY ? snapshotId==null || !snapshotId.matches("[a-f0-9]{64}") : snapshotId!=null)
            throw new ApiException(422,"INVALID_SNAPSHOT","Snapshotziel passt nicht zur Operation.");
        if (type == OperationType.CONSOLE) ConsolePolicy.validate(commandText, true);
        else if (commandText != null) throw new ApiException(422, "INVALID_COMMAND", "Kommando passt nicht zur Aktion.");
        if (type == null || (type.playerOperation() ? targetName == null || !targetName.matches("[A-Za-z0-9_]{3,16}") : targetName != null))
            throw new ApiException(422, "INVALID_PLAYER_TARGET", "Spielerziel passt nicht zur Operation.");
        lockServer(server);
        List<Job> existing = jdbc.query("SELECT * FROM operation_job WHERE server_id = ? AND actor = ? AND idempotency_key = ?",
            JobService::map, server, actor, key);
        if (!existing.isEmpty()) {
            Job job = existing.getFirst();
            if (job.type() != type || !job.reason().equals(reason) || !java.util.Objects.equals(job.targetName(), targetName) || !java.util.Objects.equals(job.commandText(), commandText) || !java.util.Objects.equals(job.snapshotId(),snapshotId) || !java.util.Objects.equals(job.configurationJson(),configurationJson) || !java.util.Objects.equals(job.configurationRevision(),configurationRevision) || !java.util.Objects.equals(job.restorePlanId(),restorePlanId) || !java.util.Objects.equals(job.modPlanId(),modPlanId))
                throw new ApiException(409, "IDEMPOTENCY_CONFLICT", "Schlüssel gehört zu einer anderen Anfrage.");
            return job;
        }
        if (!(type==OperationType.MOD_APPLY ? agent.modsAvailable() : type==OperationType.RESTORE_APPLY ? agent.restoreAvailable() : type==OperationType.CONFIG_APPLY ? agent.configurationAvailable() : type == OperationType.BACKUP || type == OperationType.RESTORE_TEST ? agent.backupsAvailable() : type == OperationType.CONSOLE ? agent.consoleAvailable() : type.operatorOperation() ? agent.operatorOperationsAvailable() : type.playerOperation() ? agent.playerOperationsAvailable() : agent.operationsAvailable()))
            throw new ApiException(503, "HOST_AGENT_NOT_CONFIGURED", "Host-Agent nicht angebunden. Keine Aktion ausgeführt.");
        if (jdbc.queryForObject("SELECT COUNT(*) FROM operation_job WHERE server_id = ? AND state IN ('QUEUED','RUNNING','UNKNOWN')", Integer.class, server) > 0)
            throw new ApiException(409, "SERVER_BUSY", "Server ist durch einen laufenden oder ungeklärten Job gesperrt.");
        UUID id = UUID.randomUUID();
        OffsetDateTime now = now();
        jdbc.update("INSERT INTO operation_job (id,server_id,actor,idempotency_key,operation_type,reason,state,created_at,updated_at,target_name) VALUES (?,?,?,?,?,?,'QUEUED',?,?,?)",
            id, server, actor, key, type.name(), reason, now, now, targetName);
        if(modPlanId!=null)jdbc.update("UPDATE operation_job SET mod_plan_id=? WHERE id=?",modPlanId,id);
        audit(id, actor, server, type.name(), "QUEUED");
        if (commandText != null) jdbc.update("UPDATE operation_job SET command_text = ? WHERE id = ?", commandText, id);
        if (snapshotId != null) jdbc.update("UPDATE operation_job SET snapshot_id = ? WHERE id = ?", snapshotId, id);
        if (configurationJson != null) jdbc.update("UPDATE operation_job SET configuration_json=?,configuration_revision=? WHERE id=?",configurationJson,configurationRevision,id);
        if(restorePlanId!=null)jdbc.update("UPDATE operation_job SET restore_plan_id=? WHERE id=?",restorePlanId,id);
        return find(server, id);
    }

    public Job find(String server, UUID id) {
        return jdbc.query("SELECT * FROM operation_job WHERE server_id = ? AND id = ?", JobService::map, server, id)
            .stream().findFirst().orElseThrow(() -> new ApiException(404, "JOB_NOT_FOUND", "Job nicht gefunden."));
    }
    @Transactional public Job reconcile(String server,UUID id,String actor){
        lockServer(server);Job job=find(server,id);if(job.state()!=Job.State.UNKNOWN)throw new ApiException(409,"JOB_NOT_UNKNOWN","Nur ungeklärte Jobs können abgeglichen werden.");
        final HostAgent.Reconciled proof;try{proof=agent.reconcile(server,id);}catch(Exception error){throw new ApiException(503,"RECONCILIATION_UNAVAILABLE","Hostabgleich nicht verfügbar. Die Sperre bleibt bestehen.");}
        if(proof==null||!id.equals(proof.operationId())||proof.type()!=job.type()||!java.util.Objects.equals(proof.targetName(),job.targetName())||!java.util.Set.of("SUCCEEDED","FAILED").contains(proof.state()))throw new ApiException(409,"RECONCILIATION_REQUIRED","Der Host kann den Ausgang noch nicht belegen. Keine Änderung und keine Wiederholung.");
        jdbc.update("UPDATE operation_job SET state=?,result_text=?,error_code=?,updated_at=? WHERE id=? AND state='UNKNOWN'",proof.state(),proof.resultText(),proof.state().equals("FAILED")?"VERIFIED_ORIGINAL_STATE":null,now(),id);
        audit(id,actor,server,"JOB_RECONCILED",proof.state());return find(server,id);
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
    @Transactional public void consoleResult(Job job, String text) {
        if (text == null || text.length() > 16384) throw new IllegalArgumentException();
        jdbc.update("UPDATE operation_job SET result_text = ? WHERE id = ? AND state = 'RUNNING'", text, job.jobId());
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
            rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant(), rs.getString("error_code"), rs.getString("target_name"), rs.getString("command_text"), rs.getString("result_text"),rs.getString("snapshot_id"),rs.getString("configuration_json"),rs.getString("configuration_revision"),rs.getObject("restore_plan_id",UUID.class),rs.getObject("mod_plan_id",UUID.class));
    }
}
