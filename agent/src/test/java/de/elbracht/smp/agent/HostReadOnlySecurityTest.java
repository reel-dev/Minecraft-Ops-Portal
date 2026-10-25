package de.elbracht.smp.agent;

import java.nio.file.Files;
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

@SpringBootTest(properties = {"spring.profiles.active=docker-01",
    "spring.datasource.url=jdbc:h2:mem:hostreadonly;DB_CLOSE_DELAY=-1",
    "agent.expected-image-id=sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"})
@AutoConfigureMockMvc
class HostReadOnlySecurityTest {
    private static final String TOKEN = "test-only-host-secret-0123456789abcdef";
    @Autowired MockMvc mvc;
    @MockitoBean DockerControl control;
    @MockitoBean ContainerProbe docker;
    @MockitoBean MinecraftProbe minecraft;
    @DynamicPropertySource static void token(DynamicPropertyRegistry properties) throws Exception {
        var file = Files.createTempFile("agent-host-", ".token"); Files.writeString(file, TOKEN); file.toFile().deleteOnExit();
        properties.add("agent.token-file", () -> file.toString());
    }
    @Test void readOnlyHostProfileRejectsMutationsEvenWithValidToken() throws Exception {
        for (String action : new String[] {"START", "STOP", "RESTART"})
            mvc.perform(post("/agent/v1/servers/main/operations").header("Authorization", "Bearer " + TOKEN)
                .contentType("application/json").content("{\"operationId\":\"22a256ca-457d-4f88-a54a-71cbaae8f386\",\"type\":\"" + action + "\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/agent/v1/servers/other/status").header("Authorization", "Bearer " + TOKEN)).andExpect(status().isNotFound());
        mvc.perform(get("/agent/v1/servers/main/status")).andExpect(status().isUnauthorized());
        verifyNoInteractions(control, docker, minecraft);
    }
    @Test void authenticatedStatusStillNeedsRealMinecraftReadiness() throws Exception {
        when(docker.inspect()).thenReturn(new ContainerProbe.State("running", true, false, false, false, true, true));
        when(minecraft.query()).thenReturn(new MinecraftProbe.Result("26.3", 0));
        mvc.perform(get("/agent/v1/servers/main/status").header("Authorization", "Bearer " + TOKEN))
            .andExpect(status().isOk()).andExpect(jsonPath("$.lifecycle").value("RUNNING"))
            .andExpect(jsonPath("$.minecraftVersion").value("26.3"))
            .andExpect(jsonPath("$.loaderVersion").isEmpty()).andExpect(cookie().doesNotExist("JSESSIONID"));
        verifyNoInteractions(control);
    }
}
