package de.elbracht.smp;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import de.elbracht.smp.api.ApiException;
import de.elbracht.smp.operations.*;
import de.elbracht.smp.servers.HostAgent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:portaltest;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "portal.local-password=test-only-password",
    "portal.worker-enabled=false"
})
@AutoConfigureMockMvc
class PortalIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JobService jobs;
    @Autowired de.elbracht.smp.audit.AuditService audit;
    @MockitoBean HostAgent agent;

    @BeforeEach void resetData() {
        jdbc.update("DELETE FROM audit_event");
        jdbc.update("DELETE FROM operation_job");
        jdbc.update("DELETE FROM server_grant");
        jdbc.update("INSERT INTO server_grant VALUES ('viewer','main','VIEWER')");
        jdbc.update("INSERT INTO server_grant VALUES ('operator','main','OPERATOR')");
        jdbc.update("INSERT INTO server_grant VALUES ('admin','main','ADMIN')");
        when(agent.operationsAvailable()).thenReturn(true);
    }

    @Test void apiRequiresSessionAndCsrf() throws Exception {
        mvc.perform(get("/api/v1/servers")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/servers/main/operations").with(user("operator"))
            .header("Idempotency-Key", "request-0001").contentType("application/json")
            .content("{\"type\":\"RESTART\",\"reason\":\"Wartung\"}"))
            .andExpect(status().isForbidden());
    }

    @Test void loginCreatesSessionAndMaskedCsrfWorks() throws Exception {
        var login = mvc.perform(post("/login").with(csrf()).param("username", "local-admin")
            .param("password", "test-only-password")).andExpect(status().is3xxRedirection()).andReturn();
        var session = (org.springframework.mock.web.MockHttpSession) login.getRequest().getSession(false);
        mvc.perform(get("/api/v1/me").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.name").value("local-admin"));
        var csrf = mvc.perform(get("/api/v1/csrf").session(session)).andExpect(status().isOk()).andReturn();
        var json = new com.fasterxml.jackson.databind.ObjectMapper().readTree(csrf.getResponse().getContentAsString());
        mvc.perform(post("/api/v1/logout").session(session)
            .header(json.get("headerName").asText(), json.get("token").asText()))
            .andExpect(status().isNoContent());
    }

    @Test void rolesAndServerScopeAreCheckedOnDirectCalls() throws Exception {
        mvc.perform(get("/api/v1/servers").with(user("stranger")))
            .andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(get("/api/v1/servers/other/status").with(user("operator"))).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/servers/main/operations").with(user("viewer")).with(csrf())
            .header("Idempotency-Key", "request-0001").contentType("application/json")
            .content("{\"type\":\"RESTART\",\"reason\":\"Wartung\"}"))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/servers/main/jobs/" + UUID.randomUUID()).with(user("viewer")))
            .andExpect(status().isForbidden());
    }

    @Test void operatorReceivesAcceptedAndLocation() throws Exception {
        mvc.perform(post("/api/v1/servers/main/operations").with(user("operator")).with(csrf())
            .header("Idempotency-Key", "request-0001").contentType("application/json")
            .content("{\"type\":\"RESTART\",\"reason\":\"Wartung\"}"))
            .andExpect(status().isAccepted()).andExpect(header().exists("Location"))
            .andExpect(jsonPath("$.state").value("QUEUED"));
    }

    @Test void invalidOperationIsRejectedWithoutLeakingInput() throws Exception {
        mvc.perform(post("/api/v1/servers/main/operations").with(user("operator")).with(csrf())
            .header("Idempotency-Key", "request-0001").contentType("application/json")
            .content("{\"type\":\"SHELL\",\"reason\":\"sensitive-input\"}"))
            .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("sensitive-input"))));
    }

    @Test void idempotencyAndDurableLockPreventDuplicates() {
        Job first = jobs.submit("main", "operator", "request-0001", OperationType.RESTART, "Wartung");
        Job retry = jobs.submit("main", "operator", "request-0001", OperationType.RESTART, "Wartung");
        assertThat(retry.jobId()).isEqualTo(first.jobId());
        assertThatThrownBy(() -> jobs.submit("main", "operator", "request-0001", OperationType.STOP, "Wartung"))
            .isInstanceOf(ApiException.class).hasFieldOrPropertyWithValue("code", "IDEMPOTENCY_CONFLICT");
        assertThatThrownBy(() -> jobs.submit("main", "operator", "request-0002", OperationType.RESTART, "Wartung"))
            .isInstanceOf(ApiException.class).hasFieldOrPropertyWithValue("code", "SERVER_BUSY");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM operation_job", Integer.class)).isEqualTo(1);
        assertThat(new JobService(jdbc, agent).find("main", first.jobId()).state()).isEqualTo(Job.State.QUEUED);
    }

    @Test void concurrentRequestsSerializePerServer() throws Exception {
        var gate = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var task = (java.util.concurrent.Callable<String>) () -> {
                gate.await();
                try { jobs.submit("main", "operator", UUID.randomUUID().toString(), OperationType.RESTART, "Wartung"); return "QUEUED"; }
                catch (ApiException error) { return error.code; }
            };
            var one = pool.submit(task); var two = pool.submit(task); gate.countDown();
            assertThat(java.util.List.of(one.get(), two.get())).containsExactlyInAnyOrder("QUEUED", "SERVER_BUSY");
        }
    }

    @Test void restartRecoveryBlocksUncertainResultsAndAuditsThem() {
        Job first = jobs.submit("main", "operator", "request-0001", OperationType.RESTART, "Wartung");
        jobs.claim(first);
        jobs.recover();
        assertThat(jobs.find("main", first.jobId()).state()).isEqualTo(Job.State.UNKNOWN);
        assertThatThrownBy(() -> jobs.submit("main", "operator", "request-0002", OperationType.START, "Wartung"))
            .hasFieldOrPropertyWithValue("code", "SERVER_BUSY");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE outcome = 'UNKNOWN'", Integer.class)).isEqualTo(1);
    }

    @Test void workerOnlyConfirmsSuccessAfterAgentReturnsAndKeepsUncertaintyLocked() {
        var worker = new JobWorker(jobs, agent);
        worker.recover();
        Job first = jobs.submit("main", "operator", "request-0001", OperationType.RESTART, "Wartung");
        worker.run();
        verify(agent).execute("main", OperationType.RESTART, first.jobId());
        assertThat(jobs.find("main", first.jobId()).state()).isEqualTo(Job.State.SUCCEEDED);
        Job second = jobs.submit("main", "operator", "request-0002", OperationType.STOP, "Wartung");
        doThrow(new IllegalStateException("secret-must-not-leak")).when(agent).execute("main", OperationType.STOP, second.jobId());
        worker.run();
        assertThat(jobs.find("main", second.jobId()).state()).isEqualTo(Job.State.UNKNOWN);
        assertThat(jobs.find("main", second.jobId()).errorCode()).isEqualTo("AGENT_RESULT_UNKNOWN");
    }

    @Test void disconnectedAdapterNeverExecutesOrReportsInventedMeasurements() throws Exception {
        var disconnected = new de.elbracht.smp.servers.DisconnectedHostAgent();
        when(agent.operationsAvailable()).thenReturn(false);
        when(agent.observe("main")).thenReturn(disconnected.observe("main"));
        mvc.perform(get("/api/v1/servers/main/status").with(user("viewer")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.lifecycle").value("UNKNOWN"))
            .andExpect(jsonPath("$.playersOnline").isEmpty()).andExpect(jsonPath("$.observedAt").isEmpty())
            .andExpect(jsonPath("$.minecraftVersion").isEmpty());
        mvc.perform(post("/api/v1/servers/main/operations").with(user("operator")).with(csrf())
            .header("Idempotency-Key", "request-0001").contentType("application/json")
            .content("{\"type\":\"RESTART\",\"reason\":\"Wartung\"}"))
            .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("HOST_AGENT_NOT_CONFIGURED"));
        verify(agent, never()).execute(anyString(), any());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM operation_job", Integer.class)).isZero();
    }

    @Test void jobHistoryRequiresOperatorOnTheRequestedServer() throws Exception {
        mvc.perform(get("/api/v1/servers/main/jobs")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/servers/main/jobs").with(user("viewer"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/servers/other/jobs").with(user("operator"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/servers/main/jobs").with(user("operator")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty())
            .andExpect(jsonPath("$.nextCursor").isEmpty()).andExpect(jsonPath("$.blockingJob").isEmpty());
    }

    @Test void historyValidatesLimitsAndCursor() throws Exception {
        for (String limit : new String[] { "0", "51", "invalid" })
            mvc.perform(get("/api/v1/servers/main/jobs").param("limit", limit).with(user("operator")))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/api/v1/servers/main/jobs").param("before", "not-a-uuid").with(user("operator")))
            .andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/api/v1/servers/main/jobs").param("before", UUID.randomUUID().toString()).with(user("operator")))
            .andExpect(status().isNotFound());
    }

    @Test void historyRestoresUnknownLockAfterRestartWithoutExposingIdempotencyKey() throws Exception {
        Job job = jobs.submit("main", "operator", "private-request-key", OperationType.RESTART, "Wartung");
        jobs.recover();
        mvc.perform(get("/api/v1/servers/main/jobs").with(user("operator")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.blockingJob.jobId").value(job.jobId().toString()))
            .andExpect(jsonPath("$.blockingJob.state").value("UNKNOWN"))
            .andExpect(jsonPath("$.items[0].errorCode").value("RECONCILIATION_REQUIRED"))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private-request-key"))));
        verify(agent, never()).execute(anyString(), any());
    }

    @Test void cursorPaginationIsStableWithEqualTimestampsAndNewArrivals() {
        var timestamp = java.time.OffsetDateTime.parse("2026-10-25T10:00:00Z");
        for (int i = 1; i <= 5; i++) {
            UUID id = UUID.fromString("00000000-0000-0000-0000-%012d".formatted(i));
            jdbc.update("INSERT INTO operation_job (id,server_id,actor,idempotency_key,operation_type,reason,state,created_at,updated_at) VALUES (?,'main','operator',?,'START','Test','SUCCEEDED',?,?)",
                id, "history-key-" + i, timestamp, timestamp);
        }
        var first = jobs.history("main", 2, null);
        assertThat(first.items()).extracting(Job::jobId).containsExactly(
            UUID.fromString("00000000-0000-0000-0000-000000000005"), UUID.fromString("00000000-0000-0000-0000-000000000004"));
        Job newest = jobs.submit("main", "operator", "new-arrival-key", OperationType.RESTART, "Test");
        jdbc.update("UPDATE operation_job SET created_at = ? WHERE id = ?", timestamp.plusSeconds(1), newest.jobId());
        var second = jobs.history("main", 2, first.nextCursor());
        var third = jobs.history("main", 2, second.nextCursor());
        assertThat(second.items()).extracting(Job::jobId).containsExactly(
            UUID.fromString("00000000-0000-0000-0000-000000000003"), UUID.fromString("00000000-0000-0000-0000-000000000002"));
        assertThat(third.items()).hasSize(1);
        assertThat(third.nextCursor()).isNull();
        assertThat(third.blockingJob().jobId()).isEqualTo(newest.jobId());
    }

    @Test void cursorAndDetailCannotReadJobsFromAnotherServer() throws Exception {
        jdbc.update("INSERT INTO managed_server (id,display_name) VALUES ('other','Other')");
        try {
            Job other = jobs.submit("other", "operator", "other-server-key", OperationType.START, "Test");
            mvc.perform(get("/api/v1/servers/main/jobs").param("before", other.jobId().toString()).with(user("operator")))
                .andExpect(status().isNotFound());
            mvc.perform(get("/api/v1/servers/main/jobs/" + other.jobId()).with(user("operator"))).andExpect(status().isNotFound());
        } finally {
            jdbc.update("DELETE FROM audit_event WHERE server_id = 'other'");
            jdbc.update("DELETE FROM operation_job WHERE server_id = 'other'");
            jdbc.update("DELETE FROM managed_server WHERE id = 'other'");
        }
    }

    @Test void auditRequiresAdminForTheSpecificServer() throws Exception {
        mvc.perform(get("/api/v1/servers/main/audit")).andExpect(status().isUnauthorized());
        for (String userName : new String[] { "viewer", "operator" })
            mvc.perform(get("/api/v1/servers/main/audit").with(user(userName))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/servers/other/audit").with(user("admin"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/servers/main/audit").with(user("admin")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
    }

    @Test void auditExposesOnlyActualTransitionsAndNoFreeTextOrRequestKey() throws Exception {
        Job job = jobs.submit("main", "operator", "private-idempotency-key", OperationType.RESTART, "private-reason");
        assertThat(jobs.claim(job)).isTrue();
        assertThat(jobs.claim(job)).isFalse();
        assertThatThrownBy(() -> jobs.finish(job, Job.State.QUEUED, null)).hasFieldOrPropertyWithValue("code", "INVALID_JOB_TRANSITION");
        jobs.finish(job, Job.State.SUCCEEDED, null);
        jobs.finish(job, Job.State.FAILED, "SHOULD_NOT_BE_AUDITED");
        assertThat(jobs.find("main", job.jobId()).state()).isEqualTo(Job.State.SUCCEEDED);
        assertThat(audit.list("main", job.jobId(), 20, null).items())
            .extracting(de.elbracht.smp.audit.AuditService.Event::outcome)
            .containsExactlyInAnyOrder("QUEUED", "RUNNING", "SUCCEEDED");
        mvc.perform(get("/api/v1/servers/main/audit").with(user("admin")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(3))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private-reason"))))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private-idempotency-key"))))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("SHOULD_NOT_BE_AUDITED"))));
        verify(agent, never()).execute(anyString(), any());
    }

    @Test void auditFiltersByJobAndRejectsMismatchedCursors() throws Exception {
        Job first = jobs.submit("main", "operator", "audit-request-one", OperationType.START, "Test");
        jobs.claim(first); jobs.finish(first, Job.State.SUCCEEDED, null);
        Job second = jobs.submit("main", "operator", "audit-request-two", OperationType.STOP, "Test");
        var firstEvents = audit.list("main", first.jobId(), 20, null);
        assertThat(firstEvents.items()).hasSize(3).allMatch(event -> event.jobId().equals(first.jobId()));
        mvc.perform(get("/api/v1/servers/main/audit").param("jobId", second.jobId().toString()).with(user("admin")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].jobId").value(second.jobId().toString()));
        mvc.perform(get("/api/v1/servers/main/audit").param("jobId", second.jobId().toString())
            .param("before", firstEvents.items().getFirst().id().toString()).with(user("admin")))
            .andExpect(status().isNotFound());
        for (String query : new String[] { "jobId", "before" }) {
            mvc.perform(get("/api/v1/servers/main/audit").param(query, "invalid").with(user("admin")))
                .andExpect(status().isUnprocessableEntity());
            mvc.perform(get("/api/v1/servers/main/audit").param(query, UUID.randomUUID().toString()).with(user("admin")))
                .andExpect(status().isNotFound());
        }
        for (String size : new String[] { "0", "51", "invalid" })
            mvc.perform(get("/api/v1/servers/main/audit").param("limit", size).with(user("admin")))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test void auditCursorPaginationHandlesTiesAndNewEvents() {
        Job job = jobs.submit("main", "operator", "audit-pagination-key", OperationType.START, "Test");
        jobs.claim(job); jobs.finish(job, Job.State.SUCCEEDED, null);
        var timestamp = java.time.OffsetDateTime.parse("2026-10-25T08:00:00Z");
        jdbc.update("UPDATE audit_event SET occurred_at = ?", timestamp);
        var all = audit.list("main", null, 20, null).items();
        var pageOne = audit.list("main", null, 2, null);
        jobs.submit("main", "operator", "audit-new-arrival", OperationType.RESTART, "Test");
        jdbc.update("UPDATE audit_event SET occurred_at = ? WHERE job_id <> ?", timestamp.plusSeconds(1), job.jobId());
        var pageTwo = audit.list("main", null, 2, pageOne.nextCursor());
        assertThat(pageOne.items()).containsExactlyElementsOf(all.subList(0, 2));
        assertThat(pageTwo.items()).containsExactlyElementsOf(all.subList(2, 3));
        assertThat(pageTwo.nextCursor()).isNull();
    }

    @Test void auditCannotUseAnotherServersJobOrCursor() throws Exception {
        jdbc.update("INSERT INTO managed_server (id,display_name) VALUES ('other','Other')");
        try {
            Job other = jobs.submit("other", "operator", "audit-other-key", OperationType.START, "Test");
            UUID cursor = audit.list("other", null, 20, null).items().getFirst().id();
            mvc.perform(get("/api/v1/servers/main/audit").param("jobId", other.jobId().toString()).with(user("admin")))
                .andExpect(status().isNotFound());
            mvc.perform(get("/api/v1/servers/main/audit").param("before", cursor.toString()).with(user("admin")))
                .andExpect(status().isNotFound());
        } finally {
            jdbc.update("DELETE FROM audit_event WHERE server_id = 'other'");
            jdbc.update("DELETE FROM operation_job WHERE server_id = 'other'");
            jdbc.update("DELETE FROM managed_server WHERE id = 'other'");
        }
    }
}
