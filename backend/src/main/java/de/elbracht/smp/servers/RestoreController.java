package de.elbracht.smp.servers;
import de.elbracht.smp.api.ApiException;
import de.elbracht.smp.identity.ServerAccess;
import de.elbracht.smp.operations.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
@RestController @Validated
@RequestMapping("/api/v1/servers/{serverId}/restore-plans")
public class RestoreController {
 public record PlanRequest(@NotBlank @Pattern(regexp="[a-f0-9]{64}")String snapshotId,boolean startAfterRestore){}
 public record ExecuteRequest(@NotBlank @Size(max=240)String reason){}
 private final HostAgent agent;private final ServerAccess access;private final JobService jobs;
 private final de.elbracht.smp.audit.AuditService audit;
 public RestoreController(HostAgent agent,ServerAccess access,JobService jobs,de.elbracht.smp.audit.AuditService audit){this.agent=agent;this.access=access;this.jobs=jobs;this.audit=audit;}
 private void require(String server,Authentication auth){access.requireAdmin(auth,server);if(!agent.restoreAvailable())throw new ApiException(503,"RESTORE_NOT_CONFIGURED","Wiederherstellung für diesen Anschluss nicht freigeschaltet.");}
 @PostMapping public HostAgent.RestorePlan create(@PathVariable String serverId,@Valid @RequestBody PlanRequest request,Authentication auth){
  require(serverId,auth);var data=agent.diagnostics(serverId,"backups");boolean eligible=false;
  if(data!=null&&data.data()!=null)for(var snapshot:data.data().path("snapshots"))if(request.snapshotId().equals(snapshot.path("id").asText())&&"LOCAL_ENCRYPTED_FULL_MINECRAFT_DATA".equals(snapshot.path("scope").asText())&&snapshot.path("bootVerifiedAt").isTextual())eligible=true;
  if(!eligible)throw new ApiException(422,"SNAPSHOT_NOT_RESTORE_READY","Dieser Snapshot besitzt noch keinen bestätigten vollständigen Proberestore mit Weltstart.");
  try{var plan=agent.prepareRestore(serverId,request.snapshotId(),request.startAfterRestore());audit.recordPlan(serverId,auth.getName(),"RESTORE_PLAN_CREATED",plan.planId());return plan;}catch(Exception error){throw new ApiException(503,"RESTORE_PLAN_UNAVAILABLE","Restoreplan konnte nicht bestätigt werden. Keine Serveränderung ausgeführt.");}
 }
 @GetMapping("/{id}") public HostAgent.RestorePlan get(@PathVariable String serverId,@PathVariable UUID id,Authentication auth){require(serverId,auth);try{return agent.restorePlan(serverId,id);}catch(Exception error){throw new ApiException(503,"RESTORE_PLAN_UNAVAILABLE","Restoreplan nicht verfügbar.");}}
 @DeleteMapping("/{id}") public void cancel(@PathVariable String serverId,@PathVariable UUID id,Authentication auth){require(serverId,auth);try{agent.cancelRestore(serverId,id);audit.recordPlan(serverId,auth.getName(),"RESTORE_PLAN_CANCELLED",id);}catch(Exception error){throw new ApiException(503,"RESTORE_PLAN_UNAVAILABLE","Restoreplan konnte nicht abgebrochen werden.");}}
 @PostMapping("/{id}/execute") public ResponseEntity<Job> execute(@PathVariable String serverId,@PathVariable UUID id,@RequestHeader("Idempotency-Key")@Pattern(regexp="[A-Za-z0-9_-]{8,128}")String key,@Valid @RequestBody ExecuteRequest request,Authentication auth){
  require(serverId,auth);var replay=jobs.replayRestore(serverId,auth.getName(),key,request.reason(),id);if(replay!=null)return ResponseEntity.accepted().body(replay);
  var plan=get(serverId,id,auth);if(!"PLANNED".equals(plan.state())||!Instant.parse(plan.expiresAt()).isAfter(Instant.now()))throw new ApiException(409,"RESTORE_PLAN_EXPIRED","Restoreplan ist abgelaufen oder wurde bereits verwendet. Neu planen.");
  return ResponseEntity.accepted().body(jobs.submitRestore(serverId,auth.getName(),key,request.reason(),id,plan.snapshotId()));
 }
}
