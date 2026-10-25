package de.elbracht.smp.operations;

import de.elbracht.smp.servers.HostAgent;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "portal.worker-enabled", havingValue = "true", matchIfMissing = true)
public class JobWorker {
    private final JobService jobs;
    private final HostAgent agent;
    private volatile boolean ready;
    public JobWorker(JobService jobs, HostAgent agent) { this.jobs = jobs; this.agent = agent; }
    @EventListener(ApplicationReadyEvent.class)
    public void recover() { jobs.recover(); ready = true; }
    @Scheduled(fixedDelayString = "${portal.worker-delay-ms:1000}")
    public void run() {
        if (!ready || !agent.operationsAvailable()) return;
        for (Job job : jobs.queued()) {
            if (!jobs.claim(job)) continue;
            try {
                agent.execute(job.serverId(), job.type());
                jobs.finish(job, Job.State.SUCCEEDED, null);
            } catch (Exception error) {
                // Timeout/Verbindungsabbruch kann einen unbekannten Ausgang haben.
                // Keine Ausnahmebotschaften oder Zugangsdaten speichern/loggen.
                jobs.finish(job, Job.State.UNKNOWN, "AGENT_RESULT_UNKNOWN");
            }
        }
    }
}
