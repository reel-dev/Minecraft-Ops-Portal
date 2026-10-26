package de.elbracht.smp.servers;
import de.elbracht.smp.identity.ServerAccess;
import de.elbracht.smp.operations.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
@RestController @Validated
@RequestMapping("/api/v1/servers/{serverId}/configuration")
public class ConfigurationController {
 private final HostAgent agent;private final ServerAccess access;private final JobService jobs;
 public ConfigurationController(HostAgent agent,ServerAccess access,JobService jobs){this.agent=agent;this.access=access;this.jobs=jobs;}
 public record Request(@NotBlank @Size(max=240)String reason,@NotBlank @Pattern(regexp="[a-f0-9]{64}")String revision,@NotEmpty Map<String,String> values){}
 @GetMapping public HostAgent.Diagnostics read(@PathVariable String serverId,Authentication auth){access.requireAdmin(auth,serverId);return agent.diagnostics(serverId,"configuration");}
 @PostMapping public ResponseEntity<Job> apply(@PathVariable String serverId,@RequestHeader("Idempotency-Key")@Pattern(regexp="[A-Za-z0-9_-]{8,128}")String key,@Valid @RequestBody Request request,Authentication auth){
  access.requireAdmin(auth,serverId);ConfigurationPolicy.validate(request.revision(),request.values());
  var replay=jobs.replayConfiguration(serverId,auth.getName(),key,request.reason(),request.revision(),request.values());if(replay!=null)return ResponseEntity.accepted().body(replay);
  var current=agent.diagnostics(serverId,"configuration");if(current==null||current.data()==null||!current.data().path("fields").isArray())throw new de.elbracht.smp.api.ApiException(503,"CONFIGURATION_UNAVAILABLE","Konfigurationsquelle nicht verfügbar.");
  if(!request.revision().equals(current.data().path("revision").asText()))throw new de.elbracht.smp.api.ApiException(412,"CONFIGURATION_REVISION_CHANGED","Konfiguration wurde zwischenzeitlich geändert. Neu laden und Plan erneut prüfen.");
  for(String changed:request.values().keySet()){boolean allowed=false;for(var field:current.data().path("fields"))if(changed.equals(field.path("key").asText()))allowed=!field.path("managedByEnvironment").asBoolean(true)&&field.path("value").isTextual();if(!allowed)throw new de.elbracht.smp.api.ApiException(422,"PROPERTY_NOT_EDITABLE","Eine Eigenschaft ist nicht verfügbar oder wird von Docker vorgegeben.");}
  return ResponseEntity.accepted().body(jobs.submitConfiguration(serverId,auth.getName(),key,request.reason(),request.revision(),request.values()));
 }
}
