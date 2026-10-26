package de.elbracht.smp.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Seven wall-clock days, verified snapshots only. Unmanaged snapshots and active plans are retained. */
@Service
@org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization
public class BackupRetention {
    // One agent instance owns one repository. Restore-plan creation must not race forget/prune.
    static final Object REPOSITORY_GUARD=new Object();
    public record Entry(String id, Instant createdAt, boolean verified, boolean referenced) {}
    public record Preview(String observedAt, String cutoff, String revision, String latestSuccessfulId,
                          boolean stale, List<Entry> candidates, int retained, int unmanaged) {}
    public record Policy(boolean enabled, int days, String lastAppliedAt, Integer lastForgottenCount, String lastState) {}
    private final BackupProperties properties;
    private final AgentProperties agent;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final Clock clock;

    @Autowired
    public BackupRetention(BackupProperties properties, AgentProperties agent, JdbcTemplate jdbc, ObjectMapper mapper) {
        this(properties, agent, jdbc, mapper, Clock.systemUTC());
    }
    BackupRetention(BackupProperties properties, AgentProperties agent, JdbcTemplate jdbc, ObjectMapper mapper, Clock clock) {
        this.properties=properties; this.agent=agent; this.jdbc=jdbc; this.mapper=mapper; this.clock=clock;
        if(properties.retentionEnabled() && (!properties.enabled() || !"docker-test-control".equals(agent.mode())))
            throw new IllegalStateException("Aufbewahrung benötigt den ausdrücklich freigeschalteten lokalen Backupanschluss");
    }

    @jakarta.annotation.PostConstruct
    void recover() { jdbc.update("UPDATE backup_retention_run SET state='UNKNOWN' WHERE state='RUNNING'"); }

    public Policy policy() {
        var runs=jdbc.query("SELECT state,finished_at,forgotten_count FROM backup_retention_run ORDER BY started_at DESC LIMIT 1",
            (rs,n)->new Policy(properties.retentionEnabled(),7,rs.getString("finished_at"),rs.getInt("forgotten_count"),rs.getString("state")));
        return runs.isEmpty()?new Policy(properties.retentionEnabled(),7,null,null,"NOT_RUN"):runs.getFirst();
    }

    public synchronized Preview preview() throws Exception {
        if(!properties.enabled() || !"docker-test-control".equals(agent.mode())) throw new IOException("Keine Aufbewahrungsquelle");
        List<Entry> entries=inventory();
        var decision=decide(entries,clock.instant());
        var digest=MessageDigest.getInstance("SHA-256");
        Path config=properties.repository().toRealPath().resolve("config");
        if(!Files.isRegularFile(config,LinkOption.NOFOLLOW_LINKS) || Files.size(config)>65536)throw new IOException("Repositoryidentität nicht bestätigt");
        digest.update(Files.readAllBytes(config));
        digest.update(mapper.writeValueAsBytes(entries.stream().sorted(Comparator.comparing(Entry::id))
            .map(e->List.of(e.id(),e.createdAt().toString(),e.verified(),e.referenced())).toList()));
        return new Preview(decision.observedAt(),decision.cutoff(),HexFormat.of().formatHex(digest.digest()),
            decision.latestSuccessfulId(),decision.stale(),decision.candidates(),decision.retained(),decision.unmanaged());
    }

    static Preview decide(List<Entry> entries, Instant now) throws IOException {
        if(entries.size()>5000)throw new IOException("Zu viele Snapshots");
        Set<String> ids=new HashSet<>();
        for(var e:entries)if(e==null || e.id()==null || !e.id().matches("[a-f0-9]{64}") || !ids.add(e.id())
            || e.createdAt()==null || e.createdAt().isAfter(now.plusSeconds(60)))throw new IOException("Snapshotbestand nicht eindeutig");
        var latest=entries.stream().filter(Entry::verified).max(Comparator.comparing(Entry::createdAt).thenComparing(Entry::id)).orElse(null);
        Instant cutoff=now.minus(Duration.ofDays(7));
        List<Entry> candidates=entries.stream().filter(e->latest!=null && e.verified() && !e.referenced()
            && !e.id().equals(latest.id()) && e.createdAt().isBefore(cutoff)).sorted(Comparator.comparing(Entry::createdAt).thenComparing(Entry::id)).toList();
        return new Preview(now.toString(),cutoff.toString(),null,latest==null?null:latest.id(),
            latest==null || latest.createdAt().isBefore(cutoff),candidates,entries.size()-candidates.size(),
            (int)entries.stream().filter(e->!e.verified()).count());
    }

    /** Called only after a new normal backup passed read-data; never from a failed backup or pre-restore copy. */
    public synchronized void afterSuccessfulBackup(UUID operation, String snapshot) throws Exception {
        synchronized(REPOSITORY_GUARD){applyGuarded(operation,snapshot);}
    }
    private void applyGuarded(UUID operation,String snapshot) throws Exception {
        if(!properties.retentionEnabled())return;
        if(jdbc.queryForObject("SELECT COUNT(*) FROM backup_retention_run WHERE operation_id=?",Integer.class,operation)!=0)
            throw new IOException("Aufbewahrungsauftrag bereits vorhanden; keine Wiederholung");
        var plan=preview();
        if(!snapshot.equals(plan.latestSuccessfulId()) || plan.stale())throw new IOException("Neue Sicherung nicht als jüngste bestätigt");
        jdbc.update("INSERT INTO backup_retention_run(operation_id,started_at,state,forgotten_count,source_snapshot,inventory_revision,candidate_ids,forgotten_ids) VALUES (?,?,'RUNNING',0,?,?,?,'[]')",operation,clock.instant().toString(),snapshot,plan.revision(),mapper.writeValueAsString(plan.candidates().stream().map(Entry::id).toList()));
        int forgotten=0;
        List<String> confirmed=new ArrayList<>();
        try {
            if(!plan.candidates().isEmpty()) {
                run(List.of("check","--read-data"));
                var unchanged=preview();
                if(!same(plan,unchanged))throw new IOException("Snapshotbestand geändert");
                Set<String> remaining=new HashSet<>(inventory().stream().map(Entry::id).toList());
                for(int offset=0;offset<plan.candidates().size();offset+=100) {
                    var batch=plan.candidates().subList(offset,Math.min(offset+100,plan.candidates().size())).stream().map(Entry::id).toList();
                    List<String> dry=new ArrayList<>(List.of("forget","--dry-run","--json"));dry.addAll(batch);run(dry);
                    if(!remaining.equals(new HashSet<>(inventory().stream().map(Entry::id).toList())))throw new IOException("Snapshotbestand während Vorschau geändert");
                    var fresh=preview();
                    if(!snapshot.equals(fresh.latestSuccessfulId()) || !new HashSet<>(fresh.candidates().stream().map(Entry::id).toList()).containsAll(batch))
                        throw new IOException("Aktuelle Schutzreferenz oder Sicherungszuordnung geändert");
                    List<String> forget=new ArrayList<>(List.of("forget","--json"));forget.addAll(batch);run(forget);
                    remaining.removeAll(batch);
                    var actual=new HashSet<>(inventoryAfterForget(batch).stream().map(Entry::id).toList());
                    if(!remaining.equals(actual) || !actual.contains(snapshot))throw new IOException("Entfernung nicht bestätigt");
                    for(String id:batch)jdbc.update("UPDATE backup_snapshot SET integrity='EXPIRED' WHERE snapshot_id=? AND integrity='READ_DATA_OK'",id);
                    forgotten+=batch.size();
                    confirmed.addAll(batch);
                    jdbc.update("UPDATE backup_retention_run SET forgotten_count=?,forgotten_ids=? WHERE operation_id=?",forgotten,mapper.writeValueAsString(confirmed),operation);
                }
                run(List.of("prune"));
                run(List.of("check","--read-data"));
            }
            jdbc.update("UPDATE backup_retention_run SET state='SUCCEEDED',finished_at=? WHERE operation_id=?",clock.instant().toString(),operation);
        } catch(Exception error) {
            jdbc.update("UPDATE backup_retention_run SET state='UNKNOWN',finished_at=?,forgotten_count=? WHERE operation_id=?",clock.instant().toString(),forgotten,operation);
            throw new IOException("Aufbewahrung nicht vollständig bestätigt; Repository und Job prüfen");
        }
    }

    private boolean same(Preview a,Preview b) {
        return a.revision().equals(b.revision()) && a.candidates().equals(b.candidates()) && Objects.equals(a.latestSuccessfulId(),b.latestSuccessfulId());
    }
    private List<Entry> inventory() throws Exception { return inventoryAfterForget(List.of()); }
    private List<Entry> inventoryAfterForget(List<String> expectedAbsent) throws Exception {
        var data=mapper.readTree(run(List.of("snapshots","--json","--tag","smp:main")));
        if(!data.isArray() || data.size()>5000)throw new IOException("Snapshotliste nicht bestätigt");
        var known=new HashSet<>(jdbc.query("SELECT snapshot_id FROM backup_snapshot WHERE integrity='READ_DATA_OK'",(rs,n)->rs.getString(1)));
        known.removeAll(expectedAbsent);
        var referenced=new HashSet<>(jdbc.query("SELECT snapshot_id FROM restore_plan WHERE state IN ('PLANNED','APPLYING','UNKNOWN') UNION SELECT pre_backup FROM restore_plan WHERE state IN ('PLANNED','APPLYING','UNKNOWN') UNION SELECT pre_backup FROM mod_plan WHERE state IN ('PLANNED','APPLYING','UNKNOWN')",(rs,n)->rs.getString(1)));
        List<Entry> entries=new ArrayList<>();
        Set<String> actual=new HashSet<>();
        for(var item:data) {
            String id=item.path("id").asText();
            if(!id.matches("[a-f0-9]{64}") || !actual.add(id) || !item.path("tags").isArray())throw new IOException("Snapshotkennung nicht bestätigt");
            Set<String> tags=new HashSet<>();item.path("tags").forEach(t->tags.add(t.asText()));
            if(!tags.contains("smp:main"))throw new IOException("Snapshotzuordnung widersprüchlich");
            entries.add(new Entry(id,Instant.parse(item.path("time").asText()),known.contains(id),referenced.contains(id)));
        }
        if(!actual.containsAll(known))throw new IOException("Bestätigte Sicherung fehlt im Repository");
        // Validate duplicates, timestamps and unsupported input even during post-forget verification.
        decide(entries,clock.instant());
        return List.copyOf(entries);
    }
    String run(List<String> args) throws Exception {return ResticCommand.local(properties,properties.workspace(),args);}
}
