package de.elbracht.smp.identity;

import de.elbracht.smp.api.ApiException;
import de.elbracht.smp.servers.HostAgent;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@org.springframework.validation.annotation.Validated
@RequestMapping("/api/v1/servers/{serverId}/settings")
public class SettingsController {
    private final ServerAccess access; private final JdbcTemplate jdbc; private final HostAgent agent;
    public SettingsController(ServerAccess access, JdbcTemplate jdbc, HostAgent agent) { this.access=access;this.jdbc=jdbc;this.agent=agent; }
    public record Grant(String subject, String role) {}
    public record GrantRequest(@NotBlank @Size(min=1,max=200) String subject, @NotBlank @Pattern(regexp="VIEWER|OPERATOR|ADMIN") String role) {}
    @GetMapping
    public Map<String,Object> get(@PathVariable String serverId, Authentication auth) {
        access.requireAdmin(auth,serverId);
        requireServer(serverId);
        var roles=jdbc.query("SELECT subject,portal_role FROM server_grant WHERE server_id=? ORDER BY subject",(rs,n)->new Grant(rs.getString(1),rs.getString(2)),serverId);
        return Map.of("grants",roles,"hostSource",agent.statusSource(),"lifecycleEnabled",agent.operationsAvailable(),"whitelistEnabled",agent.playerOperationsAvailable(),
            "operatorEnabled",agent.operatorOperationsAvailable(),"consoleEnabled",agent.consoleAvailable(),"backupRetentionDays",7);
    }
    @PutMapping("/grants") @Transactional
    public Grant grant(@PathVariable String serverId,@Valid @RequestBody GrantRequest request,Authentication auth) {
        access.requireAdmin(auth,serverId);
        if(request.subject()==null || request.role()==null || !request.subject().equals(request.subject().strip()) || request.subject().chars().anyMatch(c->c<32))
            throw new ApiException(422,"INVALID_SUBJECT","Gültige Konto-ID erforderlich.");
        jdbc.queryForList("SELECT id FROM managed_server WHERE id=? FOR UPDATE",serverId);
        requireServer(serverId);access.requireAdmin(auth,serverId);
        if(request.subject().equals(auth.getName())) throw new ApiException(409,"SELF_ROLE_CHANGE","Eigene Berechtigung hier nicht ändern.");
        int changed=jdbc.update("UPDATE server_grant SET portal_role=? WHERE subject=? AND server_id=?",request.role(),request.subject(),serverId);
        if(changed==0) jdbc.update("INSERT INTO server_grant VALUES (?,?,?)",request.subject(),serverId,request.role());
        jdbc.update("INSERT INTO audit_event (id,actor,server_id,action,outcome,occurred_at) VALUES (?,?,?,'ROLE_UPDATED','RECORDED',?)",UUID.randomUUID(),auth.getName(),serverId,OffsetDateTime.now());
        return new Grant(request.subject(),request.role());
    }
    @DeleteMapping("/grants") @Transactional
    public void revoke(@PathVariable String serverId,@RequestParam @Size(min=1,max=200) String subject,Authentication auth) {
        access.requireAdmin(auth,serverId);
        if(subject.equals(auth.getName())) throw new ApiException(409,"SELF_ROLE_CHANGE","Eigenen Zugriff hier nicht entfernen.");
        jdbc.queryForList("SELECT id FROM managed_server WHERE id=? FOR UPDATE",serverId);
        requireServer(serverId);access.requireAdmin(auth,serverId);
        int changed=jdbc.update("DELETE FROM server_grant WHERE subject=? AND server_id=?",subject,serverId);
        if(changed>0) jdbc.update("INSERT INTO audit_event (id,actor,server_id,action,outcome,occurred_at) VALUES (?,?,?,'ROLE_REVOKED','RECORDED',?)",UUID.randomUUID(),auth.getName(),serverId,OffsetDateTime.now());
    }
    private void requireServer(String id){if(jdbc.queryForObject("SELECT COUNT(*) FROM managed_server WHERE id=?",Integer.class,id)!=1)throw new ApiException(404,"SERVER_NOT_FOUND","Server nicht vorhanden.");}
}
