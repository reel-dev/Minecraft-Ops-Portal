package de.elbracht.smp.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Semaphore;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Fixed test-container sources; never accepts a path, container, process or command. */
@RestController
public class DiagnosticsController {
    private final AgentProperties properties;
    private final DockerControl docker;
    private final TestWhitelistControl players;
    private final ObjectMapper mapper;
    private record MetricSnapshot(Instant observedAt,Map<String,String> data){}
    private volatile MetricSnapshot metricSnapshot;
    @org.springframework.beans.factory.annotation.Autowired private LocalBackupService backups;
    @org.springframework.beans.factory.annotation.Autowired private TestConfigurationService configuration;
    @org.springframework.beans.factory.annotation.Autowired private RestoreService restore;
    @org.springframework.beans.factory.annotation.Autowired private BackupRetention retention;
    private final Map<String,Semaphore> probes = Map.of("players",new Semaphore(1),"metrics",new Semaphore(1),"logs",new Semaphore(1),"backups",new Semaphore(1),"retention",new Semaphore(1),"configuration",new Semaphore(1),"mods",new Semaphore(1),"runtime",new Semaphore(1));
    public DiagnosticsController(AgentProperties properties, DockerControl docker, TestWhitelistControl players, ObjectMapper mapper) {
        this.properties = properties; this.docker = docker; this.players = players; this.mapper = mapper;
    }
    @GetMapping("/agent/v1/servers/{serverId}/diagnostics/{kind}")
    public ResponseEntity<?> read(@PathVariable String serverId, @PathVariable String kind) {
        if (!serverId.equals(properties.serverId()) || !Set.of("players", "metrics", "logs", "backups", "retention", "configuration", "mods", "runtime").contains(kind)) return ResponseEntity.notFound().build();
        var probe = probes.get(kind);
        if (!"docker-test-control".equals(properties.mode()))return ResponseEntity.status(503).build();
        if(kind.equals("metrics")&&fresh(metricSnapshot))return metricResponse(serverId,metricSnapshot);
        boolean acquired;try{acquired=kind.equals("metrics")?probe.tryAcquire(3,java.util.concurrent.TimeUnit.SECONDS):probe.tryAcquire();}catch(InterruptedException error){Thread.currentThread().interrupt();return ResponseEntity.status(503).build();}
        if(!acquired)return ResponseEntity.status(503).build();
        try {
            Instant observedAt=Instant.now();
            Object data = switch (kind) {
                case "players" -> players.snapshot();
                case "metrics" -> {var snapshot=metricSnapshot;if(!fresh(snapshot)){var measured=metrics(docker.readTestStats(),mapper);snapshot=new MetricSnapshot(Instant.now(),Map.copyOf(measured));metricSnapshot=snapshot;}observedAt=snapshot.observedAt();yield snapshot.data();}
                case "logs" -> Map.of("lines", redactLogs(docker.readTestLogs()));
                case "backups" -> Map.of("available",backups.available(),"snapshots",backups.snapshots(),"bootTestsAvailable",backups.bootTestsAvailable(),"restoreAvailable",restore.available(),"scope","LOCAL_ENCRYPTED_FULL_MINECRAFT_DATA","retention",retention.policy());
                case "retention" -> {var preview=retention.preview();observedAt=Instant.now();yield preview;}
                case "configuration" -> configuration.read();
                case "mods" -> Map.of("files",modFiles(docker.readTestMods()));
                case "runtime" -> Map.of("javaVersion",docker.readTestJavaVersion());
                default -> throw new IllegalArgumentException();
            };
            return ResponseEntity.ok(Map.of("serverId", serverId, "observedAt", observedAt, "data", data));
        } catch (Exception error) {
            if (error instanceof InterruptedException) Thread.currentThread().interrupt();
            return ResponseEntity.status(503).build();
        } finally { probe.release(); }
    }
    private static boolean fresh(MetricSnapshot snapshot){Instant now=Instant.now();return snapshot!=null&&snapshot.observedAt().isAfter(now.minusSeconds(3))&&!snapshot.observedAt().isAfter(now.plusSeconds(1));}
    private static ResponseEntity<?> metricResponse(String server,MetricSnapshot snapshot){return ResponseEntity.ok(Map.of("serverId",server,"observedAt",snapshot.observedAt(),"data",snapshot.data()));}
    static Map<String, String> metrics(String raw, ObjectMapper mapper) throws Exception {
        var node = mapper.readTree(raw);
        Map<String, String> result = new LinkedHashMap<>();
        for (String field : List.of("CPUPerc", "MemUsage", "MemPerc", "NetIO", "BlockIO", "PIDs")) {
            String value = node.path(field).asText("");
            if (value.isEmpty() || value.length() > 100 || !value.matches("[0-9A-Za-z.% /+:-]+")) throw new java.io.IOException();
            result.put(field, value);
        }
        return result;
    }
    static List<String> redactLogs(String raw) {
        return raw.lines().limit(200).map(line -> {
            // Drop whole potentially confidential/chat line; do not try to reveal partial credentials.
            if (line.matches("(?i).*(password|passwd|secret|token|authorization|api[-_ ]?key|rcon|<[^>]{1,40}>|issued server command|https?://[^\\s/]+@|[?&](key|auth|signature|credential)=).*"))
                return timestamp(line)+"[Vertrauliche Logzeile ausgeblendet]";
            String cleaned=line.replaceAll("\\u001B\\[[;\\d]*[ -/]*[@-~]", "")
                .replaceAll("(?<![0-9])(?:[0-9]{1,3}\\.){3}[0-9]{1,3}(?::[0-9]+)?", "[IP]")
                .replaceAll("[\\p{Cntrl}&&[^\\t]]", "");
            var ipv6=java.util.regex.Pattern.compile("(?i)(?<![0-9a-f:])(?:[0-9a-f]{0,4}:){2,7}[0-9a-f]{0,4}(?![0-9a-f:])").matcher(cleaned);
            return ipv6.replaceAll(match->{String candidate=match.group();return candidate.contains("::")||candidate.chars().filter(ch->ch==':').count()>=7?"[IP]":candidate;});
        }).map(line -> line.length() > 2000 ? line.substring(0, 2000) + " …" : line).toList();
    }
    static List<Map<String,Object>> modFiles(String raw)throws java.io.IOException{
        List<Map<String,Object>> files=new ArrayList<>();for(String line:raw.lines().toList()){
            String[] parts=line.split("\t",-1);if(parts.length!=2||!parts[0].matches("[A-Za-z0-9_.+ -]{1,180}\\.jar")||!parts[1].matches("[0-9]{1,12}")||files.size()>=500)throw new java.io.IOException();
            files.add(Map.of("name",parts[0],"bytes",Long.parseLong(parts[1]),"provenance","UNMAPPED"));
        }return List.copyOf(files);
    }
    private static String timestamp(String line){int space=line.indexOf(' ');if(space<1||space>40)return "";String prefix=line.substring(0,space);try{java.time.Instant.parse(prefix);return prefix+" ";}catch(Exception error){return "";}}
}
