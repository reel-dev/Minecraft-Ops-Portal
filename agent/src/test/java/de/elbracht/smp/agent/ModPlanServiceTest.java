package de.elbracht.smp.agent;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class ModPlanServiceTest {
 @TempDir Path root;EmbeddedDatabase database;JdbcTemplate jdbc;LocalBackupService backups;DockerControl control;ContainerProbe docker;RconPlayerReader rcon;ModPlanService service;ObjectMapper mapper=new ObjectMapper();Map<UUID,Path> plans=new HashMap<>();AtomicBoolean running=new AtomicBoolean(true);
 @BeforeEach void setup()throws Exception{
  database=new EmbeddedDatabaseBuilder().generateUniqueName(true).setType(EmbeddedDatabaseType.H2).addScript("schema.sql").build();jdbc=new JdbcTemplate(database);backups=mock(LocalBackupService.class);control=mock(DockerControl.class);docker=mock(ContainerProbe.class);rcon=mock(RconPlayerReader.class);when(backups.bootTestsAvailable()).thenReturn(true);when(rcon.available()).thenReturn(true);
  when(control.readTestModHashes()).thenReturn(Map.of());when(control.readTestModManifest()).thenReturn(new byte[0]);when(control.testTargetIdentity()).thenReturn("b".repeat(64));when(control.readTestRuntimeProfile()).thenReturn(new DockerControl.RuntimeProfile("26.3","FABRIC","0.19.5"));when(control.readTestJavaVersion()).thenReturn("25.0.4.1");when(docker.inspect()).thenAnswer(call->new ContainerProbe.State(running.get()?"running":"exited",running.get(),false,false,false,true,true));doAnswer(call->{running.set(false);return null;}).when(control).stop();doAnswer(call->{running.set(true);return null;}).when(control).start();
  when(backups.newModPlanDirectory(any())).thenAnswer(call->{UUID id=call.getArgument(0);Path directory=Files.createDirectory(root.resolve("mod-plan-"+id));plans.put(id,directory);return directory;});when(backups.modPlanDirectory(any())).thenAnswer(call->plans.get(call.getArgument(0)));
  service=new ModPlanService(new ModControlProperties(true,true,"after-swap"),StatusServiceTest.properties(25585,"docker-test-control"),backups,control,docker,()->new MinecraftProbe.Result("26.3",0),rcon,jdbc,mapper);
 }
 @AfterEach void close(){database.shutdown();}
 ModPlanService.Package pack(String environment)throws Exception{
  var output=new ByteArrayOutputStream();try(var zip=new ZipOutputStream(output)){zip.putNextEntry(new ZipEntry("fabric.mod.json"));zip.write(mapper.writeValueAsBytes(Map.of("schemaVersion",1,"id","explicit_local_fixture","version","1.0.0","environment",environment)));zip.closeEntry();}byte[] bytes=output.toByteArray();var pin=new ModPlanService.Pin(1,2,bytes.length,HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)),HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(bytes)),List.of(),"LOCAL_ACCEPTANCE_FIXTURE");return new ModPlanService.Package(pin,Base64.getEncoder().encodeToString(bytes));
 }
 ModPlanService.Request request(ModPlanService.Package pack)throws Exception{return new ModPlanService.Request("26.3","0.19.5","25.0.4.1",service.state().revision(),List.of(new ModPlanService.Root(1,2)),List.of(pack));}
 @Test void manualFilesAndStaleRevisionNeverBecomeAnOwnedPlan()throws Exception{
  var request=request(pack("server"));when(control.readTestModHashes()).thenReturn(Map.of("manual.jar","c".repeat(64)));assertThat(service.state().managed()).isFalse();assertThatThrownBy(()->service.create(request)).isInstanceOf(OperationRejected.class);verify(backups,never()).newModPlanDirectory(any());verify(backups,never()).createBeforeRestore(any());
 }
 @Test void pureClientJarAndWrongRuntimeDoNotChangeTheServer()throws Exception{
  assertThatThrownBy(()->service.create(request(pack("client")))).isInstanceOf(IOException.class);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM mod_plan",Integer.class)).isZero();verify(backups).discardModPlanDirectory(any());verify(control,never()).stop();
  var request=request(pack("server"));when(control.readTestJavaVersion()).thenReturn("26");assertThatThrownBy(()->service.create(request)).isInstanceOf(OperationRejected.class);
 }
 @Test void changedPackageCannotReachThePreBackupOrSwap()throws Exception{
  var plan=service.create(request(pack("server")));Files.writeString(plans.get(plan.planId()).resolve("cf-1-2.jar"),"tampered");assertThatThrownBy(()->service.apply(plan.planId(),UUID.randomUUID())).isInstanceOf(OperationRejected.class);verify(backups,never()).createBeforeRestore(any());verify(control,never()).swapTestData(any(),any());
 }
 @Test void removalProducesAnEmptyOwnedSetWithoutDeletingAnyLiveFile()throws Exception{
  var pin=pack("server").pin();byte[] manifest=mapper.writeValueAsBytes(new ModPlanService.Manifest(1,List.of(new ModPlanService.Root(1,2)),List.of(pin)));when(control.readTestModHashes()).thenReturn(Map.of(pin.name(),pin.sha256()));when(control.readTestModManifest()).thenReturn(manifest);
  var plan=service.create(new ModPlanService.Request("26.3","0.19.5","25.0.4.1",service.state().revision(),List.of(),List.of()));assertThat(plan.files()).isEmpty();assertThat(plan.changes()).containsExactly("Entfernen: cf-1-2.jar");verify(control,never()).stop();verify(control,never()).swapTestData(any(),any());
 }
 void prepareSwap(UUID operation,boolean brokenRollback)throws Exception{
  Path stage=Files.createDirectory(root.resolve("backup-"+operation));Files.createDirectory(stage.resolve("mods"));when(backups.createBeforeRestore(operation)).thenAnswer(call->{running.set(false);return "c".repeat(64);});when(backups.stoppedModStage(operation)).thenReturn(stage);var swap=new DockerControl.DataSwap(operation,List.of("world"),List.of("world","mods","portal-mod-manifest.json"));when(control.swapTestData(stage,operation)).thenReturn(swap);if(brokenRollback)doThrow(new IOException()).when(control).rollbackTestData(swap);
 }
 @Test void probePrecedesSwapAndFailedSwapRestoresOriginalStateWithoutRetry()throws Exception{
  var plan=service.create(request(pack("server")));UUID operation=UUID.randomUUID();prepareSwap(operation,false);var result=service.apply(plan.planId(),operation);assertThat(result.state()).isEqualTo("FAILED");assertThat(service.find(plan.planId()).state()).isEqualTo("ROLLED_BACK");
  var order=inOrder(backups,control,rcon);order.verify(backups).createBeforeRestore(operation);order.verify(backups).verifyModStage(any(),eq(operation));order.verify(control).swapTestData(any(),eq(operation));order.verify(control).rollbackTestData(any());order.verify(control).start();order.verify(rcon).readOnlineNames();assertThatThrownBy(()->service.apply(plan.planId(),operation)).isInstanceOf(OperationRejected.class);verify(backups,times(1)).createBeforeRestore(operation);
 }
 @Test void unconfirmedRollbackPreservesAnUnknownPlanAndItsPackages()throws Exception{
  var plan=service.create(request(pack("server")));UUID operation=UUID.randomUUID();prepareSwap(operation,true);assertThat(service.apply(plan.planId(),operation).state()).isEqualTo("UNKNOWN");service.cleanup();assertThat(service.find(plan.planId()).state()).isEqualTo("UNKNOWN");verify(backups,never()).discardModPlanDirectory(plan.planId());
 }
 @Test void importRequiresEveryManualFileAndExactBytesBeforeStaging()throws Exception{
  var pack=pack("server");when(control.readTestModHashes()).thenReturn(Map.of("manual.jar",pack.pin().sha256(),"other.jar","c".repeat(64)));var request=new ModPlanService.Request("26.3","0.19.5","25.0.4.1",service.state().revision(),List.of(new ModPlanService.Root(1,2)),List.of(pack),Map.of("manual.jar",1L));assertThatThrownBy(()->service.create(request)).hasMessage("MOD_IMPORT_INCOMPLETE");
  when(control.readTestModHashes()).thenReturn(Map.of("manual.jar","c".repeat(64)));var mismatch=new ModPlanService.Request("26.3","0.19.5","25.0.4.1",service.state().revision(),List.of(new ModPlanService.Root(1,2)),List.of(pack),Map.of("manual.jar",1L));assertThatThrownBy(()->service.create(mismatch)).hasMessage("MOD_IMPORT_HASH_MISMATCH");verify(backups,never()).newModPlanDirectory(any());verify(control,never()).stop();
 }
 @Test void importCannotReplaceAnUnverifiedManifest()throws Exception{
  var pack=pack("server");when(control.readTestModHashes()).thenReturn(Map.of("manual.jar",pack.pin().sha256()));when(control.readTestModManifest()).thenReturn("{broken".getBytes(java.nio.charset.StandardCharsets.UTF_8));var request=new ModPlanService.Request("26.3","0.19.5","25.0.4.1",service.state().revision(),List.of(new ModPlanService.Root(1,2)),List.of(pack),Map.of("manual.jar",1L));assertThatThrownBy(()->service.create(request)).isInstanceOf(OperationRejected.class);verify(backups,never()).newModPlanDirectory(any());
 }
 @Test void importPlanShowsAllRenamesAndRechecksTheOriginalInventory()throws Exception{
  var pack=pack("server");when(control.readTestModHashes()).thenReturn(Map.of("manual.jar",pack.pin().sha256()));var request=new ModPlanService.Request("26.3","0.19.5","25.0.4.1",service.state().revision(),List.of(new ModPlanService.Root(1,2)),List.of(pack),Map.of("manual.jar",1L));var plan=service.create(request);assertThat(plan.changes()).contains("Entfernen: manual.jar","Hinzufügen: cf-1-2.jar").anyMatch(value->value.startsWith("Import:"));assertThat(jdbc.queryForObject("SELECT import_verified FROM mod_plan WHERE id=?",Boolean.class,plan.planId())).isTrue();
  when(control.readTestModHashes()).thenReturn(Map.of("manual.jar",pack.pin().sha256(),"new.jar","d".repeat(64)));assertThatThrownBy(()->service.apply(plan.planId(),UUID.randomUUID())).isInstanceOf(OperationRejected.class);verify(backups,never()).createBeforeRestore(any());
 }
 @Test void failedImportRestoresTheOriginalUnmanagedInventory()throws Exception{
  var pack=pack("server");when(control.readTestModHashes()).thenReturn(Map.of("manual.jar",pack.pin().sha256()));var request=new ModPlanService.Request("26.3","0.19.5","25.0.4.1",service.state().revision(),List.of(new ModPlanService.Root(1,2)),List.of(pack),Map.of("manual.jar",1L));var plan=service.create(request);UUID operation=UUID.randomUUID();prepareSwap(operation,false);Files.write(root.resolve("backup-"+operation).resolve("mods/manual.jar"),Base64.getDecoder().decode(pack.content()));assertThat(service.apply(plan.planId(),operation).state()).isEqualTo("FAILED");assertThat(service.find(plan.planId()).state()).isEqualTo("ROLLED_BACK");assertThat(service.state().actualFiles()).containsOnlyKeys("manual.jar");verify(control).rollbackTestData(any());
 }
}
