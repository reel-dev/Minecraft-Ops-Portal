package de.elbracht.smp.agent;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Immutable restore plan; verified preimage and retained on-volume rollback, test target only. */
@Service @EnableConfigurationProperties(RestoreProperties.class)
public class RestoreService {
 public record Plan(UUID planId,String snapshotId,String createdAt,String expiresAt,String minecraftVersion,String loaderVersion,List<String> components,boolean startAfterRestore,String state){}
 public record Outcome(String state,String result){}
 private final RestoreProperties properties;private final LocalBackupService backups;private final DockerControl control;private final ContainerProbe docker;private final MinecraftProbe minecraft;private final RconPlayerReader rcon;private final JdbcTemplate jdbc;private final ObjectMapper mapper;private final AtomicBoolean faultUsed=new AtomicBoolean();
 public RestoreService(RestoreProperties properties,AgentProperties agent,LocalBackupService backups,DockerControl control,ContainerProbe docker,MinecraftProbe minecraft,RconPlayerReader rcon,JdbcTemplate jdbc,ObjectMapper mapper){
  this.properties=properties;this.backups=backups;this.control=control;this.docker=docker;this.minecraft=minecraft;this.rcon=rcon;this.jdbc=jdbc;this.mapper=mapper;
  if(properties.enabled()&&(!"docker-test-control".equals(agent.mode())||!backups.available()||!rcon.available()))throw new IllegalStateException("Wiederherstellung benötigt den vollständigen isolierten Testanschluss");
 }
 public boolean available(){return properties.enabled();}
 public synchronized Plan create(String snapshot,boolean startAfter)throws Exception{
  synchronized(BackupRetention.REPOSITORY_GUARD){return createGuarded(snapshot,startAfter);}
 }
 private Plan createGuarded(String snapshot,boolean startAfter)throws Exception{
  if(!available())throw new OperationRejected(503,"TEST_RESTORE_DISABLED");
  if(jdbc.queryForObject("SELECT COUNT(*) FROM restore_plan WHERE state='PLANNED' AND expires_at>?",Integer.class,Instant.now().toString())>=2)throw new OperationRejected(409,"RESTORE_PLAN_LIMIT");
  control.verifyTarget();UUID id=UUID.randomUUID();Path stage=backups.stageRestorePlan(id,snapshot);
  boolean recorded=false;try{
   var proof=mapper.readTree(Files.readAllBytes(stage.resolve(".portal-snapshot.json")));if(!"26.3".equals(proof.path("minecraftVersion").asText())||!"0.19.5".equals(proof.path("loaderVersion").asText())||!TestDockerControl.TEST_IMAGE.equals(proof.path("image").asText()))throw new IOException("Snapshot-Laufzeitprofil ungeeignet");
   Path serverProperties=stage.resolve("server.properties");Files.write(serverProperties,rcon.prepareRestoredProperties(Files.readAllBytes(serverProperties)));
   var now=Instant.now();jdbc.update("INSERT INTO restore_plan (id,snapshot_id,created_at,expires_at,target_identity,stage_digest,start_after,state) VALUES (?,?,?,?,?,?,?,'PLANNED')",id,snapshot,now.toString(),now.plusSeconds(900).toString(),control.testTargetIdentity(),digest(stage),startAfter);recorded=true;return find(id);
  }finally{if(!recorded)backups.discardPlanDirectory(id);}
 }
 public Plan find(UUID id){return jdbc.query("SELECT * FROM restore_plan WHERE id=?",(rs,n)->new Plan(id,rs.getString("snapshot_id"),rs.getString("created_at"),rs.getString("expires_at"),"26.3","0.19.5",List.of("Welt und Dimensionen","Mods und Konfiguration","Spielerdaten","Minecraft-Laufzeitdateien"),rs.getBoolean("start_after"),rs.getString("state")),id).stream().findFirst().orElseThrow(()->new OperationRejected(404,"RESTORE_PLAN_NOT_FOUND"));}
 public synchronized Plan cancel(UUID id){var current=find(id);if(current.state().equals("CANCELLED"))return current;if(jdbc.update("UPDATE restore_plan SET state='CANCELLED' WHERE id=? AND state='PLANNED'",id)!=1)throw new OperationRejected(409,"RESTORE_PLAN_NOT_CANCELLABLE");return find(id);}
 @org.springframework.scheduling.annotation.Scheduled(fixedDelay=60000,initialDelay=60000)
 public synchronized void cleanup(){
  if(!available())return;var now=Instant.now();
  var ids=jdbc.query("SELECT id FROM restore_plan WHERE stage_cleaned=FALSE AND (state='CANCELLED' OR (state='PLANNED' AND expires_at<?) OR (state IN ('SUCCEEDED','ROLLED_BACK') AND created_at<?)) LIMIT 10",(rs,n)->rs.getObject(1,UUID.class),now.toString(),now.minusSeconds(7*86400).toString());
  for(UUID id:ids)try{backups.discardPlanDirectory(id);jdbc.update("UPDATE restore_plan SET stage_cleaned=TRUE,state=CASE WHEN state='PLANNED' THEN 'EXPIRED' ELSE state END WHERE id=?",id);}catch(IOException ignored){}
 }
 public synchronized Outcome apply(UUID planId,UUID operation)throws Exception{
  if(!available())throw new OperationRejected(503,"TEST_RESTORE_DISABLED");var plan=find(planId);
  var row=jdbc.queryForMap("SELECT * FROM restore_plan WHERE id=?",planId);
  if(!"PLANNED".equals(plan.state())||!Instant.parse(plan.expiresAt()).isAfter(Instant.now()))throw new OperationRejected(409,"RESTORE_PLAN_EXPIRED");
  final Path stage;try{stage=backups.planDirectory(planId);if(!control.testTargetIdentity().equals(row.get("TARGET_IDENTITY"))||!digest(stage).equals(row.get("STAGE_DIGEST")))throw new OperationRejected(409,"RESTORE_PLAN_CHANGED");}
  catch(OperationRejected error){throw error;}catch(Exception error){throw new OperationRejected(409,"RESTORE_PLAN_CHANGED");}
  var before=docker.inspect();if(!before.targetMatches()||before.paused()||before.restarting()||before.dead())throw new OperationRejected(409,"RESTORE_SOURCE_UNAVAILABLE");
  boolean wasRunning=before.running();DockerControl.DataSwap swap=null;boolean originalIntact=true;
  jdbc.update("UPDATE restore_plan SET state='APPLYING',operation_id=? WHERE id=? AND state='PLANNED'",operation,planId);
  try{
   String preBackup=backups.createBeforeRestore(operation);jdbc.update("UPDATE restore_plan SET pre_backup=? WHERE id=?",preBackup,planId);
   swap=control.swapTestData(stage,operation);originalIntact=false;jdbc.update("UPDATE restore_plan SET swap_json=? WHERE id=?",mapper.writeValueAsString(swap),planId);
   if("after-swap".equals(properties.fault())&&faultUsed.compareAndSet(false,true))throw new IOException("Expliziter lokaler Testfehler nach Datenwechsel");
   if(plan.startAfterRestore()){control.start();ready();}else{var stopped=docker.inspect();if(stopped.running()||!stopped.targetMatches())throw new IOException();}
   jdbc.update("UPDATE restore_plan SET state='SUCCEEDED' WHERE id=?",planId);
   return new Outcome("SUCCEEDED","Bestätigter Snapshot vollständig wiederhergestellt. Vorabsicherung erstellt; vorherige Daten als Rückweg erhalten. "+(plan.startAfterRestore()?"Minecraft-Readiness und RCON anschließend bestätigt.":"Server bleibt wie im Plan bestätigt ausgeschaltet."));
  }catch(Exception failure){
   if(failure instanceof InterruptedException)Thread.currentThread().interrupt();
   if(failure instanceof DockerControl.DataSwapFailure swapFailure)originalIntact=swapFailure.originalRestored;
   try{
    if(swap!=null){var running=docker.inspect();if(running.running())control.stop();control.rollbackTestData(swap);originalIntact=true;}
    if(!originalIntact)throw new IOException();
    if(wasRunning){var current=docker.inspect();if(!current.running())control.start();ready();}else{var current=docker.inspect();if(current.running())control.stop();if(docker.inspect().running())throw new IOException();}
    jdbc.update("UPDATE restore_plan SET state='ROLLED_BACK' WHERE id=?",planId);return new Outcome("FAILED","Wiederherstellung fehlgeschlagen. Vorheriger Datenbestand und Betriebszustand anhand des Rückwegs wiederhergestellt und geprüft. Keine automatische Wiederholung.");
   }catch(Exception rollback){jdbc.update("UPDATE restore_plan SET state='UNKNOWN' WHERE id=?",planId);return new Outcome("UNKNOWN","Wiederherstellung oder Rückweg nicht vollständig bestätigt. Datenkopien bleiben erhalten; manuelle Prüfung erforderlich.");}
  }
 }
 private void ready()throws Exception{long deadline=System.nanoTime()+Duration.ofSeconds(90).toNanos();while(System.nanoTime()<deadline){try{var state=docker.inspect();if(state.targetMatches()&&state.running()&&!state.paused()&&!state.restarting()&&!state.dead()&&state.portMatches()){var ping=minecraft.query();var profile=control.readTestRuntimeProfile();if("26.3".equals(ping.minecraftVersion())&&profile!=null&&"0.19.5".equals(profile.loaderVersion())){rcon.readOnlineNames();return;}}}catch(IOException ignored){}Thread.sleep(500);}throw new IOException("Readiness nicht bestätigt");}
 static String digest(Path root)throws Exception{
  MessageDigest digest=MessageDigest.getInstance("SHA-256");try(var paths=Files.walk(root)){var all=paths.sorted().toList();if(all.size()>100000)throw new IOException();for(Path path:all){
   if(Files.isSymbolicLink(path))throw new IOException();if(Files.isDirectory(path,LinkOption.NOFOLLOW_LINKS))continue;if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))throw new IOException();digest.update(root.relativize(path).toString().replace('\\','/').getBytes(java.nio.charset.StandardCharsets.UTF_8));digest.update((byte)0);
   try(var input=Files.newInputStream(path,LinkOption.NOFOLLOW_LINKS)){byte[] buffer=new byte[8192];int count;while((count=input.read(buffer))>=0)digest.update(buffer,0,count);}digest.update((byte)0);
  }}return HexFormat.of().formatHex(digest.digest());
 }
}
