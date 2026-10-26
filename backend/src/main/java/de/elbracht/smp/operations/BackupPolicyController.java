package de.elbracht.smp.operations;

import de.elbracht.smp.identity.ServerAccess;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
public class BackupPolicyController {
    private final ServerAccess access;
    private final BackupSchedule schedule;
    public BackupPolicyController(ServerAccess access, BackupSchedule schedule) {
        this.access = access;
        this.schedule = schedule;
    }
    @GetMapping("/api/v1/servers/{serverId}/backup-policy")
    public BackupSchedule.Policy read(@PathVariable String serverId, Authentication auth) {
        access.require(auth, serverId, true);
        return schedule.policy(serverId);
    }
}
