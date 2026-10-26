package de.elbracht.smp.operations;

import de.elbracht.smp.api.ApiException;
import de.elbracht.smp.servers.HostAgent;
import java.time.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** Daily dispatch uses the existing durable idempotency and per-server mutation lock. */
@Service
@EnableConfigurationProperties(BackupScheduleProperties.class)
public class BackupSchedule {
    public record Policy(boolean enabled, String dailyTime, String zone, String nextRunAt, String state) {}
    private final BackupScheduleProperties properties;
    private final JobService jobs;
    private final HostAgent agent;
    private final Clock clock;
    private final LocalTime time;
    private final ZoneId zone;
    private volatile boolean ready;
    private LocalDate acceptedDate;
    private String dispatchState = "WAITING";

    @Autowired
    public BackupSchedule(BackupScheduleProperties properties, JobService jobs, HostAgent agent) {
        this(properties, jobs, agent, Clock.systemUTC());
    }

    BackupSchedule(BackupScheduleProperties properties, JobService jobs, HostAgent agent, Clock clock) {
        this.properties = properties;
        this.jobs = jobs;
        this.agent = agent;
        this.clock = clock;
        try {
            time = LocalTime.parse(properties.dailyTime());
            zone = ZoneId.of(properties.zone());
            if (time.getSecond() != 0 || time.getNano() != 0 || properties.serverId() == null
                || !properties.serverId().matches("[A-Za-z0-9_-]{1,64}")
                || properties.dispatchWindow() == null || properties.dispatchWindow().isNegative()
                || properties.dispatchWindow().isZero() || properties.dispatchWindow().compareTo(Duration.ofHours(24)) > 0)
                throw new IllegalArgumentException();
        } catch (RuntimeException error) {
            throw new IllegalStateException("Backup-Zeitplan benötigt Server-ID, tägliche Uhrzeit, Zeitzone und ein Zeitfenster bis 24 Stunden");
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ready() { ready = true; }

    @Scheduled(scheduler = "backupScheduler", fixedDelayString = "${portal.backup-schedule.poll-ms:30000}", initialDelay = 30000)
    public void tick() {
        if (ready) dispatch();
    }

    synchronized void dispatch() {
        if (!properties.enabled()) return;
        Instant now = clock.instant();
        LocalDate date = now.atZone(zone).toLocalDate();
        Instant due = date.atTime(time).atZone(zone).toInstant();
        // A spring DST gap moves the scheduled wall time forward; an overlap uses the first occurrence.
        // The date-based key remains unique in both cases. Never catch up an older calendar day.
        if (now.isBefore(due) || !now.isBefore(due.plus(properties.dispatchWindow())) || date.equals(acceptedDate)) return;
        if (!agent.backupsAvailable()) { dispatchState = "SOURCE_NOT_CONFIGURED"; return; }
        try {
            jobs.submit(properties.serverId(), "system:backup", "daily-backup_" + date,
                OperationType.BACKUP, "Tägliche geplante Sicherung (" + properties.zone() + ")");
            // Even FAILED/UNKNOWN is the result for this slot. A restart replays the same persisted job.
            acceptedDate = date;
            dispatchState = "DISPATCHED";
        } catch (ApiException error) {
            dispatchState = error.status == 409 ? "SERVER_BUSY" : "DISPATCH_UNAVAILABLE";
        } catch (RuntimeException error) {
            dispatchState = "STORAGE_UNAVAILABLE";
        }
    }

    public synchronized Policy policy(String serverId) {
        boolean enabled = properties.enabled() && properties.serverId().equals(serverId);
        Instant now = clock.instant();
        LocalDate date = now.atZone(zone).toLocalDate();
        Instant next = date.atTime(time).atZone(zone).toInstant();
        if (date.equals(acceptedDate) || !now.isBefore(next.plus(properties.dispatchWindow())))
            next = date.plusDays(1).atTime(time).atZone(zone).toInstant();
        String state = !enabled ? "DISABLED" : !agent.backupsAvailable() ? "SOURCE_NOT_CONFIGURED"
            : !date.equals(acceptedDate) && !now.isBefore(date.atTime(time).atZone(zone).toInstant().plus(properties.dispatchWindow()))
                ? "MISSED_WINDOW" : dispatchState;
        return new Policy(enabled, time.toString(), zone.getId(), enabled ? next.toString() : null, state);
    }
}
