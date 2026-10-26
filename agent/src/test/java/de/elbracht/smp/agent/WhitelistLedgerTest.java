package de.elbracht.smp.agent;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class WhitelistLedgerTest {
    @Test void completedTargetSurvivesServiceRecreationAndDifferentTargetConflicts() throws Exception {
        var database = new EmbeddedDatabaseBuilder().generateUniqueName(true).setType(EmbeddedDatabaseType.H2).addScript("schema.sql").build();
        try {
            var store = new OperationStore(new JdbcTemplate(database)); var guard = mock(DockerControl.class);
            var docker = mock(ContainerProbe.class); var whitelist = mock(TestWhitelistControl.class); var minecraft = mock(MinecraftProbe.class);
            when(whitelist.available()).thenReturn(true);
            when(docker.inspect()).thenReturn(new ContainerProbe.State("running", true, false, false, false, true, true));
            var properties = StatusServiceTest.properties(25585, "docker-test-control");
            var service = new OperationService(properties, store, guard, docker, minecraft, whitelist);
            var id = UUID.randomUUID();
            assertThat(service.execute(id, OperationStore.Type.WHITELIST_ADD, "TestPlayer").state()).isEqualTo("SUCCEEDED");
            var restoredStore = new OperationStore(new JdbcTemplate(database)); restoredStore.recover();
            var restored = new OperationService(properties, restoredStore, guard, docker, minecraft, whitelist);
            assertThat(restored.execute(id, OperationStore.Type.WHITELIST_ADD, "TestPlayer").targetName()).isEqualTo("TestPlayer");
            assertThatThrownBy(() -> restored.execute(id, OperationStore.Type.WHITELIST_ADD, "OtherPlayer")).isInstanceOf(OperationRejected.class);
            verify(whitelist, times(1)).apply(OperationStore.Type.WHITELIST_ADD, "TestPlayer");
            verify(guard, never()).start(); verify(guard, never()).stop(); verifyNoInteractions(minecraft);
        } finally { database.shutdown(); }
    }
    @Test void uncertainWhitelistOutcomeBlocksSubsequentLifecycleAndWhitelistRequests() throws Exception {
        var database = new EmbeddedDatabaseBuilder().generateUniqueName(true).setType(EmbeddedDatabaseType.H2).addScript("schema.sql").build();
        try {
            var store = new OperationStore(new JdbcTemplate(database)); var guard = mock(DockerControl.class);
            var docker = mock(ContainerProbe.class); var whitelist = mock(TestWhitelistControl.class);
            when(whitelist.available()).thenReturn(true);
            when(docker.inspect()).thenReturn(new ContainerProbe.State("running", true, false, false, false, true, true));
            doThrow(new java.io.IOException("private diagnostic")).when(whitelist).apply(OperationStore.Type.WHITELIST_REMOVE, "TestPlayer");
            var service = new OperationService(StatusServiceTest.properties(25585, "docker-test-control"), store, guard, docker, mock(MinecraftProbe.class), whitelist);
            var id = UUID.randomUUID();
            assertThat(service.execute(id, OperationStore.Type.WHITELIST_REMOVE, "TestPlayer").state()).isEqualTo("UNKNOWN");
            assertThatThrownBy(() -> service.execute(UUID.randomUUID(), OperationStore.Type.START)).isInstanceOf(OperationRejected.class);
            assertThatThrownBy(() -> service.execute(UUID.randomUUID(), OperationStore.Type.WHITELIST_ADD, "OtherPlayer")).isInstanceOf(OperationRejected.class);
            assertThat(service.execute(id, OperationStore.Type.WHITELIST_REMOVE, "TestPlayer").state()).isEqualTo("UNKNOWN");
            verify(whitelist, times(1)).apply(OperationStore.Type.WHITELIST_REMOVE, "TestPlayer");
        } finally { database.shutdown(); }
    }
}
