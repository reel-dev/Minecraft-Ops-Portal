package de.elbracht.smp.servers;

import de.elbracht.smp.identity.ServerAccess;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/servers/{serverId}")
public class DiagnosticsController {
    private final HostAgent agent;
    private final ServerAccess access;
    public DiagnosticsController(HostAgent agent, ServerAccess access) { this.agent = agent; this.access = access; }
    @GetMapping("/players/membership")
    public HostAgent.Diagnostics players(@PathVariable String serverId, Authentication auth) {
        access.require(auth, serverId, true); return agent.diagnostics(serverId, "players");
    }
    @GetMapping("/metrics")
    public HostAgent.Diagnostics metrics(@PathVariable String serverId, Authentication auth) {
        access.require(auth, serverId, false); return agent.diagnostics(serverId, "metrics");
    }
    @GetMapping("/logs")
    public HostAgent.Diagnostics logs(@PathVariable String serverId, Authentication auth) {
        access.require(auth, serverId, true); return agent.diagnostics(serverId, "logs");
    }
    @GetMapping("/backups") public HostAgent.Diagnostics backups(@PathVariable String serverId,Authentication auth){access.require(auth,serverId,true);return agent.diagnostics(serverId,"backups");}
    @GetMapping("/backups/retention") public HostAgent.Diagnostics retention(@PathVariable String serverId,Authentication auth){access.requireAdmin(auth,serverId);return agent.diagnostics(serverId,"retention");}
}
