package de.elbracht.smp.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.*;
import java.time.Duration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestWhitelistControlTest {
    @TempDir Path root;
    private RconPlayerReader rcon;
    private DockerControl guard;
    private TestWhitelistControl control;
    private static final String ENTRY = "[{\"name\":\"TestPlayer\",\"uuid\":\"00000000-0000-0000-0000-000000000001\"}]";
    @BeforeEach void setup() throws Exception {
        rcon = mock(RconPlayerReader.class); guard = mock(DockerControl.class); when(rcon.available()).thenReturn(true);
        Files.writeString(root.resolve("whitelist.json"), "[]"); Files.writeString(root.resolve("ops.json"), "[]");
        control = new TestWhitelistControl(new TestWhitelistProperties(true, root, "directory"), StatusServiceTest.properties(25585, "docker-test-control"),
            new RconProperties("test-control", "127.0.0.1", 25595, root.resolve("unused"), Duration.ofSeconds(1)), rcon, guard, new ObjectMapper());
    }
    @Test void addRequiresFileConfirmationAndUsesOnlyNamedRconMutation() throws Exception {
        doAnswer(call -> { Files.writeString(root.resolve("whitelist.json"), ENTRY); return null; }).when(rcon).addToTestWhitelist("TestPlayer");
        control.apply(OperationStore.Type.WHITELIST_ADD, "TestPlayer");
        verify(rcon).addToTestWhitelist("TestPlayer"); verify(guard, times(2)).verifyRconTarget(25595);
        control.apply(OperationStore.Type.WHITELIST_ADD, "TestPlayer");
        verify(rcon, times(1)).addToTestWhitelist("TestPlayer");
        assertThat(Files.readString(root.resolve("ops.json"))).isEqualTo("[]");
    }
    @Test void removeVerifiesAbsenceAndNeverWritesPlayerFilesItself() throws Exception {
        Files.writeString(root.resolve("whitelist.json"), ENTRY);
        doAnswer(call -> { Files.writeString(root.resolve("whitelist.json"), "[]"); return null; }).when(rcon).removeFromTestWhitelist("TestPlayer");
        control.apply(OperationStore.Type.WHITELIST_REMOVE, "TestPlayer");
        verify(rcon).removeFromTestWhitelist("TestPlayer");
    }
    @Test void operatorsAreProtectedByNameAndUuidEvenAfterRename() throws Exception {
        Files.writeString(root.resolve("whitelist.json"), ENTRY);
        Files.writeString(root.resolve("ops.json"), ENTRY.replace("TestPlayer", "OldName"));
        assertThatThrownBy(() -> control.apply(OperationStore.Type.WHITELIST_REMOVE, "TestPlayer")).isInstanceOf(IOException.class);
        verify(rcon, never()).removeFromTestWhitelist(anyString());
    }
    @Test void rconReplyWithoutChangedFileIsNeverReportedAsSuccess() {
        assertThatThrownBy(() -> control.apply(OperationStore.Type.WHITELIST_ADD, "TestPlayer")).isInstanceOf(IOException.class);
    }
    @Test void malformedMissingOrDuplicatePlayerFilesBlockMutation() throws Exception {
        for (String data : new String[]{"{}", "private-invalid-text", ENTRY.replace("00000000-0000-0000-0000-000000000001", "bad"), ENTRY + "{}",
                "[" + ENTRY.substring(1, ENTRY.length() - 1) + "," + ENTRY.substring(1, ENTRY.length() - 1) + "]"}) {
            Files.writeString(root.resolve("whitelist.json"), data);
            assertThatThrownBy(() -> control.apply(OperationStore.Type.WHITELIST_ADD, "TestPlayer")).isInstanceOf(IOException.class);
        }
        Files.delete(root.resolve("whitelist.json"));
        assertThatThrownBy(() -> control.apply(OperationStore.Type.WHITELIST_ADD, "TestPlayer")).isInstanceOf(IOException.class);
        verify(rcon, never()).addToTestWhitelist(anyString());
    }
    @Test void productionAndReadOnlyRconCannotEnableTestWhitelist() {
        assertThatThrownBy(() -> new TestWhitelistControl(new TestWhitelistProperties(true, root, "directory"), StatusServiceTest.properties(25585, "docker-read-only"),
            new RconProperties("test-control", "127.0.0.1", 25595, root.resolve("unused"), Duration.ofSeconds(1)), rcon, guard, new ObjectMapper()))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new TestWhitelistControl(new TestWhitelistProperties(true, root, "directory"), StatusServiceTest.properties(25585, "docker-test-control"),
            new RconProperties("read-only", "127.0.0.1", 25595, root.resolve("unused"), Duration.ofSeconds(1)), rcon, guard, new ObjectMapper()))
            .isInstanceOf(IllegalStateException.class);
    }
    @Test void dockerSourceUsesTheGuardedContainersFilesWithoutLocalWorldPath() throws Exception {
        var content = new java.util.concurrent.atomic.AtomicReference<>("[]");
        when(guard.readTestPlayerFile(DockerControl.PlayerFile.WHITELIST)).thenAnswer(call -> content.get().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        when(guard.readTestPlayerFile(DockerControl.PlayerFile.OPERATORS)).thenReturn("[]".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        doAnswer(call -> { content.set(ENTRY); return null; }).when(rcon).addToTestWhitelist("TestPlayer");
        var dockerFiles = new TestWhitelistControl(new TestWhitelistProperties(true, null, "docker-test"), StatusServiceTest.properties(25585, "docker-test-control"),
            new RconProperties("test-control", "127.0.0.1", 25595, root.resolve("unused"), Duration.ofSeconds(1)), rcon, guard, new ObjectMapper());
        dockerFiles.apply(OperationStore.Type.WHITELIST_ADD, "TestPlayer");
        verify(guard, times(2)).readTestPlayerFile(DockerControl.PlayerFile.WHITELIST);
        verify(guard, times(2)).readTestPlayerFile(DockerControl.PlayerFile.OPERATORS);
        assertThat(Files.readString(root.resolve("whitelist.json"))).isEqualTo("[]");
    }
    @Test void unavailableDockerFilesDoNotPermitARconMutation() throws Exception {
        when(guard.readTestPlayerFile(DockerControl.PlayerFile.WHITELIST)).thenThrow(new IOException("private path"));
        var dockerFiles = new TestWhitelistControl(new TestWhitelistProperties(true, null, "docker-test"), StatusServiceTest.properties(25585, "docker-test-control"),
            new RconProperties("test-control", "127.0.0.1", 25595, root.resolve("unused"), Duration.ofSeconds(1)), rcon, guard, new ObjectMapper());
        assertThatThrownBy(() -> dockerFiles.apply(OperationStore.Type.WHITELIST_ADD, "TestPlayer")).isInstanceOf(IOException.class).hasMessage("Testdatei nicht bestätigt");
        verify(rcon, never()).addToTestWhitelist(anyString());
    }
    @Test void opGrantRequiresExplicitGateAndWhitelistAndConfirmsTheActualFile() throws Exception {
        assertThatThrownBy(() -> control.apply(OperationStore.Type.OP_GRANT,"TestPlayer")).isInstanceOf(IOException.class);
        org.springframework.test.util.ReflectionTestUtils.setField(control,"opEnabled",true);
        assertThatThrownBy(() -> control.apply(OperationStore.Type.OP_GRANT,"TestPlayer")).isInstanceOf(IOException.class);
        verify(rcon,never()).changeTestOperator(anyBoolean(),anyString());
        Files.writeString(root.resolve("whitelist.json"),ENTRY);
        doAnswer(call->{Files.writeString(root.resolve("ops.json"),ENTRY);return null;}).when(rcon).changeTestOperator(true,"TestPlayer");
        control.apply(OperationStore.Type.OP_GRANT,"TestPlayer");
        control.apply(OperationStore.Type.OP_GRANT,"TestPlayer");
        verify(rcon,times(1)).changeTestOperator(true,"TestPlayer");
        assertThat(Files.readString(root.resolve("whitelist.json"))).isEqualTo(ENTRY);
    }
    @Test void deopConfirmsAbsenceAndDoesNotRemoveWhitelistMembership() throws Exception {
        org.springframework.test.util.ReflectionTestUtils.setField(control,"opEnabled",true);
        Files.writeString(root.resolve("whitelist.json"),ENTRY); Files.writeString(root.resolve("ops.json"),ENTRY);
        doAnswer(call->{Files.writeString(root.resolve("ops.json"),"[]");return null;}).when(rcon).changeTestOperator(false,"TestPlayer");
        control.apply(OperationStore.Type.OP_REVOKE,"TestPlayer");
        assertThat(Files.readString(root.resolve("whitelist.json"))).isEqualTo(ENTRY);
    }
}
