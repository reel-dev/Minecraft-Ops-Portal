package de.elbracht.smp.servers;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/** Long host jobs must not occupy the metric collector's scheduler. */
@Configuration
public class MonitoringScheduling {
 @Bean(name="taskScheduler")ThreadPoolTaskScheduler jobsScheduler(){return scheduler("portal-jobs-");}
 @Bean(name="metricsScheduler")ThreadPoolTaskScheduler metricsScheduler(){return scheduler("metric-history-");}
 @Bean(name="backupScheduler")ThreadPoolTaskScheduler backupScheduler(){return scheduler("backup-schedule-");}
 private static ThreadPoolTaskScheduler scheduler(String prefix){var scheduler=new ThreadPoolTaskScheduler();scheduler.setPoolSize(1);scheduler.setThreadNamePrefix(prefix);scheduler.setRemoveOnCancelPolicy(true);return scheduler;}
}
