package de.elbracht.smp;
import de.elbracht.smp.operations.*;
import de.elbracht.smp.servers.HostAgent;
import de.elbracht.smp.servers.MetricHistory;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Opt-in, dedicated loopback database only; never uses DATABASE_URL or a Minecraft host. */
@EnabledIfEnvironmentVariable(named="PORTAL_POSTGRES_TEST_ENABLED",matches="true")
@SpringBootTest(properties={"spring.datasource.url=jdbc:postgresql://127.0.0.1:55432/portal_test","spring.datasource.username=portal_test","spring.datasource.password=${PORTAL_POSTGRES_TEST_PASSWORD}","portal.local-password=isolated-db-test-only","portal.worker-enabled=false"})
class PostgresLiveTest {
 @Autowired JdbcTemplate jdbc;@Autowired JobService jobs;@MockitoBean HostAgent agent;
 @BeforeEach void setup(){jdbc.update("DELETE FROM audit_event");jdbc.update("DELETE FROM operation_job");jdbc.update("DELETE FROM metric_sample");when(agent.operationsAvailable()).thenReturn(true);when(agent.restoreAvailable()).thenReturn(true);}
 @Test void migrationsAndConcurrentIdempotencyUseRealPostgresLocks()throws Exception {
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success=TRUE",Integer.class)).isGreaterThanOrEqualTo(11);
  try(var pool=Executors.newFixedThreadPool(2)){var gate=new CountDownLatch(1);Callable<Job> submit=()->{gate.await();return jobs.submit("main","postgres-fixture","concurrent-db-proof",OperationType.START,"Isolierte PostgreSQL-Prüfung");};var first=pool.submit(submit);var second=pool.submit(submit);gate.countDown();assertThat(first.get(15,TimeUnit.SECONDS).jobId()).isEqualTo(second.get(15,TimeUnit.SECONDS).jobId());}
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM operation_job",Integer.class)).isEqualTo(1);assertThat(jobs.history("main",20,null).blockingJob()).isNotNull();
  assertThatThrownBy(()->jobs.submit("main","postgres-fixture","different-db-proof",OperationType.STOP,"Konfliktprüfung")).isInstanceOf(de.elbracht.smp.api.ApiException.class);verify(agent,never()).execute(anyString(),any(),any());
 }
 @Test void restartKeepsUncertainRestoreLockedAndPreservesPlanAndAudit(){
  var plan=java.util.UUID.randomUUID();var job=jobs.submitRestore("main","postgres-fixture","restore-db-proof","Wiederanlauf prüfen",plan,"a".repeat(64));assertThat(jobs.claim(job)).isTrue();
  new JobService(jdbc,agent).recover();var recovered=jobs.find("main",job.jobId());assertThat(recovered.state()).isEqualTo(Job.State.UNKNOWN);assertThat(recovered.restorePlanId()).isEqualTo(plan);assertThat(jobs.history("main",20,null).blockingJob().jobId()).isEqualTo(job.jobId());
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE job_id=?",Integer.class,job.jobId())).isGreaterThanOrEqualTo(2);
  assertThatThrownBy(()->jobs.submit("main","postgres-fixture","blocked-after-crash",OperationType.START,"Keine blinde Wiederholung")).isInstanceOf(de.elbracht.smp.api.ApiException.class);
 }
 @Test void measurementsRemainTypedAcrossServiceRestartWithoutSyntheticSamples()throws Exception{
  var now=java.time.Instant.now();var node=new com.fasterxml.jackson.databind.ObjectMapper().readTree("{\"CPUPerc\":\"121.5%\",\"MemUsage\":\"1GiB / 3GiB\",\"NetIO\":\"100kB / 20kB\",\"BlockIO\":\"0B / 1MB\",\"PIDs\":\"10\"}");when(agent.diagnostics("main","metrics")).thenReturn(new HostAgent.Diagnostics("main",now,node,null));
  new MetricHistory(jdbc,agent,true).collect();new MetricHistory(jdbc,agent,true).collect();
  var afterRestart=new MetricHistory(jdbc,agent,true).read("main",24);assertThat(afterRestart.samples()).hasSize(1);assertThat(afterRestart.samples().getFirst().cpuPercent()).isEqualTo(121.5);assertThat(afterRestart.samples().getFirst().memoryUsed()).isEqualTo(1073741824L);
  when(agent.diagnostics("main","metrics")).thenReturn(new HostAgent.Diagnostics("main",null,null,"SOURCE_UNAVAILABLE"));new MetricHistory(jdbc,agent,true).collect();assertThat(new MetricHistory(jdbc,agent,true).read("main",24).samples()).hasSize(1);
 }
 @Test void modPlanBindingAndReplayRemainTypedInPostgres(){
  when(agent.modsAvailable()).thenReturn(true);var plan=java.util.UUID.randomUUID();var job=jobs.submitMods("main","postgres-fixture","mod-db-proof","Gepinnten Modplan speichern",plan);assertThat(jobs.find("main",job.jobId()).modPlanId()).isEqualTo(plan);assertThat(jobs.claim(job)).isTrue();jobs.finish(job,Job.State.SUCCEEDED,null);assertThat(jobs.replayMods("main","postgres-fixture","mod-db-proof","Gepinnten Modplan speichern",plan).jobId()).isEqualTo(job.jobId());assertThatThrownBy(()->jobs.replayMods("main","postgres-fixture","mod-db-proof","Gepinnten Modplan speichern",java.util.UUID.randomUUID())).isInstanceOf(de.elbracht.smp.api.ApiException.class);
 }
}
