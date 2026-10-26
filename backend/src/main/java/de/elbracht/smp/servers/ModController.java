package de.elbracht.smp.servers;
import de.elbracht.smp.identity.ServerAccess;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
@RestController @Validated
@RequestMapping("/api/v1/servers/{serverId}/mods")
public class ModController {
 private final HostAgent agent;private final ServerAccess access;private final ModCatalog catalog;
 public ModController(HostAgent agent,ServerAccess access,ModCatalog catalog){this.agent=agent;this.access=access;this.catalog=catalog;}
 @GetMapping public HostAgent.Diagnostics inventory(@PathVariable String serverId,Authentication auth){access.require(auth,serverId,false);return agent.diagnostics(serverId,"mods");}
 @GetMapping("/search") public ModCatalog.Search search(@PathVariable String serverId,@RequestParam(defaultValue="")@Size(max=100)String q,Authentication auth){access.require(auth,serverId,false);return catalog.search(serverId,q);}
 public record PreviewRequest(@Positive long projectId,@Positive long fileId){}
 @PostMapping("/preview") public ModCatalog.DependencyPlan preview(@PathVariable String serverId,@Valid @RequestBody PreviewRequest request,Authentication auth){access.requireAdmin(auth,serverId);return catalog.preview(serverId,request.projectId(),request.fileId());}
 @PostMapping("/inspect") public ModCatalog.Inspection inspect(@PathVariable String serverId,@Valid @RequestBody PreviewRequest request,Authentication auth){access.requireAdmin(auth,serverId);return catalog.inspect(serverId,request.projectId(),request.fileId());}
}
