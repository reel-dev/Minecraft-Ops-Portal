package de.elbracht.smp.operations;

import de.elbracht.smp.servers.HostAgent;
import java.time.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:backup-schedule;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","portal.local-password=test-only","portal.worker-enabled=false"})
@AutoConfigureMockMvc
class BackupSchedulePersistenceTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired JobService jobs;
    @Autowired MockMvc mvc;
    @MockitoBean HostAgent agent;
    @BeforeEach void prepare() {
        jdbc.update("DELETE FROM audit_event");
        jdbc.update("DELETE FROM operation_job");
        jdbc.update("DELETE FROM server_grant");
        jdbc.update("INSERT INTO server_grant VALUES ('operator','main','OPERATOR')");
        when(agent.backupsAvailable()).thenReturn(true);
    }
    BackupSchedule schedule() {
        return new BackupSchedule(new BackupScheduleProperties(true,"main","03:00","Europe/Berlin",Duration.ofHours(2)),
            jobs,agent,Clock.fixed(Instant.parse("2026-10-26T02:01:00Z"),ZoneOffset.UTC));
    }
    @Test void restartReplaysTheSamePersistedUnknownBackupWithoutAnotherMutation() {
        schedule().dispatch();
        var original = jobs.history("main",10,null).blockingJob();
        assertThat(original).isNotNull();
        jdbc.update("UPDATE operation_job SET state='RUNNING' WHERE id=?",original.jobId());
        jobs.recover();
        schedule().dispatch();
        var page = jobs.history("main",10,null);
        assertThat(page.items()).hasSize(1);
        assertThat(page.blockingJob().jobId()).isEqualTo(original.jobId());
        assertThat(page.blockingJob().state()).isEqualTo(Job.State.UNKNOWN);
        verify(agent,never()).backup(anyString(),any(),any(),any());
    }
    @Test void aConcurrentManualJobKeepsItsLockAndLaterDispatchUsesTheSameSlot() {
        var manual = jobs.submit("main","operator","manual-backup-key",OperationType.BACKUP,"Explicit unit fixture");
        var scheduled = schedule();
        scheduled.dispatch();
        assertThat(jobs.history("main",10,null).items()).hasSize(1);
        assertThat(jobs.claim(manual)).isTrue();
        jobs.finish(manual,Job.State.FAILED,"EXPLICIT_UNIT_FIXTURE");
        scheduled.dispatch();
        assertThat(jobs.history("main",10,null).items()).hasSize(2);
        assertThat(jobs.history("main",10,null).blockingJob().actor()).isEqualTo("system:backup");
    }
    @Test void policyNeedsAuthenticatedServerOperatorAndDoesNotEnableByReading() throws Exception {
        mvc.perform(get("/api/v1/servers/main/backup-policy")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/servers/main/backup-policy").with(user("stranger"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/servers/main/backup-policy").with(user("operator")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(false)).andExpect(jsonPath("$.state").value("DISABLED"));
        assertThat(jobs.history("main",10,null).items()).isEmpty();
    }
}
