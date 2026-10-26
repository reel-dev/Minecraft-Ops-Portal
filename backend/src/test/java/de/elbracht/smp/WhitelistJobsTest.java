package de.elbracht.smp;

import de.elbracht.smp.api.ApiException;
import de.elbracht.smp.operations.*;
import de.elbracht.smp.servers.HostAgent;
import java.util.UUID;
import java.util.concurrent.*;
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

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:whitelistjobs;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "portal.local-password=test-only-password", "portal.worker-enabled=false"})
@AutoConfigureMockMvc
class WhitelistJobsTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JobService jobs;
    @MockitoBean HostAgent agent;
    @BeforeEach void setup() {
        jdbc.update("DELETE FROM audit_event"); jdbc.update("DELETE FROM operation_job"); jdbc.update("DELETE FROM server_grant");
        jdbc.update("INSERT INTO server_grant VALUES ('viewer','main','VIEWER')");
        jdbc.update("INSERT INTO server_grant VALUES ('operator','main','OPERATOR')");
        when(agent.operationsAvailable()).thenReturn(true); when(agent.playerOperationsAvailable()).thenReturn(true);
    }
    private String body(String name) { return "{\"type\":\"WHITELIST_ADD\",\"reason\":\"Lokaler Test\",\"targetName\":\"" + name + "\"}"; }
    @Test void rolesServerScopeAndCsrfAreRequiredAndOpCannotBeSmuggledIn() throws Exception {
        mvc.perform(post("/api/v1/servers/main/operations").with(csrf()).header("Idempotency-Key", "whitelist-0001").contentType("application/json").content(body("TestPlayer"))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/servers/main/operations").with(user("viewer")).with(csrf()).header("Idempotency-Key", "whitelist-0001").contentType("application/json").content(body("TestPlayer"))).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/servers/main/operations").with(user("operator")).header("Idempotency-Key", "whitelist-0001").contentType("application/json").content(body("TestPlayer"))).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/servers/other/operations").with(user("operator")).with(csrf()).header("Idempotency-Key", "whitelist-0001").contentType("application/json").content(body("TestPlayer"))).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/servers/main/operations").with(user("operator")).with(csrf()).header("Idempotency-Key", "whitelist-0001").contentType("application/json").content(body("TestPlayer").replace("WHITELIST_ADD", "OP_GRANT"))).andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM operation_job", Integer.class)).isZero();
    }
    @Test void targetIsDurableAndPartOfIdempotencyAndCommonServerLock() throws Exception {
        for (int attempt = 0; attempt < 2; attempt++)
            mvc.perform(post("/api/v1/servers/main/operations").with(user("operator")).with(csrf()).header("Idempotency-Key", "whitelist-0001").contentType("application/json").content(body("TestPlayer")))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.targetName").value("TestPlayer"));
        mvc.perform(post("/api/v1/servers/main/operations").with(user("operator")).with(csrf()).header("Idempotency-Key", "whitelist-0001").contentType("application/json").content(body("OtherPlayer"))).andExpect(status().isConflict());
        assertThatThrownBy(() -> jobs.submit("main", "operator", "restart-0001", OperationType.RESTART, "Test"))
            .isInstanceOf(ApiException.class).hasFieldOrPropertyWithValue("code", "SERVER_BUSY");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM operation_job", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event", Integer.class)).isEqualTo(1);
    }
    @Test void namesAndCapabilityFailClosedWithoutCreatingJobs() throws Exception {
        for (String name : new String[]{"bad;op", "aa", "12345678901234567"})
            mvc.perform(post("/api/v1/servers/main/operations").with(user("operator")).with(csrf()).header("Idempotency-Key", "whitelist-0001").contentType("application/json").content(body(name))).andExpect(status().isUnprocessableEntity());
        assertThatThrownBy(() -> jobs.submit("main", "operator", "whitelist-0001", OperationType.WHITELIST_ADD, "Test"))
            .isInstanceOf(ApiException.class).hasFieldOrPropertyWithValue("status", 422);
        assertThatThrownBy(() -> jobs.submit("main", "operator", "whitelist-0001", OperationType.START, "Test", "TestPlayer"))
            .isInstanceOf(ApiException.class).hasFieldOrPropertyWithValue("status", 422);
        when(agent.playerOperationsAvailable()).thenReturn(false);
        mvc.perform(post("/api/v1/servers/main/operations").with(user("operator")).with(csrf()).header("Idempotency-Key", "whitelist-0001").contentType("application/json").content(body("TestPlayer"))).andExpect(status().isServiceUnavailable());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM operation_job", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event", Integer.class)).isZero();
    }
    @Test void workerCarriesTargetAndAuditThroughSamePipeline() {
        var worker = new JobWorker(jobs, agent); worker.recover();
        Job job = jobs.submit("main", "operator", "whitelist-0001", OperationType.WHITELIST_ADD, "Test", "TestPlayer");
        worker.run();
        verify(agent).execute("main", OperationType.WHITELIST_ADD, job.jobId(), "TestPlayer");
        assertThat(jobs.find("main", job.jobId()).state()).isEqualTo(Job.State.SUCCEEDED);
        assertThat(jdbc.queryForList("SELECT outcome FROM audit_event ORDER BY occurred_at", String.class)).containsExactly("QUEUED", "RUNNING", "SUCCEEDED");
    }
    @Test void uncertainResultAndRestartKeepTargetAndBlockBothKindsOfAction() {
        var worker = new JobWorker(jobs, agent); worker.recover();
        Job job = jobs.submit("main", "operator", "whitelist-0001", OperationType.WHITELIST_REMOVE, "Test", "TestPlayer");
        doThrow(new IllegalStateException("private diagnostic")).when(agent).execute("main", OperationType.WHITELIST_REMOVE, job.jobId(), "TestPlayer");
        worker.run(); worker.run();
        assertThat(jobs.find("main", job.jobId()).state()).isEqualTo(Job.State.UNKNOWN);
        assertThat(jobs.find("main", job.jobId()).targetName()).isEqualTo("TestPlayer");
        verify(agent, times(1)).execute("main", OperationType.WHITELIST_REMOVE, job.jobId(), "TestPlayer");
        for (OperationType type : new OperationType[]{OperationType.WHITELIST_ADD, OperationType.RESTART})
            assertThatThrownBy(() -> jobs.submit("main", "operator", "another-0001", type, "Test", type.playerOperation() ? "OtherPlayer" : null))
                .isInstanceOf(ApiException.class).hasFieldOrPropertyWithValue("code", "SERVER_BUSY");
    }
    @Test void parallelLifecycleAndWhitelistRequestsCannotBothBeAccepted() throws Exception {
        var latch = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> race(latch, OperationType.WHITELIST_ADD, "whitelist-0001"));
            var b = executor.submit(() -> race(latch, OperationType.RESTART, "restart-0001"));
            latch.countDown(); assertThat((a.get() ? 1 : 0) + (b.get() ? 1 : 0)).isEqualTo(1);
        }
    }
    private boolean race(CountDownLatch latch, OperationType type, String key) throws Exception {
        latch.await();
        try { jobs.submit("main", "operator", key, type, "Test", type.playerOperation() ? "TestPlayer" : null); return true; }
        catch (ApiException error) { assertThat(error.code).isEqualTo("SERVER_BUSY"); return false; }
    }
}
