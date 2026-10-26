package de.elbracht.smp.agent;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/agent/v1/servers/{serverId}/restore-plans")
public class RestorePlanController {
 public record Request(@NotBlank @Pattern(regexp="[a-f0-9]{64}")String snapshotId,boolean startAfterRestore){}
 private final AgentProperties properties;private final RestoreService service;
 public RestorePlanController(AgentProperties properties,RestoreService service){this.properties=properties;this.service=service;}
 private void target(String id){if(!properties.serverId().equals(id))throw new OperationRejected(404,"SERVER_NOT_FOUND");if(!"docker-test-control".equals(properties.mode())||!service.available())throw new OperationRejected(503,"TEST_RESTORE_DISABLED");}
 @PostMapping public RestoreService.Plan create(@PathVariable String serverId,@Valid @RequestBody Request request)throws Exception{target(serverId);return service.create(request.snapshotId(),request.startAfterRestore());}
 @GetMapping("/{id}") public RestoreService.Plan get(@PathVariable String serverId,@PathVariable UUID id){target(serverId);return service.find(id);}
 @DeleteMapping("/{id}") public RestoreService.Plan cancel(@PathVariable String serverId,@PathVariable UUID id){target(serverId);return service.cancel(id);}
 @ExceptionHandler(OperationRejected.class)ResponseEntity<java.util.Map<String,String>> rejected(OperationRejected error){return ResponseEntity.status(error.status).body(java.util.Map.of("code",error.code));}
}
