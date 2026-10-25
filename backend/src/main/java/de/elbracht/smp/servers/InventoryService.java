package de.elbracht.smp.servers;

import de.elbracht.smp.api.ApiException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryService {
    public enum Field {
        HOST_TOPOLOGY("Proxmox-/Docker-Host-Zuordnung"),
        CONTAINER("Zielcontainer und eindeutige Serverzuordnung"),
        IMAGE_DIGEST("itzg-Image mit Digest"),
        MINECRAFT_VERSION("Tatsächliche Minecraft-Version"),
        LOADER("Loader und Loader-Version"),
        JAVA_RUNTIME("Java-Runtime des Minecraft-Containers"),
        DATA_VOLUME("Datenvolume und Mountart"),
        CONFIGURATION_SOURCE("Quelle für server.properties und Umgebungswerte"),
        RCON("RCON-Aktivierung, interne Erreichbarkeit und Secretablage"),
        READINESS("Readiness-Prüfung"),
        MOD_INVENTORY("Modbestand und Dateihashes"),
        XAERO("Xaero-Projekte und Server-/Clientverteilung"),
        BACKUP("Letzte erfolgreiche Sicherung und Integritätsprüfung"),
        TEST_SERVER("Isolierter Testserver und getrennte Daten"),
        PORTAL_ACCESS("Portalzugriff und OIDC-Provider");
        public final String label;
        Field(String label) { this.label = label; }
    }
    public record Input(@NotBlank @Size(max = 2000) String value,
        @NotBlank @Size(max = 240) String source, @NotNull @PastOrPresent Instant observedAt) {}
    public record Evidence(Field key, String label, String value, String source, Instant observedAt,
        Instant recordedAt, String recordedBy, Long revision) {}
    public record Snapshot(long revision, int documentedCount, int requiredCount, List<Evidence> fields) {}

    private final JdbcTemplate jdbc;
    public InventoryService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Snapshot read(String server) {
        long revision = jdbc.query("SELECT inventory_revision FROM managed_server WHERE id = ?",
            (rs, row) -> rs.getLong(1), server).stream().findFirst()
            .orElseThrow(() -> new ApiException(404, "SERVER_NOT_FOUND", "Server nicht registriert."));
        Map<Field, Evidence> documented = jdbc.query("SELECT * FROM inventory_evidence WHERE server_id = ?", (rs, row) -> {
            Field key = Field.valueOf(rs.getString("field_key"));
            return new Evidence(key, key.label, rs.getString("evidence_value"), rs.getString("source"),
                rs.getTimestamp("observed_at").toInstant(), rs.getTimestamp("recorded_at").toInstant(),
                rs.getString("recorded_by"), rs.getLong("revision"));
        }, server).stream().collect(Collectors.toMap(Evidence::key, Function.identity()));
        List<Evidence> fields = Arrays.stream(Field.values()).map(key -> documented.getOrDefault(key,
            new Evidence(key, key.label, null, null, null, null, null, null))).toList();
        return new Snapshot(revision, documented.size(), Field.values().length, fields);
    }

    @Transactional
    public Snapshot record(String server, Field field, long expectedRevision, Input input, String actor) {
        long revision = jdbc.query("SELECT inventory_revision FROM managed_server WHERE id = ? FOR UPDATE",
            (rs, row) -> rs.getLong(1), server).stream().findFirst()
            .orElseThrow(() -> new ApiException(404, "SERVER_NOT_FOUND", "Server nicht registriert."));
        if (revision != expectedRevision)
            throw new ApiException(412, "INVENTORY_REVISION_CONFLICT", "Bestandsaufnahme wurde inzwischen geändert. Bitte neu laden und Angaben erneut prüfen.");
        long next = revision + 1;
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        var observedAt = input.observedAt().atOffset(ZoneOffset.UTC);
        int changed = jdbc.update("UPDATE inventory_evidence SET evidence_value = ?, source = ?, observed_at = ?, recorded_at = ?, recorded_by = ?, revision = ? WHERE server_id = ? AND field_key = ?",
            input.value().strip(), input.source().strip(), observedAt, now, actor, next, server, field.name());
        if (changed == 0) jdbc.update("INSERT INTO inventory_evidence (server_id,field_key,evidence_value,source,observed_at,recorded_at,recorded_by,revision) VALUES (?,?,?,?,?,?,?,?)",
            server, field.name(), input.value().strip(), input.source().strip(), observedAt, now, actor, next);
        jdbc.update("UPDATE managed_server SET inventory_revision = ? WHERE id = ?", next, server);
        // Keine eingetragenen Werte oder Quellen im Audit; nur Änderungsmetadaten.
        jdbc.update("INSERT INTO audit_event (id,actor,server_id,job_id,action,outcome,occurred_at,subject_key,revision) VALUES (?,?,?,NULL,'INVENTORY_UPDATED','RECORDED',?,?,?)",
            UUID.randomUUID(), actor, server, now, field.name(), next);
        // Manuell dokumentierte Angaben sind keine vollständige technische Abnahme.
        // verified_at und gemeldete Nutzerangaben bleiben unverändert.
        return read(server);
    }
}
