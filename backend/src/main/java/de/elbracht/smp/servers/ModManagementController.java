package de.elbracht.smp.servers;
import de.elbracht.smp.api.ApiException;
import de.elbracht.smp.audit.AuditService;
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
@RequestMapping("/api/v1/servers/{serverId}/mods")
public class ModManagementController {
 public record Request(@NotBlank @Pattern(regexp="INSTALL|UPDATE|REMOVE")String action,@Positive long projectId,@Positive Long fileId){}
 public record ExecuteRequest(@NotBlank @Size(max=240)String reason){}
 public record ImportAssignment(@NotBlank @Pattern(regexp="[A-Za-z0-9_.+ -]{1,180}\\.jar")String name,@Positive long projectId,@Positive long fileId){}
 public record ImportRequest(@NotNull @Size(min=1,max=32)java.util.List<@NotNull @Valid ImportAssignment> assignments){}
 private final ServerAccess access;private final ModManagementService mods;private final JobService jobs;private final AuditService audit;
 public ModManagementController(ServerAccess access,ModManagementService mods,JobService jobs,AuditService audit){this.access=access;this.mods=mods;this.jobs=jobs;this.audit=audit;}
 @GetMapping("/state")public ModManagementService.State state(@PathVariable String serverId,Authentication auth){access.require(auth,serverId,false);return mods.state(serverId);}
 @PostMapping("/plans")public ModManagementService.Plan prepare(@PathVariable String serverId,@Valid @RequestBody Request request,Authentication auth){access.requireAdmin(auth,serverId);if(jobs.history(serverId,1,null).blockingJob()!=null)throw new ApiException(409,"SERVER_BUSY","Zuerst den laufenden oder ungeklärten Serverjob prüfen.");var plan=mods.prepare(serverId,request.action(),request.projectId(),request.fileId());audit.recordPlan(serverId,auth.getName(),"MOD_PLAN_CREATED",plan.planId());return plan;}
 @PostMapping("/import-plans")public ModManagementService.Plan importPlan(@PathVariable String serverId,@Valid @RequestBody ImportRequest request,Authentication auth){access.requireAdmin(auth,serverId);if(jobs.history(serverId,1,null).blockingJob()!=null)throw new ApiException(409,"SERVER_BUSY","Zuerst den laufenden oder ungeklärten Serverjob prüfen.");var plan=mods.prepareImport(serverId,request.assignments().stream().map(item->new ModManagementService.Assignment(item.name(),item.projectId(),item.fileId())).toList());audit.recordPlan(serverId,auth.getName(),"MOD_IMPORT_PLAN_CREATED",plan.planId());return plan;}
 @GetMapping("/plans/{id}")public ModManagementService.Plan get(@PathVariable String serverId,@PathVariable UUID id,Authentication auth){access.requireAdmin(auth,serverId);return mods.find(serverId,id);}
 @DeleteMapping("/plans/{id}")public void cancel(@PathVariable String serverId,@PathVariable UUID id,Authentication auth){access.requireAdmin(auth,serverId);mods.cancel(serverId,id);audit.recordPlan(serverId,auth.getName(),"MOD_PLAN_CANCELLED",id);}
 @PostMapping("/plans/{id}/execute")public ResponseEntity<Job> execute(@PathVariable String serverId,@PathVariable UUID id,@RequestHeader("Idempotency-Key")@Pattern(regexp="[A-Za-z0-9_-]{8,128}")String key,@Valid @RequestBody ExecuteRequest request,Authentication auth){access.requireAdmin(auth,serverId);var replay=jobs.replayMods(serverId,auth.getName(),key,request.reason(),id);if(replay!=null)return ResponseEntity.accepted().body(replay);var plan=mods.find(serverId,id);if(!plan.state().equals("PLANNED")||!Instant.parse(plan.expiresAt()).isAfter(Instant.now()))throw new ApiException(409,"MOD_PLAN_EXPIRED","Modplan ist abgelaufen oder bereits verwendet. Neu planen.");return ResponseEntity.accepted().body(jobs.submitMods(serverId,auth.getName(),key,request.reason(),id));}
}
