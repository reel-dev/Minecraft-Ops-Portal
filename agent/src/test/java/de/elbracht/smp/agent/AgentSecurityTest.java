package de.elbracht.smp.agent;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"agent.mode=docker-read-only", "spring.datasource.url=jdbc:h2:mem:agentsecurity;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
class AgentSecurityTest {
    private static final String TOKEN = "test-only-agent-secret-0123456789abcdef";
    @Autowired MockMvc mvc;
    @MockitoBean ContainerProbe container;
    @MockitoBean MinecraftProbe minecraft;
    @DynamicPropertySource static void token(DynamicPropertyRegistry properties) throws Exception {
        Path file = Files.createTempFile("smp-agent-test-", ".token"); Files.writeString(file, TOKEN); file.toFile().deleteOnExit();
        properties.add("agent.token-file", () -> file.toString());
    }
    @Test void missingOrInvalidTokenNeverReachesProbes() throws Exception {
        mvc.perform(get("/agent/v1/servers/main/status")).andExpect(status().isUnauthorized());
        mvc.perform(get("/agent/v1/servers/main/status").header("Authorization", "Bearer invalid")).andExpect(status().isUnauthorized());
        verifyNoInteractions(container, minecraft);
    }
    @Test void fixedTargetAndMethodCannotBeOverridden() throws Exception {
        mvc.perform(get("/agent/v1/servers/other/status").header("Authorization", "Bearer " + TOKEN)).andExpect(status().isNotFound());
        mvc.perform(post("/agent/v1/servers/main/operations").header("Authorization", "Bearer " + TOKEN).content("{\"type\":\"RESTART\"}"))
            .andExpect(status().isForbidden());
        verifyNoInteractions(container, minecraft);
    }
    @Test void authenticatedReadReturnsOnlyVerifiedStatusFields() throws Exception {
        when(container.inspect()).thenReturn(new ContainerProbe.State("running", true, false, false, false, true, true));
        when(minecraft.query()).thenReturn(new MinecraftProbe.Result("test-version", 0));
        mvc.perform(get("/agent/v1/servers/main/status").header("Authorization", "Bearer " + TOKEN))
            .andExpect(status().isOk()).andExpect(jsonPath("$.lifecycle").value("RUNNING"))
            .andExpect(jsonPath("$.minecraftReady").value(true)).andExpect(jsonPath("$.loaderVersion").isEmpty())
            .andExpect(cookie().doesNotExist("JSESSIONID"));
    }
}
