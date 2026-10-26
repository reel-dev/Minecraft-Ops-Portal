package de.elbracht.smp.agent;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class RconPlayerReaderTest {
    @TempDir Path directory;
    private static final String SECRET = "test-only secret with spaces ";
    @FunctionalInterface interface Scenario { void respond(Socket socket) throws Exception; }
    private RconPlayerReader reader(int port, Duration timeout) throws Exception {
        var file = directory.resolve("rcon-secret"); Files.writeString(file, SECRET + "\n");
        return new RconPlayerReader(new RconProperties("read-only", "127.0.0.1", port, file, timeout));
    }
    private void scenario(Scenario serverAction, Duration timeout, boolean succeeds) throws Exception {
        try (var server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
             var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            server.setSoTimeout(3000);
            var fake = executor.submit(() -> { try (var socket = server.accept()) {
                socket.setSoTimeout(2000); serverAction.respond(socket);
            } catch (Exception error) { throw new RuntimeException(error); } });
            var reader = reader(server.getLocalPort(), timeout);
            if (succeeds) assertThat(reader.readOnlineNames()).containsExactly("Player_A", "Test_B");
            else assertThatThrownBy(reader::readOnlineNames).isInstanceOf(IOException.class)
                .hasMessage("RCON-Spielerabfrage nicht bestätigt");
            fake.get(4, TimeUnit.SECONDS);
        }
    }
    private record Packet(int id, int type, String body) {}
    private static Packet request(Socket socket) throws Exception {
        var input = socket.getInputStream();
        int size = ByteBuffer.wrap(input.readNBytes(4)).order(ByteOrder.LITTLE_ENDIAN).getInt();
        byte[] bytes = input.readNBytes(size);
        var packet = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        return new Packet(packet.getInt(), packet.getInt(), new String(bytes, 8, size - 10, StandardCharsets.UTF_8));
    }
    @Test void authenticatesAndSendsOnlyListWithCorrelatedMultipartResponse() throws Exception {
        scenario(socket -> {
            var auth = request(socket); assertThat(auth.type()).isEqualTo(3); assertThat(auth.body()).isEqualTo(SECRET);
            RconPlayerReader.write(socket.getOutputStream(), 1, 0, "");
            RconPlayerReader.write(socket.getOutputStream(), 1, 2, "");
            var command = request(socket); assertThat(command.body()).isEqualTo("list"); assertThat(command.id()).isEqualTo(2);
            RconPlayerReader.write(socket.getOutputStream(), 2, 0, "There are 2 of a max of 20 players online: Player_");
            var end = request(socket); assertThat(end.body()).isEmpty(); assertThat(end.id()).isEqualTo(3);
            RconPlayerReader.write(socket.getOutputStream(), 2, 0, "A, Test_B");
            RconPlayerReader.write(socket.getOutputStream(), 3, 0, "");
        }, Duration.ofSeconds(2), true);
    }
    @Test void rejectedAuthenticationClosesWithoutSendingCommand() throws Exception {
        scenario(socket -> { request(socket); RconPlayerReader.write(socket.getOutputStream(), -1, 2, "");
            assertThat(socket.getInputStream().read()).isEqualTo(-1);
        }, Duration.ofSeconds(1), false);
    }
    @Test void rejectsMalformedLengthsAndUncorrelatedPackets() throws Exception {
        scenario(socket -> { request(socket); socket.getOutputStream().write(new byte[]{-1, -1, -1, 127}); }, Duration.ofSeconds(1), false);
        scenario(socket -> { request(socket); RconPlayerReader.write(socket.getOutputStream(), 999, 2, ""); }, Duration.ofSeconds(1), false);
    }
    @Test void totalDeadlineClosesIncompleteResponse() throws Exception {
        assertThatCode(() -> org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(Duration.ofSeconds(2), () ->
            scenario(socket -> { request(socket); socket.getOutputStream().write(10); socket.getOutputStream().flush();
                assertThat(socket.getInputStream().read()).isEqualTo(-1);
            }, Duration.ofMillis(200), false))).doesNotThrowAnyException();
    }
    @Test void emptyListIsValidButInconsistentOrUnknownFormatsAreRejected() throws Exception {
        assertThat(RconPlayerReader.parseNames("There are 0 of a max of 20 players online: ")).isEmpty();
        for (String response : new String[]{"Unknown command", "There are 1 of a max of 20 players online:",
                "There are 2 of a max of 20 players online: Steve, steve", "There are 1 of a max of 0 players online: Steve",
                "There are 1 of a max of 20 players online: Steve;op", "There are 1 of a max of 20 players online: Steve,",
                "There are 1 of a max of 20 players online: Steve\nprivate-upstream-text"})
            assertThatThrownBy(() -> RconPlayerReader.parseNames(response)).isInstanceOf(IOException.class);
    }
    @Test void disabledModeReadsNoSecretAndInvalidConfigurationFailsClosed() throws Exception {
        var disabled = new RconPlayerReader(new RconProperties("disconnected", "127.0.0.1", 25575, directory.resolve("missing"), Duration.ofSeconds(1)));
        assertThat(disabled.available()).isFalse();
        assertThatThrownBy(disabled::readOnlineNames).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> new RconProperties("read-only", "minecraft-server", 25575, directory.resolve("unused"), Duration.ofSeconds(1))).isInstanceOf(IllegalStateException.class);
        var secret = directory.resolve("bad-secret"); Files.writeString(secret, "private\nsecond-line");
        assertThatThrownBy(() -> new RconPlayerReader(new RconProperties("read-only", "127.0.0.1", 25575, secret, Duration.ofSeconds(1))))
            .isInstanceOf(IllegalStateException.class).hasMessage("RCON-Secret-Datei fehlt oder ist ungültig");
    }
    @Test void readOnlyModeRejectsNamedWhitelistMutationsBeforeOpeningSocket() throws Exception {
        var client = reader(65534, Duration.ofMillis(100));
        assertThatThrownBy(() -> client.addToTestWhitelist("TestPlayer")).isInstanceOf(IOException.class).hasMessage("Test-RCON-Steuerung deaktiviert");
        assertThatThrownBy(() -> client.removeFromTestWhitelist("TestPlayer")).isInstanceOf(IOException.class).hasMessage("Test-RCON-Steuerung deaktiviert");
    }
    @Test void testControlSendsOnlyValidatedNamedWhitelistCommand() throws Exception {
        try (var server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")); var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            server.setSoTimeout(3000);
            var fake = executor.submit(() -> { try (var socket = server.accept()) {
                socket.setSoTimeout(2000); request(socket); RconPlayerReader.write(socket.getOutputStream(), 1, 2, "");
                assertThat(request(socket).body()).isEqualTo("whitelist add TestPlayer");
                RconPlayerReader.write(socket.getOutputStream(), 2, 0, "Added TestPlayer");
                assertThat(request(socket).body()).isEmpty(); RconPlayerReader.write(socket.getOutputStream(), 3, 0, "");
            } catch (Exception error) { throw new RuntimeException(error); } });
            var secret = rootSecret();
            var client = new RconPlayerReader(new RconProperties("test-control", "127.0.0.1", server.getLocalPort(), secret, Duration.ofSeconds(2)));
            assertThatThrownBy(() -> client.addToTestWhitelist("bad;op")).isInstanceOf(IOException.class);
            client.addToTestWhitelist("TestPlayer"); fake.get(4, TimeUnit.SECONDS);
        }
    }
    private Path rootSecret() throws Exception { var file = directory.resolve("test-control-secret"); Files.writeString(file, SECRET); return file; }
    @Test void waitsForFirstCommandResponseBeforeSendingCompletionProbe() throws Exception {
        scenario(socket -> {
            request(socket); RconPlayerReader.write(socket.getOutputStream(), 1, 2, "");
            assertThat(request(socket).body()).isEqualTo("list");
            socket.setSoTimeout(100);
            org.junit.jupiter.api.Assertions.assertThrows(SocketTimeoutException.class, () -> socket.getInputStream().read());
            socket.setSoTimeout(2000);
            RconPlayerReader.write(socket.getOutputStream(), 2, 0, "There are 2 of a max of 20 players online: Player_A, Test_B");
            assertThat(request(socket).id()).isEqualTo(3);
            RconPlayerReader.write(socket.getOutputStream(), 3, 0, "");
        }, Duration.ofSeconds(2), true);
    }
}
