package de.elbracht.smp.servers;

import de.elbracht.smp.api.ApiException;
import de.elbracht.smp.identity.ServerAccess;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/servers/{serverId}/inventory")
public class InventoryController {
    private final InventoryService inventory;
    private final ServerAccess access;
    public InventoryController(InventoryService inventory, ServerAccess access) { this.inventory = inventory; this.access = access; }

    @GetMapping
    public ResponseEntity<InventoryService.Snapshot> read(@PathVariable String serverId, Authentication auth) {
        access.requireAdmin(auth, serverId);
        return response(inventory.read(serverId));
    }

    @PutMapping("/{field}")
    public ResponseEntity<InventoryService.Snapshot> record(@PathVariable String serverId, @PathVariable InventoryService.Field field,
        @RequestHeader(value = "If-Match", required = false) String ifMatch,
        @Valid @RequestBody InventoryService.Input input, Authentication auth) {
        access.requireAdmin(auth, serverId);
        if (ifMatch == null) throw new ApiException(428, "REVISION_REQUIRED", "If-Match mit der geladenen Revision ist erforderlich.");
        if (!ifMatch.matches("\"[0-9]{1,18}\"")) throw new ApiException(422, "INVALID_REQUEST", "If-Match benötigt eine einzelne Revision in Anführungszeichen.");
        long revision = Long.parseLong(ifMatch.substring(1, ifMatch.length() - 1));
        return response(inventory.record(serverId, field, revision, input, auth.getName()));
    }
    private ResponseEntity<InventoryService.Snapshot> response(InventoryService.Snapshot result) {
        return ResponseEntity.ok().eTag("\"" + result.revision() + "\"").body(result);
    }
}
