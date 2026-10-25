package de.elbracht.smp.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/** Java-Edition Server-List-Status: kein Login, RCON oder Spielkommando. */
@Component
public class MinecraftStatusProbe implements MinecraftProbe, AutoCloseable {
    private final AgentProperties properties;
    private final ObjectMapper mapper;
    private final ScheduledExecutorService timeout = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "minecraft-status-deadline"); thread.setDaemon(true); return thread;
    });
    public MinecraftStatusProbe(AgentProperties properties, ObjectMapper mapper) { this.properties = properties; this.mapper = mapper; }
    @Override public Result query() throws Exception {
        try (Socket socket = new Socket()) {
            var deadline = timeout.schedule(() -> { try { socket.close(); } catch (IOException ignored) { } }, properties.minecraftTimeout().toMillis(), TimeUnit.MILLISECONDS);
            try {
                socket.connect(new InetSocketAddress("127.0.0.1", properties.minecraftPort()), (int) properties.minecraftTimeout().toMillis());
                socket.setSoTimeout((int) properties.minecraftTimeout().toMillis());
                var output = new DataOutputStream(socket.getOutputStream());
                var handshake = new ByteArrayOutputStream(); var packet = new DataOutputStream(handshake);
                writeVarInt(packet, 0); writeVarInt(packet, -1);
                byte[] host = "127.0.0.1".getBytes(StandardCharsets.UTF_8);
                writeVarInt(packet, host.length); packet.write(host); packet.writeShort(properties.minecraftPort()); writeVarInt(packet, 1);
                writeVarInt(output, handshake.size()); output.write(handshake.toByteArray());
                output.writeByte(1); output.writeByte(0); output.flush();
                var input = new DataInputStream(socket.getInputStream());
                int length = readVarInt(input);
                if (length < 2 || length > 16_384) throw new IOException("Invalid Minecraft packet size");
                byte[] bytes = input.readNBytes(length);
                if (bytes.length != length) throw new EOFException();
                var response = new DataInputStream(new ByteArrayInputStream(bytes));
                if (readVarInt(response) != 0) throw new IOException("Invalid Minecraft response type");
                int jsonLength = readVarInt(response);
                if (jsonLength < 2 || jsonLength != response.available()) throw new IOException("Invalid Minecraft JSON size");
                var json = mapper.readTree(response.readNBytes(jsonLength));
                String version = json.path("version").path("name").asText("");
                var online = json.path("players").path("online");
                if (!version.matches("[A-Za-z0-9._+ -]{1,64}") || !json.path("version").path("protocol").isIntegralNumber()
                    || !online.isIntegralNumber() || !online.canConvertToInt() || online.asInt() < 0) throw new IOException("Invalid Minecraft status");
                return new Result(version, online.asInt());
            } finally { deadline.cancel(false); }
        }
    }
    static void writeVarInt(DataOutputStream output, int value) throws IOException {
        do { int next = value & 0x7f; value >>>= 7; output.writeByte(next | (value != 0 ? 0x80 : 0)); } while (value != 0);
    }
    static int readVarInt(DataInputStream input) throws IOException {
        int value = 0;
        for (int index = 0; index < 5; index++) {
            int next = input.readUnsignedByte();
            if (index == 4 && (next & 0xf0) != 0) throw new IOException("Invalid VarInt");
            value |= (next & 0x7f) << (7 * index);
            if ((next & 0x80) == 0) return value;
        }
        throw new IOException("Invalid VarInt");
    }
    @Override public void close() { timeout.shutdownNow(); }
}
