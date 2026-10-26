package de.elbracht.smp.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.*;
import static org.assertj.core.api.Assertions.*;

/** Own disposable repositories and synthetic text only. Never opens Docker or an existing backup repository. */
@EnabledIfEnvironmentVariable(named="SMP_RETENTION_LIVE_TEST",matches="true")
class BackupRetentionLiveTest {
    @TempDir Path root;
    final ObjectMapper mapper=new ObjectMapper();
    @Test void realEncryptedRepositoryForgetsOnlyExpiredVerifiedFixtureAndRestoresNewest() throws Exception {
        Path executable=Path.of(System.getenv("SMP_RETENTION_TEST_EXECUTABLE")).toRealPath();
        Path repo=Files.createDirectory(root.resolve("repository"));
        Path work=Files.createDirectory(root.resolve("work"));
        Path source=Files.createDirectory(root.resolve("source"));
        Path password=Files.writeString(root.resolve("repository-password"),UUID.randomUUID()+"-"+UUID.randomUUID());
        var properties=new BackupProperties(true,executable,repo,password,work,false,true);
        ResticCommand.local(properties,source,List.of("init"));
        String old=backup(properties,source,LocalDateTime.now().minusDays(10),"explicit old retention fixture");
        String unmanaged=backup(properties,source,LocalDateTime.now().minusDays(20),"explicit unmanaged retention fixture");
        String newest=backup(properties,source,null,"explicit newest retention fixture");
        ResticCommand.local(properties,source,List.of("check","--read-data"));
        var db=new EmbeddedDatabaseBuilder().generateUniqueName(true).setType(EmbeddedDatabaseType.H2).addScript("schema.sql").build();
        try {
            var jdbc=new JdbcTemplate(db);
            for(String id:List.of(old,newest))jdbc.update("INSERT INTO backup_snapshot(snapshot_id,created_at,integrity,scope) VALUES (?,?,'READ_DATA_OK','RETENTION_ACCEPTANCE_FIXTURE')",id,Instant.now().toString());
            var service=new BackupRetention(properties,StatusServiceTest.properties(25585,"docker-test-control"),jdbc,mapper);
            var before=service.preview();assertThat(before.candidates()).extracting(BackupRetention.Entry::id).containsExactly(old);
            UUID operation=UUID.randomUUID();service.afterSuccessfulBackup(operation,newest);
            var remaining=mapper.readTree(ResticCommand.local(properties,source,List.of("snapshots","--json")));
            Set<String> ids=new HashSet<>();remaining.forEach(snapshot->ids.add(snapshot.path("id").asText()));
            assertThat(ids).containsExactlyInAnyOrder(newest,unmanaged);
            assertThat(jdbc.queryForObject("SELECT integrity FROM backup_snapshot WHERE snapshot_id=?",String.class,old)).isEqualTo("EXPIRED");
            Path restored=Files.createDirectory(root.resolve("restored"));
            ResticCommand.local(properties,source,List.of("restore",newest,"--target",restored.toString(),"--verify"));
            List<Path> payloads;
            try(var paths=Files.walk(restored)){payloads=paths.filter(p->p.getFileName().toString().equals("payload.txt")).toList();}
            assertThat(payloads).hasSize(1);assertThat(Files.readString(payloads.getFirst())).isEqualTo("explicit newest retention fixture");
            assertThat(service.policy().lastState()).isEqualTo("SUCCEEDED");
            String proofFile=System.getenv("SMP_RETENTION_PROOF_FILE");
            if(proofFile!=null)Files.writeString(Path.of(proofFile),mapper.writerWithDefaultPrettyPrinter().writeValueAsString(Map.of(
                "scope","DISPOSABLE_SYNTHETIC_RESTIC_FIXTURE_ONLY","operationId",operation.toString(),"newest",newest,"expired",old,
                "unmanagedRetained",unmanaged,"remaining",ids,"retention",service.policy(),"readDataVerified",true,"restoreVerified",true)));
        } finally {db.shutdown();}
    }
    String backup(BackupProperties properties,Path source,LocalDateTime time,String content) throws Exception {
        Files.writeString(source.resolve("payload.txt"),content);
        List<String> args=new ArrayList<>(List.of("backup","--json","--tag","smp:main","--tag","retention-acceptance-fixture"));
        if(time!=null)args.addAll(List.of("--time",time.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))));
        args.add(".");
        String id=null;
        for(String line:ResticCommand.local(properties,source,args).lines().toList()) {
            var item=mapper.readTree(line);if(item.path("message_type").asText().equals("summary"))id=item.path("snapshot_id").asText();
        }
        assertThat(id).matches("[a-f0-9]{64}");return id;
    }
}
