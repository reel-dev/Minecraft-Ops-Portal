package de.elbracht.smp;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:settings-access;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","portal.local-password=test-only","portal.worker-enabled=false"})
@AutoConfigureMockMvc
class SettingsAccessTest {
 @Autowired MockMvc mvc;@Autowired JdbcTemplate jdbc;
 @BeforeEach void setup(){jdbc.update("DELETE FROM audit_event");jdbc.update("DELETE FROM server_grant");jdbc.update("INSERT INTO server_grant VALUES ('admin','main','ADMIN'),('operator','main','OPERATOR'),('viewer','main','VIEWER')");}
 @Test void onlyAdminMayReadOrChangeGrantsAndCsrfIsRequired()throws Exception {
  for(String name:java.util.List.of("viewer","operator")){
   mvc.perform(get("/api/v1/servers/main/settings").with(user(name))).andExpect(status().isForbidden());
   mvc.perform(put("/api/v1/servers/main/settings/grants").with(user(name)).with(csrf()).contentType("application/json").content("{\"subject\":\"other\",\"role\":\"ADMIN\"}")).andExpect(status().isForbidden());
  }
  mvc.perform(put("/api/v1/servers/main/settings/grants").with(user("admin")).contentType("application/json").content("{\"subject\":\"other\",\"role\":\"VIEWER\"}")).andExpect(status().isForbidden());
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event",Integer.class)).isZero();
 }
 @Test void grantUpdateAndRevokeAreAuditedAndDoNotCreateLoginAccounts()throws Exception {
  for(String role:java.util.List.of("VIEWER","OPERATOR"))mvc.perform(put("/api/v1/servers/main/settings/grants").with(user("admin")).with(csrf()).contentType("application/json").content("{\"subject\":\"external-subject\",\"role\":\""+role+"\"}")).andExpect(status().isOk());
  assertThat(jdbc.queryForObject("SELECT portal_role FROM server_grant WHERE subject='external-subject'",String.class)).isEqualTo("OPERATOR");
  mvc.perform(delete("/api/v1/servers/main/settings/grants").param("subject","external-subject").with(user("admin")).with(csrf())).andExpect(status().isOk());
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE action IN ('ROLE_UPDATED','ROLE_REVOKED')",Integer.class)).isEqualTo(3);
 }
 @Test void selfChangeAndUnknownServerAndBlankSubjectsAreRejected()throws Exception {
  mvc.perform(delete("/api/v1/servers/main/settings/grants").param("subject","admin").with(user("admin")).with(csrf())).andExpect(status().isConflict());
  mvc.perform(put("/api/v1/servers/main/settings/grants").with(user("admin")).with(csrf()).contentType("application/json").content("{\"subject\":\"   \",\"role\":\"ADMIN\"}")).andExpect(status().isUnprocessableEntity());
  mvc.perform(get("/api/v1/servers/missing/settings").with(user("local-admin").roles("LOCAL_ADMIN"))).andExpect(status().isNotFound());
 }
 @Test void metricHistoryIsServerScopedAndHasBoundedTimeWindows()throws Exception {
  mvc.perform(get("/api/v1/servers/main/metrics/history")).andExpect(status().isUnauthorized());
  mvc.perform(get("/api/v1/servers/main/metrics/history").with(user("stranger"))).andExpect(status().isForbidden());
  mvc.perform(get("/api/v1/servers/main/metrics/history").with(user("viewer"))).andExpect(status().isOk()).andExpect(jsonPath("$.configured").value(false)).andExpect(jsonPath("$.samples").isEmpty());
  mvc.perform(get("/api/v1/servers/main/metrics/history").param("hours","25").with(user("viewer"))).andExpect(status().isUnprocessableEntity());
 }
}
