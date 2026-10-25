package de.elbracht.smp.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class MinecraftStatusProbeTest {
    private MinecraftProbe.Result exchange(String json, boolean oversized) throws Exception {
        try (var server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")); var executor = Executors.newVirtualThreadPerTaskExecutor();
             var probe = new MinecraftStatusProbe(StatusServiceTest.properties(server.getLocalPort(), "docker-read-only"), new ObjectMapper())) {
            var writer = executor.submit(() -> {
                try (var socket = server.accept()) {
                    socket.setSoTimeout(1000);
                    var input = new DataInputStream(socket.getInputStream());
                    int length = MinecraftStatusProbe.readVarInt(input); var hello = new DataInputStream(new ByteArrayInputStream(input.readNBytes(length)));
                    assertThat(MinecraftStatusProbe.readVarInt(hello)).isZero(); assertThat(MinecraftStatusProbe.readVarInt(hello)).isEqualTo(-1);
                    int hostLength = MinecraftStatusProbe.readVarInt(hello);
                    assertThat(new String(hello.readNBytes(hostLength), StandardCharsets.UTF_8)).isEqualTo("127.0.0.1");
                    assertThat(hello.readUnsignedShort()).isEqualTo(server.getLocalPort()); assertThat(MinecraftStatusProbe.readVarInt(hello)).isEqualTo(1);
                    assertThat(input.readUnsignedByte()).isEqualTo(1); assertThat(input.readUnsignedByte()).isZero();
                    var output = new DataOutputStream(socket.getOutputStream());
                    if (oversized) MinecraftStatusProbe.writeVarInt(output, 100_000);
                    else {
                        var body = new ByteArrayOutputStream(); var packet = new DataOutputStream(body);
                        MinecraftStatusProbe.writeVarInt(packet, 0); byte[] data = json.getBytes(StandardCharsets.UTF_8);
                        MinecraftStatusProbe.writeVarInt(packet, data.length); packet.write(data);
                        MinecraftStatusProbe.writeVarInt(output, body.size()); output.write(body.toByteArray());
                    }
                    output.flush();
                }
                return null;
            });
            try { return probe.query(); } finally { writer.get(); }
        }
    }
    @Test void measuresStatusUsingHandshakeAndNoLogin() throws Exception {
        var result = exchange("{\"version\":{\"name\":\"test-version\",\"protocol\":999},\"players\":{\"online\":2}}", false);
        assertThat(result.minecraftVersion()).isEqualTo("test-version"); assertThat(result.playersOnline()).isEqualTo(2);
    }
    @Test void rejectsOversizedAndInvalidResponses() {
        assertThatThrownBy(() -> exchange("{}", true)).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> exchange("{\"version\":{\"name\":\"x\",\"protocol\":1},\"players\":{\"online\":-1}}", false)).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> exchange("{}", false)).isInstanceOf(IOException.class);
    }
    @Test void readDeadlineIncludesAServerThatNeverSendsAResponse() throws Exception {
        try (var server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")); var executor = Executors.newVirtualThreadPerTaskExecutor();
             var probe = new MinecraftStatusProbe(StatusServiceTest.properties(server.getLocalPort(), "docker-read-only"), new ObjectMapper())) {
            var writer = executor.submit(() -> { try (var socket = server.accept()) { Thread.sleep(600); } return null; });
            assertThatThrownBy(probe::query).isInstanceOf(IOException.class); writer.get();
        }
    }
}
