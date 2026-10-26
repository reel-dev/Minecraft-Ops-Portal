package de.elbracht.smp;

import de.elbracht.smp.operations.*;
import de.elbracht.smp.servers.HostAgent;
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

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:console-op;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "portal.local-password=test-only", "portal.worker-enabled=false"})
@AutoConfigureMockMvc
class ConsoleAndOperatorTest {
    @Autowired MockMvc mvc; @Autowired JdbcTemplate jdbc; @Autowired JobService jobs; @MockitoBean HostAgent agent;
    @BeforeEach void setup() {
        jdbc.update("DELETE FROM audit_event"); jdbc.update("DELETE FROM operation_job"); jdbc.update("DELETE FROM server_grant");
        for(String role:new String[]{"VIEWER","OPERATOR","ADMIN"}) jdbc.update("INSERT INTO server_grant VALUES (?,'main',?)", role.toLowerCase(),role);
        when(agent.operatorOperationsAvailable()).thenReturn(true); when(agent.playerOperationsAvailable()).thenReturn(true); when(agent.consoleAvailable()).thenReturn(true);
    }
    @Test void operatorCannotGrantOrRevokeOpEvenWithCapabilityEnabled() throws Exception {
        for(String type:new String[]{"OP_GRANT","OP_REVOKE"}) for(String actor:new String[]{"viewer","operator"})
            mvc.perform(post("/api/v1/servers/main/operations").with(user(actor)).with(csrf()).header("Idempotency-Key","op-rights-test").contentType("application/json")
                .content("{\"type\":\""+type+"\",\"reason\":\"test\",\"targetName\":\"TestPlayer\"}")).andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM operation_job",Integer.class)).isZero();
    }
    @Test void adminOpIsDurableAndRequiresExplicitCapability() throws Exception {
        when(agent.operatorOperationsAvailable()).thenReturn(false);
        var payload="{\"type\":\"OP_GRANT\",\"reason\":\"test\",\"targetName\":\"TestPlayer\"}";
        mvc.perform(post("/api/v1/servers/main/operations").with(user("admin")).with(csrf()).header("Idempotency-Key","op-rights-test").contentType("application/json").content(payload)).andExpect(status().isServiceUnavailable());
        when(agent.operatorOperationsAvailable()).thenReturn(true);
        mvc.perform(post("/api/v1/servers/main/operations").with(user("admin")).with(csrf()).header("Idempotency-Key","op-rights-test").contentType("application/json").content(payload)).andExpect(status().isAccepted()).andExpect(jsonPath("$.targetName").value("TestPlayer"));
    }
    @Test void consoleTemplatesCannotBeUsedToBypassOperatorPolicy() throws Exception {
        for(String command:new String[]{"execute run op TestPlayer","say hello","op TestPlayer","list\\nstop"})
            mvc.perform(post("/api/v1/servers/main/operations").with(user("operator")).with(csrf()).header("Idempotency-Key","console-rights-test").contentType("application/json")
                .content("{\"type\":\"CONSOLE\",\"reason\":\"test\",\"commandText\":\""+command+"\"}")).andExpect(result -> assertThat(result.getResponse().getStatus()).isIn(403,422));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM operation_job",Integer.class)).isZero();
    }
    @Test void consoleUsesSameLockAndPersistsActualResult() throws Exception {
        when(agent.console(eq("main"),any(),eq("list"))).thenReturn("There are 0 of a max of 20 players online:");
        var worker=new JobWorker(jobs,agent);worker.recover();
        mvc.perform(post("/api/v1/servers/main/operations").with(user("operator")).with(csrf()).header("Idempotency-Key","console-rights-test").contentType("application/json")
            .content("{\"type\":\"CONSOLE\",\"reason\":\"test\",\"commandText\":\"list\"}")).andExpect(status().isAccepted());
        var job=jobs.history("main",20,null).items().getFirst();
        worker.run();worker.run();
        assertThat(jobs.find("main",job.jobId()).resultText()).contains("There are 0");
        assertThat(jobs.find("main",job.jobId()).state()).isEqualTo(Job.State.SUCCEEDED);
        verify(agent,times(1)).console("main",job.jobId(),"list");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event",Integer.class)).isEqualTo(3);
    }
    @Test void logsAndMembershipRequireOperatorButMetricsAllowViewer() throws Exception {
        when(agent.diagnostics(anyString(),anyString())).thenReturn(new HostAgent.Diagnostics("main",null,null,"SOURCE_NOT_CONFIGURED"));
        for(String path:new String[]{"logs","players/membership"}) mvc.perform(get("/api/v1/servers/main/"+path).with(user("viewer"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/servers/main/metrics").with(user("viewer"))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/servers/other/metrics").with(user("viewer"))).andExpect(status().isForbidden());
    }
    @Test void restoreRequiresAdminAndABoundSnapshotTarget() throws Exception {
        when(agent.backupsAvailable()).thenReturn(true);
        String snapshot="a".repeat(64);
        String body="{\"type\":\"RESTORE_TEST\",\"reason\":\"test\",\"snapshotId\":\""+snapshot+"\"}";
        mvc.perform(post("/api/v1/servers/main/operations").with(user("operator")).with(csrf()).header("Idempotency-Key","restore-rights-test").contentType("application/json").content(body)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/servers/main/operations").with(user("admin")).with(csrf()).header("Idempotency-Key","restore-rights-test").contentType("application/json").content(body)).andExpect(status().isAccepted()).andExpect(jsonPath("$.snapshotId").value(snapshot));
        mvc.perform(post("/api/v1/servers/main/operations").with(user("admin")).with(csrf()).header("Idempotency-Key","restore-rights-test").contentType("application/json").content(body.replace(snapshot,"b".repeat(64)))).andExpect(status().isConflict());
    }
    @Test void configurationRequiresAdminAndRejectsSecretsBeforeCreatingAJob() throws Exception {
        when(agent.configurationAvailable()).thenReturn(true);
        var data=new com.fasterxml.jackson.databind.ObjectMapper().readTree("{\"revision\":\""+"a".repeat(64)+"\",\"fields\":[{\"key\":\"spawn-protection\",\"value\":\"16\",\"managedByEnvironment\":false}]}");
        when(agent.diagnostics("main","configuration")).thenReturn(new HostAgent.Diagnostics("main",java.time.Instant.now(),data,null));
        String body="{\"reason\":\"test\",\"revision\":\""+"a".repeat(64)+"\",\"values\":{\"spawn-protection\":\"12\"}}";
        mvc.perform(post("/api/v1/servers/main/configuration").with(user("operator")).with(csrf()).header("Idempotency-Key","configuration-test").contentType("application/json").content(body)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/servers/main/configuration").with(user("admin")).with(csrf()).header("Idempotency-Key","configuration-test").contentType("application/json").content(body.replace("spawn-protection","rcon.password"))).andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/api/v1/servers/main/configuration").with(user("admin")).with(csrf()).header("Idempotency-Key","configuration-test").contentType("application/json").content(body)).andExpect(status().isAccepted()).andExpect(jsonPath("$.configurationRevision").value("a".repeat(64)));
        mvc.perform(post("/api/v1/servers/main/configuration").with(user("admin")).with(csrf()).header("Idempotency-Key","configuration-test").contentType("application/json").content(body.replace(":\"12\"",":\"13\""))).andExpect(status().isConflict());
    }
    @Test void reconciliationRequiresAdminAndAnExactHostResultWithoutRetryingAnOperation()throws Exception{
        when(agent.configurationAvailable()).thenReturn(true);
        var job=jobs.submitConfiguration("main","admin","reconcile-config-0001","test","a".repeat(64),java.util.Map.of("spawn-protection","12"));jobs.recover();
        when(agent.reconcile("main",job.jobId())).thenReturn(new HostAgent.Reconciled(job.jobId(),OperationType.CONFIG_APPLY,"UNKNOWN",null,null));
        String url="/api/v1/servers/main/jobs/"+job.jobId()+"/reconcile";
        mvc.perform(post(url).with(user("operator")).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post(url).with(user("admin")).with(csrf())).andExpect(status().isConflict());
        assertThat(jobs.history("main",20,null).blockingJob()).isNotNull();
        when(agent.reconcile("main",job.jobId())).thenReturn(new HostAgent.Reconciled(job.jobId(),OperationType.CONFIG_APPLY,"FAILED",null,"Originalrevision anhand Datei bestätigt"));
        mvc.perform(post(url).with(user("admin")).with(csrf())).andExpect(status().isOk()).andExpect(jsonPath("$.state").value("FAILED"));
        assertThat(jobs.history("main",20,null).blockingJob()).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE action='JOB_RECONCILED'",Integer.class)).isEqualTo(1);
        verify(agent,never()).configuration(anyString(),any(),anyString(),anyString());
    }
}
