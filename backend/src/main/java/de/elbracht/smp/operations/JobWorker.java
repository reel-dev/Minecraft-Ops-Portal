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
        if (!ready || (!agent.operationsAvailable() && !agent.playerOperationsAvailable() && !agent.consoleAvailable() && !agent.backupsAvailable() && !agent.configurationAvailable() && !agent.restoreAvailable() && !agent.modsAvailable())) return;
        for (Job job : jobs.queued()) {
            if (!jobs.claim(job)) continue;
            try {
                if(job.type()==OperationType.MOD_APPLY)jobs.consoleResult(job,agent.applyMods(job.serverId(),job.jobId(),job.modPlanId()));
                else if(job.type()==OperationType.RESTORE_APPLY)jobs.consoleResult(job,agent.restore(job.serverId(),job.jobId(),job.restorePlanId()));
                else if(job.type()==OperationType.CONFIG_APPLY)jobs.consoleResult(job,agent.configuration(job.serverId(),job.jobId(),job.configurationRevision(),job.configurationJson()));
                else if (job.type() == OperationType.BACKUP || job.type() == OperationType.RESTORE_TEST) jobs.consoleResult(job,agent.backup(job.serverId(),job.jobId(),job.type(),job.snapshotId()));
                else if (job.type() == OperationType.CONSOLE) jobs.consoleResult(job, agent.console(job.serverId(), job.jobId(), job.commandText()));
                else if (job.type().playerOperation()) agent.execute(job.serverId(), job.type(), job.jobId(), job.targetName());
                else agent.execute(job.serverId(), job.type(), job.jobId());
                jobs.finish(job, Job.State.SUCCEEDED, null);
            } catch(HostAgent.KnownFailure failure){jobs.consoleResult(job,failure.result);jobs.finish(job,Job.State.FAILED,failure.code);
            } catch (Exception error) {
                // Timeout/Verbindungsabbruch kann einen unbekannten Ausgang haben.
                // Keine Ausnahmebotschaften oder Zugangsdaten speichern/loggen.
                jobs.finish(job, Job.State.UNKNOWN, "AGENT_RESULT_UNKNOWN");
            }
        }
    }
}
