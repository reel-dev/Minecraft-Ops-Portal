package de.elbracht.smp.agent;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class OperationController {
    public record Request(@NotNull UUID operationId, @NotNull OperationStore.Type type,
                          @jakarta.validation.constraints.Pattern(regexp = "[A-Za-z0-9_]{3,16}") String targetName,
                          @jakarta.validation.constraints.Size(max = 512) String commandText,
                          @jakarta.validation.constraints.Pattern(regexp="[a-f0-9]{64}") String snapshotId,
                          java.util.Map<String,String> configuration,String revision,UUID restorePlanId,UUID modPlanId) {}
    private final AgentProperties properties;
    private final OperationService service;
    public OperationController(AgentProperties properties, OperationService service) { this.properties = properties; this.service = service; }
    @PostMapping("/agent/v1/servers/{serverId}/operations")
    public ResponseEntity<OperationStore.Result> execute(@PathVariable String serverId, @Valid @RequestBody Request request) {
        if (!properties.serverId().equals(serverId)) return ResponseEntity.notFound().build();
        if(request.type()==OperationStore.Type.MOD_APPLY){if(request.targetName()!=null||request.commandText()!=null||request.snapshotId()!=null||request.configuration()!=null||request.revision()!=null||request.restorePlanId()!=null)throw new OperationRejected(422,"INVALID_REQUEST");return ResponseEntity.ok(service.mods(request.operationId(),request.modPlanId()));}
        if(request.modPlanId()!=null)throw new OperationRejected(422,"INVALID_REQUEST");
        if(request.type()==OperationStore.Type.RESTORE_APPLY){if(request.targetName()!=null||request.commandText()!=null||request.snapshotId()!=null||request.configuration()!=null||request.revision()!=null)throw new OperationRejected(422,"INVALID_REQUEST");return ResponseEntity.ok(service.restore(request.operationId(),request.restorePlanId()));}
        if(request.restorePlanId()!=null)throw new OperationRejected(422,"INVALID_REQUEST");
        if(request.type()==OperationStore.Type.CONFIG_APPLY){if(request.targetName()!=null||request.commandText()!=null||request.snapshotId()!=null)throw new OperationRejected(422,"INVALID_REQUEST");return ResponseEntity.ok(service.configuration(request.operationId(),request.revision(),request.configuration()));}
        if(request.configuration()!=null||request.revision()!=null)throw new OperationRejected(422,"INVALID_CONFIGURATION");
        if(request.type()==OperationStore.Type.BACKUP || request.type()==OperationStore.Type.RESTORE_TEST){
            if(request.targetName()!=null || request.commandText()!=null)throw new OperationRejected(422,"INVALID_REQUEST");
            return ResponseEntity.ok(service.backup(request.operationId(),request.type(),request.snapshotId()));
        }
        if(request.snapshotId()!=null)throw new OperationRejected(422,"INVALID_SNAPSHOT");
        if (request.type() == OperationStore.Type.CONSOLE) {
            if (request.targetName() != null) throw new OperationRejected(422, "INVALID_PLAYER_TARGET");
            return ResponseEntity.ok(service.console(request.operationId(), request.commandText()));
        }
        if (request.commandText() != null) throw new OperationRejected(422, "INVALID_COMMAND");
        return ResponseEntity.ok(service.execute(request.operationId(), request.type(), request.targetName()));
    }
    @ExceptionHandler(OperationRejected.class)
    ResponseEntity<java.util.Map<String, String>> rejected(OperationRejected error) {
        return ResponseEntity.status(error.status).body(java.util.Map.of("code", error.code));
    }
    @PostMapping("/agent/v1/servers/{serverId}/operations/{operationId}/reconcile")
    public ResponseEntity<OperationStore.Result> reconcile(@PathVariable String serverId,@PathVariable UUID operationId){
        if(!properties.serverId().equals(serverId))return ResponseEntity.notFound().build();return ResponseEntity.ok(service.reconcile(operationId));
    }
}
