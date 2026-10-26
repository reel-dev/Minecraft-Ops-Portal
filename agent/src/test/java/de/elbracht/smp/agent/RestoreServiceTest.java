package de.elbracht.smp.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RestoreServiceTest {
 @TempDir Path stage;
 EmbeddedDatabase database; JdbcTemplate jdbc;
 LocalBackupService backups; DockerControl control; ContainerProbe docker; RconPlayerReader rcon;
 RestoreService service; UUID plan;
 @BeforeEach void setup()throws Exception {
  database=new EmbeddedDatabaseBuilder().generateUniqueName(true).setType(EmbeddedDatabaseType.H2).addScript("schema.sql").build();jdbc=new JdbcTemplate(database);
  backups=mock(LocalBackupService.class);control=mock(DockerControl.class);docker=mock(ContainerProbe.class);rcon=mock(RconPlayerReader.class);
  when(backups.available()).thenReturn(true);when(rcon.available()).thenReturn(true);
  when(control.testTargetIdentity()).thenReturn("b".repeat(64));when(control.readTestRuntimeProfile()).thenReturn(new DockerControl.RuntimeProfile("26.3","FABRIC","0.19.5"));
  when(docker.inspect()).thenReturn(new ContainerProbe.State("running",true,false,false,false,true,true));
  service=new RestoreService(new RestoreProperties(true,"after-swap"),StatusServiceTest.properties(25585,"docker-test-control"),backups,control,docker,()->new MinecraftProbe.Result("26.3",0),rcon,jdbc,new ObjectMapper());
  Files.writeString(stage.resolve("level.dat"),"immutable fixture");plan=UUID.randomUUID();when(backups.planDirectory(plan)).thenReturn(stage);
  jdbc.update("INSERT INTO restore_plan(id,snapshot_id,created_at,expires_at,target_identity,stage_digest,start_after,state) VALUES(?,?,?,?,?,?,TRUE,'PLANNED')",plan,"a".repeat(64),Instant.now().toString(),Instant.now().plusSeconds(900).toString(),"b".repeat(64),RestoreService.digest(stage));
 }
 @AfterEach void close(){database.shutdown();}
 @Test void restorePlanHoldsTheSameRepositoryGuardAsRetentionUntilReferenceIsRecorded()throws Exception {
  Files.writeString(stage.resolve(".portal-snapshot.json"),new ObjectMapper().writeValueAsString(Map.of("minecraftVersion","26.3","loaderVersion","0.19.5","image",TestDockerControl.TEST_IMAGE)));
  Files.writeString(stage.resolve("server.properties"),"explicit-unit-fixture");when(rcon.prepareRestoredProperties(any())).thenReturn("explicit-unit-fixture".getBytes(java.nio.charset.StandardCharsets.UTF_8));
  when(backups.stageRestorePlan(any(),eq("a".repeat(64)))).thenAnswer(call->{assertThat(Thread.holdsLock(BackupRetention.REPOSITORY_GUARD)).isTrue();return stage;});
  var created=service.create("a".repeat(64),true);assertThat(jdbc.queryForObject("SELECT snapshot_id FROM restore_plan WHERE id=? AND state='PLANNED'",String.class,created.planId())).isEqualTo("a".repeat(64));
 }
 @Test void changedStageAndExpiredPlanNeverReachBackupOrDataSwap()throws Exception {
  Files.writeString(stage.resolve("level.dat"),"tampered");
  assertThatThrownBy(()->service.apply(plan,UUID.randomUUID())).isInstanceOf(OperationRejected.class);
  verify(backups,never()).createBeforeRestore(any());verify(control,never()).swapTestData(any(),any());
  jdbc.update("UPDATE restore_plan SET expires_at=? WHERE id=?",Instant.now().minusSeconds(1).toString(),plan);
  assertThatThrownBy(()->service.apply(plan,UUID.randomUUID())).isInstanceOf(OperationRejected.class);verifyNoInteractions(docker);
 }
 @Test void failureAfterSwapRestoresOriginalBeforeReportingKnownFailure()throws Exception {
  UUID operation=UUID.randomUUID();var swap=new DockerControl.DataSwap(operation,List.of("world"),List.of("world"));
  when(backups.createBeforeRestore(operation)).thenReturn("c".repeat(64));when(control.swapTestData(stage,operation)).thenReturn(swap);
  var outcome=service.apply(plan,operation);
  assertThat(outcome.state()).isEqualTo("FAILED");assertThat(service.find(plan).state()).isEqualTo("ROLLED_BACK");
  var order=inOrder(backups,control,rcon);order.verify(backups).createBeforeRestore(operation);order.verify(control).swapTestData(stage,operation);order.verify(control).stop();order.verify(control).rollbackTestData(swap);order.verify(rcon).readOnlineNames();
  assertThatThrownBy(()->service.apply(plan,operation)).isInstanceOf(OperationRejected.class);verify(backups,times(1)).createBeforeRestore(operation);
 }
 @Test void failedRollbackRemainsUnknownAndPreservesRecoveryEvidence()throws Exception {
  UUID operation=UUID.randomUUID();var swap=new DockerControl.DataSwap(operation,List.of("world"),List.of("world"));
  when(backups.createBeforeRestore(operation)).thenReturn("c".repeat(64));when(control.swapTestData(stage,operation)).thenReturn(swap);doThrow(new java.io.IOException()).when(control).rollbackTestData(swap);
  assertThat(service.apply(plan,operation).state()).isEqualTo("UNKNOWN");assertThat(service.find(plan).state()).isEqualTo("UNKNOWN");service.cleanup();verify(backups,never()).discardPlanDirectory(plan);
 }
}
