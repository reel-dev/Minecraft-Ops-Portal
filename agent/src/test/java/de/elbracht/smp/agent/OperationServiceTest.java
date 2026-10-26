package de.elbracht.smp.agent;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class OperationServiceTest {
    @Test void readOnlyModeCannotExecuteOperations() {
        var control = mock(DockerControl.class); var store = mock(OperationStore.class);
        var service = new OperationService(StatusServiceTest.properties(25585, "docker-read-only"), store, control, mock(ContainerProbe.class), mock(MinecraftProbe.class), mock(TestWhitelistControl.class));
        assertThatThrownBy(() -> service.execute(UUID.randomUUID(), OperationStore.Type.RESTART)).isInstanceOf(OperationRejected.class);
        verifyNoInteractions(store, control);
    }
    @Test void successfulRestartRequiresVerifiedStopThenMinecraftReadiness() throws Exception {
        var control = mock(DockerControl.class); var store = mock(OperationStore.class);
        var docker = mock(ContainerProbe.class); var minecraft = mock(MinecraftProbe.class); var id = UUID.randomUUID();
        when(docker.inspect()).thenReturn(new ContainerProbe.State("exited", false, false, false, false, true, false),
            new ContainerProbe.State("running", true, false, false, false, true, true));
        when(minecraft.query()).thenReturn(new MinecraftProbe.Result("test-version", 0));
        when(store.finish(id, OperationStore.Type.RESTART, "SUCCEEDED")).thenReturn(new OperationStore.Result(id, OperationStore.Type.RESTART, "SUCCEEDED"));
        var service = new OperationService(StatusServiceTest.properties(25585, "docker-test-control"), store, control, docker, minecraft, mock(TestWhitelistControl.class));
        assertThat(service.execute(id, OperationStore.Type.RESTART).state()).isEqualTo("SUCCEEDED");
        var ordered = inOrder(control, docker, minecraft);
        ordered.verify(control).verifyTarget(); ordered.verify(control).stop(); ordered.verify(docker).inspect();
        ordered.verify(control).start(); ordered.verify(docker).inspect(); ordered.verify(minecraft).query();
    }
    @Test void duplicateOperationIdReturnsStoredResultWithoutTouchingDocker() {
        var store = mock(OperationStore.class); var control = mock(DockerControl.class); var id = UUID.randomUUID();
        when(store.find(id)).thenReturn(new OperationStore.Result(id, OperationStore.Type.STOP, "SUCCEEDED"));
        var service = new OperationService(StatusServiceTest.properties(25585, "docker-test-control"), store, control, mock(ContainerProbe.class), mock(MinecraftProbe.class), mock(TestWhitelistControl.class));
        assertThat(service.execute(id, OperationStore.Type.STOP).state()).isEqualTo("SUCCEEDED"); verifyNoInteractions(control);
        assertThatThrownBy(() -> service.execute(id, OperationStore.Type.START)).isInstanceOf(OperationRejected.class);
    }
    @Test void guardFailureKeepsOutcomeUnknownAndExecutesNoCommand() throws Exception {
        var store = mock(OperationStore.class); var control = mock(DockerControl.class); var id = UUID.randomUUID();
        doThrow(new java.io.IOException("sensitive diagnostic")).when(control).verifyTarget();
        when(store.finish(id, OperationStore.Type.START, "UNKNOWN")).thenReturn(new OperationStore.Result(id, OperationStore.Type.START, "UNKNOWN"));
        var service = new OperationService(StatusServiceTest.properties(25585, "docker-test-control"), store, control, mock(ContainerProbe.class), mock(MinecraftProbe.class), mock(TestWhitelistControl.class));
        assertThat(service.execute(id, OperationStore.Type.START).state()).isEqualTo("UNKNOWN");
        verify(control, never()).start(); verify(control, never()).stop();
    }
}
