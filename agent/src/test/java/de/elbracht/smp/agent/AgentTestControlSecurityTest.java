package de.elbracht.smp.agent;

import java.nio.file.Files;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"agent.mode=docker-test-control", "spring.datasource.url=jdbc:h2:mem:agentcontrol;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
class AgentTestControlSecurityTest {
    private static final String TOKEN = "test-control-only-secret-0123456789abcdef";
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean DockerControl control;
    @MockitoBean ContainerProbe docker;
    @MockitoBean MinecraftProbe minecraft;
    @MockitoBean TestWhitelistControl whitelist;
    @DynamicPropertySource static void token(DynamicPropertyRegistry properties) throws Exception {
        var file = Files.createTempFile("agent-control-", ".token"); Files.writeString(file, TOKEN); file.toFile().deleteOnExit();
        properties.add("agent.token-file", () -> file.toString());
    }
    @BeforeEach void clean() { jdbc.update("DELETE FROM agent_operation"); }
    @Test void authorizedStopIsDurableAndRepeatedIdNeverStopsTwice() throws Exception {
        var id = UUID.randomUUID();
        when(docker.inspect()).thenReturn(new ContainerProbe.State("exited", false, false, false, false, true, false));
        String body = "{\"operationId\":\"" + id + "\",\"type\":\"STOP\"}";
        for (int attempt = 0; attempt < 2; attempt++)
            mvc.perform(post("/agent/v1/servers/main/operations").header("Authorization", "Bearer " + TOKEN)
                .contentType("application/json").content(body)).andExpect(status().isOk())
                .andExpect(jsonPath("$.operationId").value(id.toString())).andExpect(jsonPath("$.state").value("SUCCEEDED"));
        verify(control, times(1)).stop();
        mvc.perform(post("/agent/v1/servers/main/operations").header("Authorization", "Bearer " + TOKEN)
            .contentType("application/json").content(body.replace("STOP", "START"))).andExpect(status().isConflict());
    }
    @Test void unauthorizedForeignAndMalformedOperationsDoNotReachDocker() throws Exception {
        String body = "{\"operationId\":\"" + UUID.randomUUID() + "\",\"type\":\"STOP\"}";
        mvc.perform(post("/agent/v1/servers/main/operations").contentType("application/json").content(body)).andExpect(status().isUnauthorized());
        mvc.perform(post("/agent/v1/servers/other/operations").header("Authorization", "Bearer " + TOKEN)
            .contentType("application/json").content(body)).andExpect(status().isNotFound());
        mvc.perform(post("/agent/v1/servers/main/operations").header("Authorization", "Bearer " + TOKEN)
            .contentType("application/json").content(body.replace("STOP", "private-payload"))).andExpect(status().isUnprocessableEntity())
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private-payload"))));
        verifyNoInteractions(control, docker, minecraft);
    }
    @Test void whitelistIsDisabledUnlessTheAgentCapabilityWasEnabled() throws Exception {
        String body = "{\"operationId\":\"" + UUID.randomUUID() + "\",\"type\":\"WHITELIST_ADD\",\"targetName\":\"TestPlayer\"}";
        mvc.perform(post("/agent/v1/servers/main/operations").header("Authorization", "Bearer " + TOKEN).contentType("application/json").content(body))
            .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("TEST_WHITELIST_DISABLED"));
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM agent_operation", Integer.class)).isZero();
        verifyNoInteractions(control, docker, minecraft);
    }
    @Test void whitelistHttpRequestKeepsTargetInPersistentReplayAndRejectsAnotherName() throws Exception {
        when(whitelist.available()).thenReturn(true);
        when(docker.inspect()).thenReturn(new ContainerProbe.State("running", true, false, false, false, true, true));
        var id = UUID.randomUUID();
        String body = "{\"operationId\":\"" + id + "\",\"type\":\"WHITELIST_ADD\",\"targetName\":\"TestPlayer\"}";
        for (int attempt = 0; attempt < 2; attempt++)
            mvc.perform(post("/agent/v1/servers/main/operations").header("Authorization", "Bearer " + TOKEN).contentType("application/json").content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.state").value("SUCCEEDED")).andExpect(jsonPath("$.targetName").value("TestPlayer"));
        mvc.perform(post("/agent/v1/servers/main/operations").header("Authorization", "Bearer " + TOKEN).contentType("application/json").content(body.replace("TestPlayer", "OtherPlayer"))).andExpect(status().isConflict());
        verify(whitelist, times(1)).apply(OperationStore.Type.WHITELIST_ADD, "TestPlayer");
        verify(control, never()).start(); verify(control, never()).stop(); verifyNoInteractions(minecraft);
    }
}
