package de.elbracht.smp.agent;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class OperationController {
    public record Request(@NotNull UUID operationId, @NotNull OperationStore.Type type) {}
    private final AgentProperties properties;
    private final OperationService service;
    public OperationController(AgentProperties properties, OperationService service) { this.properties = properties; this.service = service; }
    @PostMapping("/agent/v1/servers/{serverId}/operations")
    public ResponseEntity<OperationStore.Result> execute(@PathVariable String serverId, @Valid @RequestBody Request request) {
        if (!properties.serverId().equals(serverId)) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(service.execute(request.operationId(), request.type()));
    }
    @ExceptionHandler(OperationRejected.class)
    ResponseEntity<java.util.Map<String, String>> rejected(OperationRejected error) {
        return ResponseEntity.status(error.status).body(java.util.Map.of("code", error.code));
    }
}
