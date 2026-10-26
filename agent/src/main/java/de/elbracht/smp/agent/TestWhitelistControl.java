package de.elbracht.smp.agent;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/** Test-only: read Minecraft-owned files, change membership solely through named RCON operations. */
@Component
@EnableConfigurationProperties(TestWhitelistProperties.class)
public class TestWhitelistControl {
    private final TestWhitelistProperties properties;
    private final RconProperties rconProperties;
    private final RconPlayerReader rcon;
    private final DockerControl guard;
    private final ObjectMapper mapper;
    private final Path root;
    @org.springframework.beans.factory.annotation.Value("${agent.test-op-enabled:false}")
    private boolean opEnabled;
    public TestWhitelistControl(TestWhitelistProperties properties, AgentProperties agent, RconProperties rconProperties,
                                RconPlayerReader rcon, DockerControl guard, ObjectMapper mapper) {
        this.properties = properties; this.rconProperties = rconProperties; this.rcon = rcon; this.guard = guard;
        this.mapper = mapper.copy().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        if (!"directory".equals(properties.source()) && !"docker-test".equals(properties.source()))
            throw new IllegalStateException("Ungültige Testdateiquelle");
        Path resolved = null;
        if (properties.enabled()) {
            if (!"docker-test-control".equals(agent.mode()) || !"elbracht-smp-control-test-minecraft".equals(agent.container())
                    || !"test-control".equals(rconProperties.mode()) || !rcon.available()
                    || ("directory".equals(properties.source()) && properties.dataDirectory() == null))
                throw new IllegalStateException("Whitelist-Steuerung benötigt den ausdrücklichen Testanschluss");
            if ("directory".equals(properties.source())) {
                try { resolved = properties.dataDirectory().toRealPath(); if (!Files.isDirectory(resolved)) throw new IOException(); }
                catch (IOException error) { throw new IllegalStateException("Testdatenverzeichnis fehlt"); }
            }
        }
        root = resolved;
    }
    public boolean available() { return properties.enabled(); }
    public boolean operatorsAvailable() { return available() && opEnabled; }
    public void apply(OperationStore.Type type, String name) throws Exception {
        if (!available() || !type.playerOperation() || name == null || !name.matches("[A-Za-z0-9_]{3,16}"))
            throw new IllegalStateException("Test-Whitelist deaktiviert oder ungültiges Ziel");
        guard.verifyRconTarget(rconProperties.port());
        var before = readState();
        if (type.operatorOperation()) {
            if (!operatorsAvailable()) throw new IOException("OP-Steuerung deaktiviert");
            boolean grant = type == OperationStore.Type.OP_GRANT;
            if (grant && !before.hasName(name)) throw new IOException("OP erfordert bestätigte Whitelist-Mitgliedschaft");
            if (before.isOperator(name) == grant) return;
            guard.verifyRconTarget(rconProperties.port());
            rcon.changeTestOperator(grant, name);
            var after = readState();
            if (after.isOperator(name) != grant || !after.whitelist.equals(before.whitelist)) throw new IOException("OP-Zustand nicht bestätigt");
            return;
        }
        boolean add = type == OperationStore.Type.WHITELIST_ADD;
        if (!add && before.isOperator(name)) throw new IOException("OP muss separat entzogen werden");
        if (before.hasName(name) == add) return; // Already confirmed; no RCON mutation needed.
        // Repeat guard immediately before the only possible mutation.
        guard.verifyRconTarget(rconProperties.port());
        if (add) rcon.addToTestWhitelist(name); else rcon.removeFromTestWhitelist(name);
        var after = readState();
        if (after.hasName(name) != add || (!add && after.isOperator(name))) throw new IOException("Whitelist-Zustand nicht bestätigt");
    }
    public record Entry(String name, String uuid) {}
    public record PlayerLists(List<Entry> whitelist, List<Entry> operators) {}
    public PlayerLists snapshot() throws Exception {
        if (!available()) throw new IOException("Spielerdateien nicht angebunden");
        guard.verifyTarget();
        var state = readState();
        return new PlayerLists(state.whitelist, state.operators);
    }
    private record State(List<Entry> whitelist, List<Entry> operators) {
        boolean hasName(String name) { return whitelist.stream().anyMatch(e -> e.name.equalsIgnoreCase(name)); }
        boolean isOperator(String name) {
            return operators.stream().anyMatch(e -> e.name.equalsIgnoreCase(name)
                || whitelist.stream().anyMatch(w -> w.name.equalsIgnoreCase(name) && w.uuid.equalsIgnoreCase(e.uuid)));
        }
    }
    private State readState() throws IOException { return new State(read("whitelist.json"), read("ops.json")); }
    private List<Entry> read(String file) throws IOException {
        byte[] bytes = readBytes(file);
        if (bytes == null || bytes.length > 2_097_152) throw new IOException();
        var tree = mapper.readTree(bytes);
        if (tree == null || !tree.isArray() || tree.size() > 1000) throw new IOException();
        List<Entry> entries = new ArrayList<>(); Set<String> names = new HashSet<>(), ids = new HashSet<>();
        for (var node : tree) {
            String name = node.path("name").asText(""), uuid = node.path("uuid").asText("");
            if (!node.path("name").isTextual() || !node.path("uuid").isTextual() || !name.matches("[A-Za-z0-9_]{3,16}")
                    || !uuid.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
                    || !names.add(name.toLowerCase(Locale.ROOT)) || !ids.add(uuid.toLowerCase(Locale.ROOT))) throw new IOException();
            entries.add(new Entry(name, uuid));
        }
        return List.copyOf(entries);
    }
    private byte[] readBytes(String file) throws IOException {
        if ("docker-test".equals(properties.source())) {
            try { return guard.readTestPlayerFile("whitelist.json".equals(file) ? DockerControl.PlayerFile.WHITELIST : DockerControl.PlayerFile.OPERATORS); }
            catch (Exception error) { if (error instanceof InterruptedException) Thread.currentThread().interrupt(); throw new IOException("Testdatei nicht bestätigt"); }
        }
        Path path = root.resolve(file);
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)) throw new IOException();
        try (var input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            return input.readNBytes(2_097_153);
        }
    }
}
