package de.elbracht.smp.audit;

import de.elbracht.smp.identity.ServerAccess;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
@RequestMapping("/api/v1/servers/{serverId}/audit")
public class AuditController {
    private final AuditService audit;
    private final ServerAccess access;
    public AuditController(AuditService audit, ServerAccess access) { this.audit = audit; this.access = access; }
    @GetMapping
    public AuditService.Page list(@PathVariable String serverId, @RequestParam(required = false) UUID jobId,
        @RequestParam(defaultValue = "20") @Min(1) @Max(50) int limit,
        @RequestParam(required = false) UUID before, Authentication auth) {
        access.requireAdmin(auth, serverId);
        return audit.list(serverId, jobId, limit, before);
    }
}
