package de.elbracht.smp.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.*;
import static org.assertj.core.api.Assertions.*;

class BackupRetentionTest {
    @TempDir Path root;
    EmbeddedDatabase database; JdbcTemplate jdbc;
    final ObjectMapper mapper=new ObjectMapper();
    final Instant now=Instant.parse("2026-10-26T12:00:00Z");
    final String fresh="a".repeat(64),old="b".repeat(64),foreign="c".repeat(64);
    final Map<String,Instant> snapshots=new LinkedHashMap<>();
    final List<List<String>> commands=new ArrayList<>();
    boolean failCheck,failPrune,protectDuringDry;
    BackupRetention service;
    @BeforeEach void setup() throws Exception {
        database=new EmbeddedDatabaseBuilder().generateUniqueName(true).setType(EmbeddedDatabaseType.H2).addScript("schema.sql").build();jdbc=new JdbcTemplate(database);
        Files.writeString(root.resolve("config"),"explicit-unit-repository-identity");
        snapshots.put(fresh,now);snapshots.put(old,now.minus(Duration.ofDays(10)));snapshots.put(foreign,now.minus(Duration.ofDays(20)));
        for(String id:List.of(fresh,old))jdbc.update("INSERT INTO backup_snapshot(snapshot_id,created_at,integrity) VALUES (?,?,'READ_DATA_OK')",id,snapshots.get(id).toString());
        var properties=new BackupProperties(true,root,root,root,root,false,true);
        service=new BackupRetention(properties,StatusServiceTest.properties(25585,"docker-test-control"),jdbc,mapper,Clock.fixed(now,ZoneOffset.UTC)) {
            @Override String run(List<String> args) throws Exception {
                commands.add(List.copyOf(args));
                return switch(args.getFirst()) {
                    case "snapshots" -> mapper.writeValueAsString(snapshots.entrySet().stream().map(e->Map.of("id",e.getKey(),"time",e.getValue().toString(),"tags",List.of("smp:main"))).toList());
                    case "check" -> {if(failCheck)throw new java.io.IOException("explicit check failure");yield "";}
                    case "forget" -> {assertThat(Thread.holdsLock(BackupRetention.REPOSITORY_GUARD)).isTrue();if(args.contains("--dry-run")){if(protectDuringDry)protect(old);}else args.stream().filter(id->id.matches("[a-f0-9]{64}")).forEach(snapshots::remove);yield "";}
                    case "prune" -> {if(failPrune)throw new java.io.IOException("explicit prune failure");yield "";}
                    default -> throw new AssertionError("Unexpected command");
                };
            }
        };
    }
    @AfterEach void close(){database.shutdown();}
    void protect(String id){jdbc.update("INSERT INTO restore_plan(id,snapshot_id,created_at,expires_at,target_identity,stage_digest,start_after,state) VALUES (?,?,?,?,?,?,TRUE,'PLANNED')",UUID.randomUUID(),id,now.toString(),now.plusSeconds(900).toString(),"d".repeat(64),"e".repeat(64));}
    @Test void dryRunPrecedesExactForgetAndUnknownSnapshotsSurvive() throws Exception {
        var preview=service.preview();assertThat(preview.candidates()).extracting(BackupRetention.Entry::id).containsExactly(old);
        service.afterSuccessfulBackup(UUID.randomUUID(),fresh);
        assertThat(snapshots.keySet()).containsExactly(fresh,foreign);
        int dry=commands.indexOf(List.of("forget","--dry-run","--json",old));
        int actual=commands.indexOf(List.of("forget","--json",old));assertThat(dry).isGreaterThanOrEqualTo(0);assertThat(actual).isGreaterThan(dry);
        assertThat(jdbc.queryForObject("SELECT integrity FROM backup_snapshot WHERE snapshot_id=?",String.class,old)).isEqualTo("EXPIRED");
        assertThat(service.policy().lastState()).isEqualTo("SUCCEEDED");assertThat(service.policy().lastForgottenCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT forgotten_ids FROM backup_retention_run",String.class)).isEqualTo("[\""+old+"\"]");
    }
    @Test void failedIntegrityCheckNeverForgetsOrPrunes() {
        failCheck=true;
        assertThatThrownBy(()->service.afterSuccessfulBackup(UUID.randomUUID(),fresh)).isInstanceOf(java.io.IOException.class);
        assertThat(commands).noneMatch(c->c.getFirst().equals("forget")||c.getFirst().equals("prune"));
        assertThat(snapshots).containsKey(old);assertThat(service.policy().lastState()).isEqualTo("UNKNOWN");
    }
    @Test void newRestoreReferenceDuringDryRunPreventsDeletion() {
        protectDuringDry=true;
        assertThatThrownBy(()->service.afterSuccessfulBackup(UUID.randomUUID(),fresh)).isInstanceOf(java.io.IOException.class);
        assertThat(commands).doesNotContain(List.of("forget","--json",old));assertThat(snapshots).containsKey(old);
    }
    @Test void missingKnownSnapshotBlocksPreviewRatherThanTreatingItAsExpired() {
        snapshots.remove(old);
        assertThatThrownBy(()->service.preview()).isInstanceOf(java.io.IOException.class);
        assertThat(commands).noneMatch(c->c.getFirst().equals("forget"));
    }
    @Test void interruptedPruneIsUnknownAndNeverReplayedAutomatically() {
        UUID operation=UUID.randomUUID();failPrune=true;
        assertThatThrownBy(()->service.afterSuccessfulBackup(operation,fresh)).isInstanceOf(java.io.IOException.class);
        assertThat(service.policy().lastState()).isEqualTo("UNKNOWN");assertThat(snapshots).containsKey(fresh);
        int before=commands.size();assertThatThrownBy(()->service.afterSuccessfulBackup(operation,fresh)).isInstanceOf(java.io.IOException.class);assertThat(commands).hasSize(before);
    }
    @Test void recoveredRetentionLedgerStaysUnknown() {
        jdbc.update("INSERT INTO backup_retention_run(operation_id,started_at,state,forgotten_count) VALUES (?,?,'RUNNING',0)",UUID.randomUUID(),now.toString());
        service.recover();assertThat(service.policy().lastState()).isEqualTo("UNKNOWN");
    }
}
