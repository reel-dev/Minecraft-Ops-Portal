package de.elbracht.smp.operations;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Administrator deployment configuration; no schedule changes from the browser. */
@ConfigurationProperties("portal.backup-schedule")
public record BackupScheduleProperties(
    @DefaultValue("false") boolean enabled,
    @DefaultValue("main") String serverId,
    @DefaultValue("03:00") String dailyTime,
    @DefaultValue("Europe/Berlin") String zone,
    @DefaultValue("2h") Duration dispatchWindow
) {}
