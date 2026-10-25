package de.elbracht.smp.servers;

import java.time.Instant;
import java.util.List;
import de.elbracht.smp.api.ApiException;
import de.elbracht.smp.identity.ServerAccess;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/servers")
public class ServerController {
    private final JdbcTemplate jdbc;
    private final ServerAccess access;
    private final HostAgent agent;
    public ServerController(JdbcTemplate jdbc, ServerAccess access, HostAgent agent) {
        this.jdbc = jdbc; this.access = access; this.agent = agent;
    }
    public record Server(String id, String name, String reportedMinecraftVersion, String reportedLoader,
                         Instant verifiedAt, String role, boolean operationsAvailable, String statusSource) {}
    @GetMapping
    public List<Server> list(Authentication auth) {
        return jdbc.query("SELECT * FROM managed_server ORDER BY id", (rs, row) -> new Server(
            rs.getString("id"), rs.getString("display_name"), rs.getString("reported_minecraft_version"),
            rs.getString("reported_loader"), rs.getTimestamp("verified_at") == null ? null : rs.getTimestamp("verified_at").toInstant(),
            access.role(auth, rs.getString("id")), agent.operationsAvailable(), agent.statusSource()))
            .stream().filter(server -> server.role() != null).toList();
    }
    @GetMapping("/{id}/status")
    public HostAgent.Status status(@PathVariable String id, Authentication auth) {
        access.require(auth, id, false);
        if (jdbc.queryForObject("SELECT COUNT(*) FROM managed_server WHERE id = ?", Integer.class, id) == 0)
            throw new ApiException(404, "SERVER_NOT_FOUND", "Server nicht registriert.");
        return agent.observe(id);
    }
}
