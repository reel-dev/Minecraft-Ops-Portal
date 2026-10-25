package de.elbracht.smp.agent;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class StatusController {
    private final AgentProperties properties;
    private final StatusService service;
    public StatusController(AgentProperties properties, StatusService service) { this.properties = properties; this.service = service; }
    @GetMapping("/agent/v1/servers/{serverId}/status")
    public ResponseEntity<StatusService.Snapshot> status(@PathVariable String serverId) {
        if (!properties.serverId().equals(serverId)) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(service.observe());
    }
}
