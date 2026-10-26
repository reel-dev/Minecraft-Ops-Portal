package de.elbracht.smp.agent;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/agent/v1/servers/{serverId}/mods")
public class ModPlanController {
 private final AgentProperties properties;private final ModPlanService service;
 public ModPlanController(AgentProperties properties,ModPlanService service){this.properties=properties;this.service=service;}
 private void target(String id){if(!properties.serverId().equals(id))throw new OperationRejected(404,"SERVER_NOT_FOUND");if(!"docker-test-control".equals(properties.mode())||!service.available())throw new OperationRejected(503,"TEST_MODS_DISABLED");}
 @GetMapping("/state")public ModPlanService.State state(@PathVariable String serverId)throws Exception{target(serverId);return service.state();}
 @PostMapping("/plans")public ModPlanService.Plan create(@PathVariable String serverId,@RequestBody ModPlanService.Request request)throws Exception{target(serverId);return service.create(request);}
 @GetMapping("/plans/{id}")public ModPlanService.Plan get(@PathVariable String serverId,@PathVariable UUID id){target(serverId);return service.find(id);}
 @DeleteMapping("/plans/{id}")public ModPlanService.Plan cancel(@PathVariable String serverId,@PathVariable UUID id){target(serverId);return service.cancel(id);}
 @ExceptionHandler(OperationRejected.class)ResponseEntity<java.util.Map<String,String>> rejected(OperationRejected error){return ResponseEntity.status(error.status).body(java.util.Map.of("code",error.code));}
 @ExceptionHandler(Exception.class)ResponseEntity<java.util.Map<String,String>> invalid(Exception error){return ResponseEntity.unprocessableEntity().body(java.util.Map.of("code","MOD_PLAN_NOT_VERIFIED"));}
}
