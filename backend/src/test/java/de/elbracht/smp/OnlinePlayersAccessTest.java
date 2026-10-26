package de.elbracht.smp;

import de.elbracht.smp.servers.HostAgent;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:playersaccess;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "portal.local-password=test-only-password", "portal.worker-enabled=false"})
@AutoConfigureMockMvc
class OnlinePlayersAccessTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean HostAgent agent;
    @BeforeEach void grants() {
        jdbc.update("DELETE FROM server_grant");
        jdbc.update("INSERT INTO server_grant VALUES ('viewer','main','VIEWER')");
    }
    @Test void authenticationAndServerGrantAreRequiredBeforeAnyHostAccess() throws Exception {
        mvc.perform(get("/api/v1/servers/main/players/online")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/servers/main/players/online").with(user("stranger"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/servers/other/players/online").with(user("viewer"))).andExpect(status().isForbidden());
        verifyNoInteractions(agent);
    }
    @Test void viewerCanReadNamesWithoutReceivingOrChangingPrivileges() throws Exception {
        when(agent.onlinePlayers("main")).thenReturn(new HostAgent.OnlinePlayers("main", List.of("Player_A"), Instant.now(), null));
        mvc.perform(get("/api/v1/servers/main/players/online").with(user("viewer")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.names[0]").value("Player_A"))
            .andExpect(jsonPath("$.operator").doesNotExist());
        verify(agent).onlinePlayers("main");
        verifyNoMoreInteractions(agent);
    }
}
