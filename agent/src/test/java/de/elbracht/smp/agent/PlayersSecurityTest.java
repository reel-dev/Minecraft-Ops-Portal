package de.elbracht.smp.agent;

import java.nio.file.Files;
import java.util.List;
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

@SpringBootTest(properties = {"agent.mode=docker-read-only", "spring.datasource.url=jdbc:h2:mem:playerssecurity;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
class PlayersSecurityTest {
    private static final String TOKEN = "test-only-players-token-0123456789abcdef";
    @Autowired MockMvc mvc;
    @MockitoBean ContainerProbe docker;
    @MockitoBean RconPlayerReader reader;
    @DynamicPropertySource static void token(DynamicPropertyRegistry registry) throws Exception {
        var file = Files.createTempFile("players-agent-", ".token"); Files.writeString(file, TOKEN); file.toFile().deleteOnExit();
        registry.add("agent.token-file", file::toString);
    }
    @Test void requiresTokenAndFixedServerAndRejectsWrites() throws Exception {
        mvc.perform(get("/agent/v1/servers/main/players/online")).andExpect(status().isUnauthorized());
        mvc.perform(get("/agent/v1/servers/other/players/online").header("Authorization", "Bearer " + TOKEN)).andExpect(status().isNotFound());
        mvc.perform(post("/agent/v1/servers/main/players/online").header("Authorization", "Bearer " + TOKEN)).andExpect(status().isForbidden());
        verifyNoInteractions(docker, reader);
    }
    @Test void unavailableAndWrongTargetDoNotBecomeEmptyLists() throws Exception {
        mvc.perform(get("/agent/v1/servers/main/players/online").header("Authorization", "Bearer " + TOKEN)).andExpect(status().isServiceUnavailable());
        verifyNoInteractions(docker);
        when(reader.available()).thenReturn(true);
        when(docker.inspect()).thenReturn(new ContainerProbe.State("running", true, false, false, false, false, true));
        mvc.perform(get("/agent/v1/servers/main/players/online").header("Authorization", "Bearer " + TOKEN)).andExpect(status().isServiceUnavailable());
        verify(reader, never()).readOnlineNames();
    }
    @Test void verifiedTargetReturnsNamesButNeverInventedUuidOrPrivileges() throws Exception {
        when(reader.available()).thenReturn(true);
        when(docker.inspect()).thenReturn(new ContainerProbe.State("running", true, false, false, false, true, true));
        when(reader.readOnlineNames()).thenReturn(List.of("Player_A"));
        mvc.perform(get("/agent/v1/servers/main/players/online").header("Authorization", "Bearer " + TOKEN))
            .andExpect(status().isOk()).andExpect(jsonPath("$.names[0]").value("Player_A"))
            .andExpect(jsonPath("$.observedAt").exists()).andExpect(jsonPath("$.uuid").doesNotExist()).andExpect(jsonPath("$.operator").doesNotExist());
    }
}
