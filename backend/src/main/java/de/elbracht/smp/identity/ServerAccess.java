package de.elbracht.smp.identity;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class ServerAccess {
    private final JdbcTemplate jdbc;
    public ServerAccess(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public String role(Authentication auth, String serverId) {
        if (auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_LOCAL_ADMIN"))) return "ADMIN";
        return jdbc.query("SELECT portal_role FROM server_grant WHERE subject = ? AND server_id = ?",
            (rs, row) -> rs.getString(1), auth.getName(), serverId).stream().findFirst().orElse(null);
    }
    public void require(Authentication auth, String serverId, boolean mutate) {
        String role = role(auth, serverId);
        if (role == null || (mutate && role.equals("VIEWER"))) throw new AccessDeniedException("Keine Serverberechtigung");
    }
    public void requireAdmin(Authentication auth, String serverId) {
        if (!"ADMIN".equals(role(auth, serverId))) throw new AccessDeniedException("Adminrechte für diesen Server erforderlich");
    }
}
