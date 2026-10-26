package de.elbracht.smp.agent;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.charset.*;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/** Read list by default; named whitelist operations require a separately guarded test-control mode. */
@Component
@EnableConfigurationProperties(RconProperties.class)
public class RconPlayerReader {
    private final RconProperties properties;
    private final String password;
    public RconPlayerReader(RconProperties properties) {
        this.properties = properties;
        String secret = null;
        if (!"disconnected".equals(properties.mode())) {
            try (var input = Files.newInputStream(properties.passwordFile())) {
                byte[] raw = input.readNBytes(4099);
                secret = decode(raw);
                // Strip at most one file line ending, never meaningful password spaces.
                if (secret.endsWith("\r\n")) secret = secret.substring(0, secret.length() - 2);
                else if (secret.endsWith("\n")) secret = secret.substring(0, secret.length() - 1);
                if (raw.length > 4098 || secret.isEmpty() || secret.getBytes(StandardCharsets.UTF_8).length > 4096
                        || secret.indexOf('\0') >= 0 || secret.indexOf('\n') >= 0 || secret.indexOf('\r') >= 0) throw new IOException();
            } catch (Exception error) { throw new IllegalStateException("RCON-Secret-Datei fehlt oder ist ungültig"); }
        }
        this.password = secret;
    }
    public boolean available() { return password != null; }
    byte[] prepareRestoredProperties(byte[] original)throws IOException{
        if(!"test-control".equals(properties.mode())||!available()||original==null||original.length>1_048_576)throw new IOException();
        var values=new java.util.Properties();values.load(new java.io.InputStreamReader(new ByteArrayInputStream(original),StandardCharsets.UTF_8));
        values.setProperty("rcon.password",password);values.setProperty("enable-rcon","true");values.setProperty("rcon.port","25575");values.setProperty("broadcast-rcon-to-ops","false");
        var writer=new StringWriter();values.store(writer,"Restored settings; current RCON access retained");return writer.toString().getBytes(StandardCharsets.UTF_8);
    }
    public List<String> readOnlineNames() throws IOException {
        return parseNames(exchange("list"));
    }
    String testConsole(String command) throws IOException {
        if (!"test-control".equals(properties.mode()) || command == null || command.length() > 512 || command.isBlank()
            || command.chars().anyMatch(c -> c < 32 || c == 127) || command.startsWith("/")) throw new IOException();
        String root = command.split(" ", 2)[0].toLowerCase(Locale.ROOT);
        if (Set.of("op", "deop", "whitelist", "stop", "reload", "datapack", "save-off").contains(root)) throw new IOException();
        return String.join("\n", DiagnosticsController.redactLogs(exchange(command)));
    }
    // Package-private named test operations; no free-command HTTP interface.
    void addToTestWhitelist(String name) throws IOException { changeWhitelist("add", name); }
    void removeFromTestWhitelist(String name) throws IOException { changeWhitelist("remove", name); }
    void changeTestOperator(boolean grant, String name) throws IOException {
        if (!"test-control".equals(properties.mode()) || name == null || !name.matches("[A-Za-z0-9_]{3,16}")) throw new IOException("Ungültige OP-Operation");
        exchange((grant ? "op " : "deop ") + name);
    }
    private void changeWhitelist(String action, String name) throws IOException {
        if (!"test-control".equals(properties.mode())) throw new IOException("Test-RCON-Steuerung deaktiviert");
        if (name == null || !name.matches("[A-Za-z0-9_]{3,16}")) throw new IOException("Ungültiges Spielerziel");
        exchange("whitelist " + action + " " + name);
    }
    private String exchange(String command) throws IOException {
        if (!available()) throw new IOException("RCON nicht angebunden");
        try (var socket = new Socket()) {
            var timer = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon().factory());
            var cutoff = timer.schedule(() -> { try { socket.close(); } catch (IOException ignored) { } },
                properties.timeout().toMillis(), TimeUnit.MILLISECONDS);
            try {
                // Literal address prevents DNS resolution from escaping the total deadline.
                socket.connect(new InetSocketAddress(InetAddress.getByAddress(new byte[]{127, 0, 0, 1}), properties.port()), (int)properties.timeout().toMillis());
                socket.setSoTimeout((int)properties.timeout().toMillis());
                var out = socket.getOutputStream();
                write(out, 1, 3, password);
                Packet auth = read(socket);
                if (auth.id == 1 && auth.type == 0 && auth.body.length == 0) auth = read(socket);
                if (auth.id != 1 || auth.type != 2) throw new IOException();
                write(out, 2, 2, command);
                boolean completionSent = false;
                var result = new ByteArrayOutputStream();
                for (int count = 0; count < 1024; count++) {
                    Packet packet = read(socket);
                    if (completionSent && packet.id == 3 && packet.type == 0) return decode(result.toByteArray());
                    if (packet.id != 2 || packet.type != 0 || result.size() + packet.body.length > 16_384) throw new IOException();
                    result.write(packet.body);
                    // Minecraft may close the connection if two requests arrive in one read.
                    // Wait for response #2 before issuing the completion request. Remaining
                    // response #2 fragments are still collected until correlated response #3.
                    if (!completionSent) { write(out, 3, 2, ""); completionSent = true; }
                }
                throw new IOException();
            } finally { cutoff.cancel(false); timer.shutdownNow(); }
        } catch (Exception error) {
            // No retry, upstream body, password, path or address in errors.
            throw new IOException("RCON-Spielerabfrage nicht bestätigt");
        }
    }
    static List<String> parseNames(String response) throws IOException {
        var match = Pattern.compile("There are ([0-9]{1,6}) of a max of ([0-9]{1,6}) players online: ?(.*)").matcher(response.strip());
        if (!match.matches()) throw new IOException();
        int count = Integer.parseInt(match.group(1)), maximum = Integer.parseInt(match.group(2));
        if (count > 500 || count > maximum) throw new IOException();
        String tail = match.group(3).trim();
        List<String> names = tail.isEmpty() ? List.of() : Arrays.stream(tail.split(",", -1)).map(String::trim).toList();
        Set<String> unique = new HashSet<>();
        for (String name : names)
            if (!name.matches("[A-Za-z0-9_]{3,16}") || !unique.add(name.toLowerCase(Locale.ROOT))) throw new IOException();
        if (names.size() != count) throw new IOException();
        return names;
    }
    static void write(OutputStream out, int id, int type, String text) throws IOException {
        byte[] body = text.getBytes(StandardCharsets.UTF_8);
        if (body.length > 4096) throw new IOException();
        out.write(ByteBuffer.allocate(body.length + 14).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(body.length + 10).putInt(id).putInt(type).put(body).put((byte)0).put((byte)0).array());
        out.flush();
    }
    private static Packet read(Socket socket) throws IOException {
        var input = socket.getInputStream();
        byte[] header = input.readNBytes(4);
        if (header.length != 4) throw new EOFException();
        int size = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN).getInt();
        if (size < 10 || size > 65_536) throw new IOException();
        byte[] bytes = input.readNBytes(size);
        if (bytes.length != size || bytes[size - 1] != 0 || bytes[size - 2] != 0) throw new IOException();
        var packet = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        return new Packet(packet.getInt(), packet.getInt(), Arrays.copyOfRange(bytes, 8, size - 2));
    }
    private static String decode(byte[] bytes) throws CharacterCodingException {
        return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
    }
    private record Packet(int id, int type, byte[] body) {}
}
