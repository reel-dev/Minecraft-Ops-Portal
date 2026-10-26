package de.elbracht.smp;
import de.elbracht.smp.operations.*;
import de.elbracht.smp.servers.HostAgent;
import java.time.Instant;
import java.util.*;
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
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:restore-jobs;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","portal.local-password=test-only","portal.worker-enabled=false"})
@AutoConfigureMockMvc
class RestoreJobsTest {
 @Autowired MockMvc mvc;@Autowired JdbcTemplate jdbc;@Autowired JobService jobs;@MockitoBean HostAgent agent;
 @BeforeEach void setup(){jdbc.update("DELETE FROM audit_event");jdbc.update("DELETE FROM operation_job");jdbc.update("DELETE FROM server_grant");for(String role:List.of("VIEWER","OPERATOR","ADMIN"))jdbc.update("INSERT INTO server_grant VALUES (?,'main',?)",role.toLowerCase(),role);when(agent.restoreAvailable()).thenReturn(true);}
 @Test void retentionPreviewIsAdminOnlyAndCannotExecuteAnOperation()throws Exception{
  mvc.perform(get("/api/v1/servers/main/backups/retention")).andExpect(status().isUnauthorized());
  for(String role:List.of("viewer","operator","stranger"))mvc.perform(get("/api/v1/servers/main/backups/retention").with(user(role))).andExpect(status().isForbidden());
  when(agent.diagnostics("main","retention")).thenReturn(new HostAgent.Diagnostics("main",Instant.now(),new com.fasterxml.jackson.databind.ObjectMapper().readTree("{\"candidates\":[],\"retained\":1,\"unmanaged\":0}"),null));
  mvc.perform(get("/api/v1/servers/main/backups/retention").with(user("admin"))).andExpect(status().isOk()).andExpect(jsonPath("$.data.candidates").isEmpty());
  verify(agent).diagnostics("main","retention");verify(agent,never()).backup(anyString(),any(),any(),any());assertThat(jobs.history("main",10,null).items()).isEmpty();
 }
 @Test void everyRestorePlanMutationRequiresAdminAndCsrf()throws Exception{
  UUID plan=UUID.randomUUID();for(String role:List.of("viewer","operator")){
   mvc.perform(post("/api/v1/servers/main/restore-plans").with(user(role)).with(csrf()).contentType("application/json").content("{\"snapshotId\":\""+"a".repeat(64)+"\",\"startAfterRestore\":true}")).andExpect(status().isForbidden());
   mvc.perform(post("/api/v1/servers/main/restore-plans/"+plan+"/execute").with(user(role)).with(csrf()).header("Idempotency-Key","restore-plan-test").contentType("application/json").content("{\"reason\":\"test\"}")).andExpect(status().isForbidden());
   mvc.perform(delete("/api/v1/servers/main/restore-plans/"+plan).with(user(role)).with(csrf())).andExpect(status().isForbidden());
  }
  mvc.perform(post("/api/v1/servers/main/restore-plans/"+plan+"/execute").with(user("admin")).header("Idempotency-Key","restore-plan-test").contentType("application/json").content("{\"reason\":\"test\"}")).andExpect(status().isForbidden());verifyNoInteractions(agent);
 }
 @Test void onlyFullSnapshotWithBootProofCanProduceAPlan()throws Exception{
  var mapper=new com.fasterxml.jackson.databind.ObjectMapper();when(agent.diagnostics("main","backups")).thenReturn(new HostAgent.Diagnostics("main",Instant.now(),mapper.readTree("{\"snapshots\":[]}"),null));
  mvc.perform(post("/api/v1/servers/main/restore-plans").with(user("admin")).with(csrf()).contentType("application/json").content("{\"snapshotId\":\""+"a".repeat(64)+"\",\"startAfterRestore\":true}")).andExpect(status().isUnprocessableEntity());verify(agent,never()).prepareRestore(anyString(),anyString(),anyBoolean());
 }
 @Test void knownRollbackIsFailedWithoutBlockingAndReplayNeverExecutesAgain()throws Exception{
  var worker=new JobWorker(jobs,agent);worker.recover();UUID plan=UUID.randomUUID();String snapshot="a".repeat(64);
  var dto=new HostAgent.RestorePlan(plan,snapshot,Instant.now().toString(),Instant.now().plusSeconds(900).toString(),"26.3","0.19.5",List.of("Welt"),true,"PLANNED");when(agent.restorePlan("main",plan)).thenReturn(dto);
  var response=mvc.perform(post("/api/v1/servers/main/restore-plans/"+plan+"/execute").with(user("admin")).with(csrf()).header("Idempotency-Key","restore-plan-test").contentType("application/json").content("{\"reason\":\"test\"}")).andExpect(status().isAccepted()).andReturn();
  UUID id=UUID.fromString(new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.getResponse().getContentAsString()).path("jobId").asText());
  when(agent.restore("main",id,plan)).thenThrow(new HostAgent.KnownFailure("RESTORE_FAILED_WITH_ORIGINAL_STATE","Vorheriger Datenbestand und Readiness geprüft."));worker.run();
  assertThat(jobs.find("main",id).state()).isEqualTo(Job.State.FAILED);assertThat(jobs.find("main",id).restorePlanId()).isEqualTo(plan);assertThat(jobs.history("main",20,null).blockingJob()).isNull();
  mvc.perform(post("/api/v1/servers/main/restore-plans/"+plan+"/execute").with(user("admin")).with(csrf()).header("Idempotency-Key","restore-plan-test").contentType("application/json").content("{\"reason\":\"test\"}")).andExpect(status().isAccepted()).andExpect(jsonPath("$.jobId").value(id.toString())).andExpect(jsonPath("$.state").value("FAILED"));
  worker.run();verify(agent,times(1)).restore("main",id,plan);verify(agent,times(1)).restorePlan("main",plan);
 }
 @Test void modPlanExecutionIsAdminOnlyAndKnownRollbackIsIdempotent()throws Exception{
  UUID plan=UUID.randomUUID();for(String role:List.of("viewer","operator"))mvc.perform(post("/api/v1/servers/main/mods/plans/"+plan+"/execute").with(user(role)).with(csrf()).header("Idempotency-Key","mod-plan-test").contentType("application/json").content("{\"reason\":\"test\"}")).andExpect(status().isForbidden());
  mvc.perform(post("/api/v1/servers/main/mods/plans").with(user("admin")).contentType("application/json").content("{\"action\":\"INSTALL\",\"projectId\":1,\"fileId\":2}")).andExpect(status().isForbidden());
  var mapper=new com.fasterxml.jackson.databind.ObjectMapper();var dto=new de.elbracht.smp.servers.ModManagementService.Plan(plan,Instant.now().toString(),Instant.now().plusSeconds(900).toString(),"26.3","0.19.5","25.0.4.1",List.of(),List.of("Explicit test fixture"),"PLANNED");when(agent.modsAvailable()).thenReturn(true);when(agent.modRpc(eq("main"),eq("/mods/plans/"+plan),eq("GET"),isNull(),any())).thenReturn(mapper.valueToTree(dto));var worker=new JobWorker(jobs,agent);worker.recover();
  var response=mvc.perform(post("/api/v1/servers/main/mods/plans/"+plan+"/execute").with(user("admin")).with(csrf()).header("Idempotency-Key","mod-plan-test").contentType("application/json").content("{\"reason\":\"test\"}")).andExpect(status().isAccepted()).andReturn();UUID id=UUID.fromString(mapper.readTree(response.getResponse().getContentAsString()).path("jobId").asText());when(agent.applyMods("main",id,plan)).thenThrow(new HostAgent.KnownFailure("MOD_CHANGE_FAILED","Originalstand geprüft."));worker.run();assertThat(jobs.find("main",id).modPlanId()).isEqualTo(plan);assertThat(jobs.find("main",id).state()).isEqualTo(Job.State.FAILED);assertThat(jobs.history("main",20,null).blockingJob()).isNull();
  mvc.perform(post("/api/v1/servers/main/mods/plans/"+plan+"/execute").with(user("admin")).with(csrf()).header("Idempotency-Key","mod-plan-test").contentType("application/json").content("{\"reason\":\"test\"}")).andExpect(status().isAccepted()).andExpect(jsonPath("$.jobId").value(id.toString()));worker.run();verify(agent,times(1)).applyMods("main",id,plan);
 }
 @Test void fractionalMeasurementsPersistInH2AndFailuresRemainObservable()throws Exception{
  jdbc.update("DELETE FROM metric_sample");var now=Instant.now();var data=new com.fasterxml.jackson.databind.ObjectMapper().readTree("{\"CPUPerc\":\"0.92%\",\"MemUsage\":\"1.271GiB / 3GiB\",\"NetIO\":\"63.6kB / 10.9kB\",\"BlockIO\":\"73.6MB / 3.86MB\",\"PIDs\":\"39\"}");when(agent.diagnostics("main","metrics")).thenReturn(new HostAgent.Diagnostics("main",now,data,null));var history=new de.elbracht.smp.servers.MetricHistory(jdbc,agent,true);history.collect();assertThat(history.read("main",24).samples()).hasSize(1);assertThat(history.read("main",24).collectionStatus().code()).isEqualTo("STORED");when(agent.diagnostics("main","metrics")).thenReturn(new HostAgent.Diagnostics("main",null,null,"SOURCE_UNAVAILABLE"));history.collect();assertThat(history.read("main",24).samples()).hasSize(1);assertThat(history.read("main",24).collectionStatus().code()).isEqualTo("SOURCE_UNAVAILABLE");
 }
 @Test void modImportRequiresAdminCsrfAndBoundedExplicitAssignments()throws Exception{
  String valid="{\"assignments\":[{\"name\":\"manual.jar\",\"projectId\":1,\"fileId\":2}]}";for(String role:List.of("viewer","operator"))mvc.perform(post("/api/v1/servers/main/mods/import-plans").with(user(role)).with(csrf()).contentType("application/json").content(valid)).andExpect(status().isForbidden());mvc.perform(post("/api/v1/servers/main/mods/import-plans").with(user("admin")).contentType("application/json").content(valid)).andExpect(status().isForbidden());
  for(String invalid:List.of("{\"assignments\":[]}","{\"assignments\":[null]}","{\"assignments\":[{\"name\":\"../manual.jar\",\"projectId\":1,\"fileId\":2}]}"))mvc.perform(post("/api/v1/servers/main/mods/import-plans").with(user("admin")).with(csrf()).contentType("application/json").content(invalid)).andExpect(status().isUnprocessableEntity());verifyNoInteractions(agent);
 }
}
