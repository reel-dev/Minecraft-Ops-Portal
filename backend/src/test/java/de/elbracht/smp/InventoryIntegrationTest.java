package de.elbracht.smp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:inventorytest;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "portal.local-password=test-only-password", "portal.worker-enabled=false"
})
@AutoConfigureMockMvc
class InventoryIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    private static final String BODY = """
        {"value":"test-recorded-value","source":"private-source-ref","observedAt":"2020-01-01T12:00:00Z"}
        """;
    @BeforeEach void resetData() {
        jdbc.update("DELETE FROM audit_event"); jdbc.update("DELETE FROM inventory_evidence"); jdbc.update("DELETE FROM server_grant");
        jdbc.update("UPDATE managed_server SET inventory_revision = 0, verified_at = NULL");
        jdbc.update("INSERT INTO server_grant VALUES ('admin','main','ADMIN')");
        jdbc.update("INSERT INTO server_grant VALUES ('operator','main','OPERATOR')");
        jdbc.update("INSERT INTO server_grant VALUES ('viewer','main','VIEWER')");
    }

    @Test void accessIsAdminOnlyAndMissingEvidenceIsNotPrefilled() throws Exception {
        mvc.perform(get("/api/v1/servers/main/inventory")).andExpect(status().isUnauthorized());
        for (String actor : new String[] { "viewer", "operator" }) {
            mvc.perform(get("/api/v1/servers/main/inventory").with(user(actor))).andExpect(status().isForbidden());
            mvc.perform(put("/api/v1/servers/main/inventory/MINECRAFT_VERSION").with(user(actor)).with(csrf())
                .header("If-Match", "\"0\"").contentType("application/json").content(BODY)).andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/v1/servers/main/inventory").with(user("admin")))
            .andExpect(status().isOk()).andExpect(header().string("ETag", "\"0\""))
            .andExpect(jsonPath("$.documentedCount").value(0)).andExpect(jsonPath("$.requiredCount").value(15))
            .andExpect(jsonPath("$.fields[3].value").isEmpty());
    }

    @Test void recordingRequiresCsrfAndExplicitRevision() throws Exception {
        mvc.perform(put("/api/v1/servers/main/inventory/MINECRAFT_VERSION").with(user("admin"))
            .header("If-Match", "\"0\"").contentType("application/json").content(BODY)).andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/servers/main/inventory/MINECRAFT_VERSION").with(user("admin")).with(csrf())
            .contentType("application/json").content(BODY)).andExpect(status().isPreconditionRequired());
        for (String revision : new String[] { "0", "*", "\"invalid\"", "\"0\",\"1\"" })
            mvc.perform(put("/api/v1/servers/main/inventory/MINECRAFT_VERSION").with(user("admin")).with(csrf())
                .header("If-Match", revision).contentType("application/json").content(BODY)).andExpect(status().isUnprocessableEntity());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_evidence", Integer.class)).isZero();
    }

    @Test void savesEvidenceAndMetadataWithoutChangingUserClaimOrMarkingServerVerified() throws Exception {
        mvc.perform(put("/api/v1/servers/main/inventory/MINECRAFT_VERSION").with(user("admin")).with(csrf())
            .header("If-Match", "\"0\"").contentType("application/json").content(BODY))
            .andExpect(status().isOk()).andExpect(header().string("ETag", "\"1\""))
            .andExpect(jsonPath("$.documentedCount").value(1)).andExpect(jsonPath("$.revision").value(1))
            .andExpect(jsonPath("$.fields[3].value").value("test-recorded-value"))
            .andExpect(jsonPath("$.fields[3].recordedBy").value("admin"))
            .andExpect(jsonPath("$.fields[3].recordedAt").isNotEmpty());
        mvc.perform(get("/api/v1/servers/main/inventory").with(user("admin")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.fields[3].source").value("private-source-ref"));
        assertThat(jdbc.queryForObject("SELECT reported_minecraft_version FROM managed_server WHERE id = 'main'", String.class)).isEqualTo("26.3");
        assertThat(jdbc.queryForObject("SELECT verified_at FROM managed_server WHERE id = 'main'", java.sql.Timestamp.class)).isNull();
        mvc.perform(get("/api/v1/servers/main/status").with(user("admin")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.lifecycle").value("UNKNOWN"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM operation_job", Integer.class)).isZero();
    }

    @Test void staleRevisionCannotOverwriteEvidenceOrAddAuditEvent() throws Exception {
        mvc.perform(put("/api/v1/servers/main/inventory/MINECRAFT_VERSION").with(user("admin")).with(csrf())
            .header("If-Match", "\"0\"").contentType("application/json").content(BODY)).andExpect(status().isOk());
        mvc.perform(put("/api/v1/servers/main/inventory/MINECRAFT_VERSION").with(user("admin")).with(csrf())
            .header("If-Match", "\"0\"").contentType("application/json").content(BODY.replace("test-recorded-value", "overwrite")))
            .andExpect(status().isPreconditionFailed());
        assertThat(jdbc.queryForObject("SELECT evidence_value FROM inventory_evidence", String.class)).isEqualTo("test-recorded-value");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event", Integer.class)).isEqualTo(1);
        mvc.perform(put("/api/v1/servers/main/inventory/MINECRAFT_VERSION").with(user("admin")).with(csrf())
            .header("If-Match", "\"1\"").contentType("application/json").content(BODY.replace("test-recorded-value", "updated-value")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.revision").value(2)).andExpect(jsonPath("$.documentedCount").value(1));
    }

    @Test void invalidEvidenceAndUnknownFieldsAreRejected() throws Exception {
        for (String body : new String[] { "{}", BODY.replace("test-recorded-value", " "), BODY.replace("private-source-ref", " "),
            BODY.replace("2020-01-01T12:00:00Z", "2999-01-01T00:00:00Z"), BODY.replace("test-recorded-value", "x".repeat(2001)) })
            mvc.perform(put("/api/v1/servers/main/inventory/MINECRAFT_VERSION").with(user("admin")).with(csrf())
                .header("If-Match", "\"0\"").contentType("application/json").content(body)).andExpect(status().isUnprocessableEntity());
        mvc.perform(put("/api/v1/servers/main/inventory/SHELL").with(user("admin")).with(csrf())
            .header("If-Match", "\"0\"").contentType("application/json").content(BODY)).andExpect(status().isUnprocessableEntity());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event", Integer.class)).isZero();
    }

    @Test void inventoryAuditContainsOnlyChangeMetadataAndNoEvidenceText() throws Exception {
        mvc.perform(put("/api/v1/servers/main/inventory/MINECRAFT_VERSION").with(user("admin")).with(csrf())
            .header("If-Match", "\"0\"").contentType("application/json").content(BODY)).andExpect(status().isOk());
        var result = mvc.perform(get("/api/v1/servers/main/audit").with(user("admin")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].action").value("INVENTORY_UPDATED"))
            .andExpect(jsonPath("$.items[0].outcome").value("RECORDED"))
            .andExpect(jsonPath("$.items[0].subjectKey").value("MINECRAFT_VERSION"))
            .andExpect(jsonPath("$.items[0].revision").value(1)).andExpect(jsonPath("$.items[0].jobId").isEmpty()).andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("test-recorded-value", "private-source-ref");
    }

    @Test void serverGrantDoesNotAllowReadingOrWritingForeignInventory() throws Exception {
        mvc.perform(get("/api/v1/servers/other/inventory").with(user("admin"))).andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/servers/other/inventory/MINECRAFT_VERSION").with(user("admin")).with(csrf())
            .header("If-Match", "\"0\"").contentType("application/json").content(BODY)).andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_evidence", Integer.class)).isZero();
    }

    @Test void aCompleteManualInventoryNeverBecomesAutomaticTechnicalApproval() throws Exception {
        int revision = 0;
        for (var field : de.elbracht.smp.servers.InventoryService.Field.values()) {
            mvc.perform(put("/api/v1/servers/main/inventory/" + field.name()).with(user("admin")).with(csrf())
                .header("If-Match", "\"" + revision + "\"").contentType("application/json").content(BODY)).andExpect(status().isOk());
            revision++;
        }
        mvc.perform(get("/api/v1/servers/main/inventory").with(user("admin")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.documentedCount").value(15));
        mvc.perform(get("/api/v1/servers").with(user("admin")))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].verifiedAt").isEmpty())
            .andExpect(jsonPath("$[0].operationsAvailable").value(false));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event", Integer.class)).isEqualTo(15);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM operation_job", Integer.class)).isZero();
    }
}
