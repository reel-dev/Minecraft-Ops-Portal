package de.elbracht.smp.servers;

import java.time.Instant;
import java.util.List;
import de.elbracht.smp.api.ApiException;
import de.elbracht.smp.identity.ServerAccess;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Value;

@RestController
@RequestMapping("/api/v1/servers")
public class ServerController {
    private final JdbcTemplate jdbc;
    private final ServerAccess access;
    private final HostAgent agent;
    private final String environmentLabel;
    public ServerController(JdbcTemplate jdbc, ServerAccess access, HostAgent agent, @Value("${portal.environment-label:}") String environmentLabel) {
        this.jdbc = jdbc; this.access = access; this.agent = agent; this.environmentLabel = environmentLabel;
    }
    public record Server(String id, String name, String reportedMinecraftVersion, String reportedLoader,
                         Instant verifiedAt, String role, boolean operationsAvailable, String statusSource, String environmentLabel,
                         boolean playerOperationsAvailable, boolean operatorOperationsAvailable, boolean consoleAvailable, boolean backupsAvailable,boolean restoreAvailable,boolean modsAvailable) {}
    @GetMapping
    public List<Server> list(Authentication auth) {
        return jdbc.query("SELECT * FROM managed_server ORDER BY id", (rs, row) -> new Server(
            rs.getString("id"), rs.getString("display_name"), rs.getString("reported_minecraft_version"),
            rs.getString("reported_loader"), rs.getTimestamp("verified_at") == null ? null : rs.getTimestamp("verified_at").toInstant(),
            access.role(auth, rs.getString("id")), agent.operationsAvailable(), agent.statusSource(), environmentLabel, agent.playerOperationsAvailable(), agent.operatorOperationsAvailable(), agent.consoleAvailable(),agent.backupsAvailable(),agent.restoreAvailable(),agent.modsAvailable()))
            .stream().filter(server -> server.role() != null).toList();
    }
    @GetMapping("/{id}/status")
    public HostAgent.Status status(@PathVariable String id, Authentication auth) {
        access.require(auth, id, false);
        if (jdbc.queryForObject("SELECT COUNT(*) FROM managed_server WHERE id = ?", Integer.class, id) == 0)
            throw new ApiException(404, "SERVER_NOT_FOUND", "Server nicht registriert.");
        var status=agent.observe(id);
        var running=jdbc.query("SELECT operation_type FROM operation_job WHERE server_id=? AND state='RUNNING' ORDER BY created_at DESC LIMIT 1",(rs,n)->rs.getString(1),id);
        var activity=running.isEmpty()?status.activity():switch(running.getFirst()){case "BACKUP"->HostAgent.Activity.BACKUP;case "RESTORE_TEST","RESTORE_APPLY"->HostAgent.Activity.RESTORE;case "MOD_APPLY"->HostAgent.Activity.MOD_CHANGE;default->status.activity();};
        return new HostAgent.Status(status.serverId(),status.lifecycle(),status.health(),activity,status.minecraftVersion(),status.loader(),status.loaderVersion(),status.observedAt(),status.reasonCode(),status.playersOnline());
    }

    @GetMapping("/{id}/players/online")
    public HostAgent.OnlinePlayers players(@PathVariable String id, Authentication auth) {
        access.require(auth, id, false);
        if (jdbc.queryForObject("SELECT COUNT(*) FROM managed_server WHERE id = ?", Integer.class, id) == 0)
            throw new ApiException(404, "SERVER_NOT_FOUND", "Server nicht registriert.");
        return agent.onlinePlayers(id);
    }
}
