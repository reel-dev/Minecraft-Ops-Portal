package de.elbracht.smp.operations;

import java.net.URI;
import java.util.UUID;
import de.elbracht.smp.identity.ServerAccess;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/v1/servers/{serverId}")
public class OperationController {
    private final JobService jobs;
    private final ServerAccess access;
    public OperationController(JobService jobs, ServerAccess access) { this.jobs = jobs; this.access = access; }
    public record Request(@NotNull OperationType type, @NotBlank @Size(max = 240) String reason,
                          @Pattern(regexp = "[A-Za-z0-9_]{3,16}") String targetName,
                          @Size(max = 512) String commandText,
                          @Pattern(regexp="[a-f0-9]{64}") String snapshotId) {}
    @PostMapping("/operations")
    public ResponseEntity<Job> submit(@PathVariable String serverId,
        @RequestHeader("Idempotency-Key") @Pattern(regexp = "[A-Za-z0-9_-]{8,128}") String key,
        @Valid @RequestBody Request request, Authentication auth) {
        access.require(auth, serverId, true);
        if (request.type().operatorOperation()) access.requireAdmin(auth, serverId);
        if (request.type() == OperationType.RESTORE_TEST || request.type()==OperationType.RESTORE_APPLY || request.type()==OperationType.MOD_APPLY) access.requireAdmin(auth, serverId);
        if (request.type() == OperationType.CONSOLE) ConsolePolicy.validate(request.commandText(), "ADMIN".equals(access.role(auth, serverId)));
        Job job = jobs.submit(serverId, auth.getName(), key, request.type(), request.reason(), request.targetName(), request.commandText(), request.snapshotId());
        return ResponseEntity.accepted().location(URI.create("/api/v1/servers/" + serverId + "/jobs/" + job.jobId())).body(job);
    }
    @GetMapping("/jobs/{jobId}")
    public Job get(@PathVariable String serverId, @PathVariable UUID jobId, Authentication auth) {
        access.require(auth, serverId, true);
        return jobs.find(serverId, jobId);
    }
    @PostMapping("/jobs/{jobId}/reconcile") public Job reconcile(@PathVariable String serverId,@PathVariable UUID jobId,Authentication auth){access.requireAdmin(auth,serverId);return jobs.reconcile(serverId,jobId,auth.getName());}
    @GetMapping("/jobs")
    public JobService.Page history(@PathVariable String serverId,
        @RequestParam(defaultValue = "20") @Min(1) @Max(50) int limit,
        @RequestParam(required = false) UUID before, Authentication auth) {
        access.require(auth, serverId, true);
        return jobs.history(serverId, limit, before);
    }
}
