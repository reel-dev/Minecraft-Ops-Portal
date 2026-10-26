package de.elbracht.smp.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/** Kein frei parametrierbarer Dockeraufruf. Mutationen ausschließlich für unsere Test-Compose. */
@Component
public class TestDockerControl implements DockerControl {
    static final String TEST_IMAGE="itzg/minecraft-server@sha256:6fd6f9bb6998b7cd06948ec6ab03fabfdf98ba3f2ec030c44069bec9a6bdf09d";
    private static final String GUARD = "{{index .Config.Labels `de.elbracht.smp.environment`}}|{{index .Config.Labels `de.elbracht.smp.server-id`}}|{{index .Config.Labels `com.docker.compose.project`}}|{{range .Mounts}}{{.Type}}:{{.Name}}:{{.Destination}};{{end}}|{{json .HostConfig.PortBindings}}|{{.Config.Image}}";
    private final AgentProperties properties;
    private final ObjectMapper mapper;
    private String profileStart;
    private RuntimeProfile runtimeProfile;
    public TestDockerControl(AgentProperties properties, ObjectMapper mapper) { this.properties = properties; this.mapper = mapper; }
    private List<String> prefix() {
        if (!"docker-test-control".equals(properties.mode()) || !"elbracht-smp-control-test-minecraft".equals(properties.container()))
            throw new IllegalStateException("Teststeuerung deaktiviert");
        return new ArrayList<>(List.of("docker", "--context", properties.dockerContext(), "container"));
    }
    @Override public void verifyTarget() throws Exception {
        var command = prefix(); command.addAll(List.of("inspect", "--format", GUARD, "--", properties.container()));
        String output = run(command, Duration.ofSeconds(3));
        var pieces = output.strip().split("\\|", 6);
        if (pieces.length != 6 || !"test".equals(pieces[0]) || !properties.serverId().equals(pieces[1])
            || !"elbracht-smp-control-test".equals(pieces[2]) || !"volume:elbracht-smp-control-test_test-world:/data;".equals(pieces[3]) || !TEST_IMAGE.equals(pieces[5]))
            throw new IOException("Test target identity rejected");
        boolean allowed = false;
        for (var port : mapper.readTree(pieces[4]).path("25565/tcp"))
            if ("127.0.0.1".equals(port.path("HostIp").asText()) && Integer.toString(properties.minecraftPort()).equals(port.path("HostPort").asText())) allowed = true;
        if (!allowed) throw new IOException("Test target port rejected");
    }
    @Override public void start() throws Exception {
        verifyTarget(); var command = prefix(); command.addAll(List.of("start", "--", properties.container())); run(command, Duration.ofSeconds(10));
    }
    @Override public void verifyRconTarget(int port) throws Exception {
        verifyTarget();
        var command = prefix(); command.addAll(List.of("inspect", "--format", "{{json .HostConfig.PortBindings}}", "--", properties.container()));
        if (!rconBindingMatches(mapper.readTree(run(command, Duration.ofSeconds(3))), port))
            throw new IOException("Test RCON target rejected");
    }
    static boolean rconBindingMatches(com.fasterxml.jackson.databind.JsonNode ports, int port) {
        if (ports == null || port < 1024 || port > 65535) return false;
        var bindings = ports.path("25575/tcp");
        return bindings.isArray() && bindings.size() == 1 && "127.0.0.1".equals(bindings.get(0).path("HostIp").asText())
            && Integer.toString(port).equals(bindings.get(0).path("HostPort").asText());
    }
    @Override public void stop() throws Exception {
        verifyTarget(); var command = prefix();
        // Docker wartet ohne SIGKILL-Fallback. Bei Agenttimeout ist das Ergebnis UNKNOWN.
        command.addAll(List.of("stop", "--timeout=-1", "--", properties.container())); run(command, Duration.ofSeconds(35));
    }
    List<String> playerFileCommand(PlayerFile file) {
        if (file == null) throw new IllegalArgumentException("Testdatei fehlt");
        var command = prefix();
        command.addAll(List.of("exec", "--user", "1000", "--", properties.container(), "/bin/cat", file.path));
        return List.copyOf(command);
    }
    @Override public byte[] readTestPlayerFile(PlayerFile file) throws Exception {
        verifyTarget();
        return runBytes(playerFileCommand(file), Duration.ofSeconds(3), 2_097_152);
    }
    private String run(List<String> command, Duration timeout) throws Exception {
        return new String(runBytes(command, timeout, 4096), StandardCharsets.UTF_8);
    }
    @Override public String readTestStats() throws Exception {
        verifyTarget();
        requireRunningStatsTarget();
        var command = prefix(); command.addAll(List.of("stats", "--no-stream", "--format", "{{json .}}", "--", properties.container()));
        String result=new String(runBytes(command, Duration.ofSeconds(5), 16_384), StandardCharsets.UTF_8);requireRunningStatsTarget();return result;
    }
    private void requireRunningStatsTarget()throws Exception{
        var command=prefix();command.addAll(List.of("inspect","--format","{{.State.Running}}|{{.State.Paused}}|{{.State.Restarting}}|{{.State.Dead}}","--",properties.container()));
        if(!"true|false|false|false".equals(run(command,Duration.ofSeconds(3)).strip()))throw new IOException("Keine aktuelle Messung eines laufenden Testcontainers");
    }
    @Override public String readTestLogs() throws Exception {
        verifyTarget();
        var command = prefix(); command.addAll(List.of("logs", "--tail", "200", "--timestamps", "--", properties.container()));
        return new String(runBytes(command, Duration.ofSeconds(3), 131_072), StandardCharsets.UTF_8);
    }
    @Override public void copyTestWorld(java.nio.file.Path destination) throws Exception {
        verifyTarget();
        var stateCommand = prefix(); stateCommand.addAll(List.of("inspect", "--format", "{{.State.Status}}", "--", properties.container()));
        if (!"exited".equals(run(stateCommand,Duration.ofSeconds(3)).strip())) throw new IOException("Weltkopie erfordert gestoppten Testserver");
        var command=prefix();command.addAll(List.of("cp",properties.container()+":/data/.",destination.toString()));runBytes(command,Duration.ofSeconds(120),4096);
    }
    @Override public void verifyCleanTestStop()throws Exception{
        verifyTarget();var command=prefix();command.addAll(List.of("inspect","--format","{{.State.Status}}|{{.State.ExitCode}}|{{.State.OOMKilled}}","--",properties.container()));
        if(!"exited|0|false".equals(run(command,Duration.ofSeconds(3)).strip()))throw new IOException("Kein sauberer Testserver-Stop bestätigt");
    }
    @Override public String testTargetIdentity()throws Exception{
        verifyTarget();var command=prefix();command.addAll(List.of("inspect","--format","{{.Id}}|{{.Image}}|{{json .Config.Env}}","--",properties.container()));
        return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(runBytes(command,Duration.ofSeconds(3),131_072)));
    }
    private enum DataTool{COPY("/bin/cp"),MOVE("/bin/mv"),MKDIR("/bin/mkdir"),FIND("/usr/bin/find"),CHMOD("/bin/chmod");final String path;DataTool(String path){this.path=path;}}
    private String dataHelper(DataTool tool,List<String> arguments,java.nio.file.Path source,java.util.UUID operation)throws Exception{
        verifyTarget();var state=prefix();state.addAll(List.of("inspect","--format","{{.State.Status}}|{{.State.Running}}","--",properties.container()));if(!"exited|false".equals(run(state,Duration.ofSeconds(3)).strip()))throw new IOException("Datenwechsel erfordert gestoppten Testserver");
        var command=prefix();command.addAll(List.of("run","--rm","--network","none","--read-only","--cap-drop","ALL","--security-opt","no-new-privileges:true","--pids-limit","32","--memory","64m","--cpus","0.5","--user","1000:1000","--label","de.elbracht.smp.environment=test-data-helper","--label","de.elbracht.smp.operation-id="+operation,"--mount","type=volume,source=elbracht-smp-control-test_test-world,target=/data"));
        if(source!=null){var root=source.toRealPath();if(root.toString().contains(","))throw new IOException();command.addAll(List.of("--mount","type=bind,source="+root+",target=/portal-source,readonly"));}
        command.addAll(List.of("--entrypoint",tool.path,TEST_IMAGE));command.addAll(arguments);return new String(runBytes(command,Duration.ofSeconds(120),131_072),StandardCharsets.UTF_8);
    }
    private static boolean dataName(String name){return name!=null&&!name.equals(".")&&!name.equals("..")&&!name.startsWith(".portal-")&&name.matches("[A-Za-z0-9._+ -]{1,180}");}
    private List<String> dataNames(String directory,java.util.UUID operation)throws Exception{
        String raw=dataHelper(DataTool.FIND,List.of(directory,"-mindepth","1","-maxdepth","1","-printf","%f\\0"),null,operation);List<String> names=new ArrayList<>();
        for(String name:raw.split("\\x00",-1)){if(name.isEmpty()||name.startsWith(".portal-"))continue;if(!dataName(name)||names.size()>=1000||names.contains(name))throw new IOException("Unsicherer Dateneintrag");names.add(name);}return names.stream().sorted().toList();
    }
    private void moveData(String from,String to,java.util.UUID operation)throws Exception{dataHelper(DataTool.MOVE,List.of("--no-clobber","--",from,to),null,operation);}
    @Override public DataSwap swapTestData(java.nio.file.Path directory,java.util.UUID operation)throws Exception{
        verifyTarget();var root=directory.toRealPath();List<String> replacement;try(var paths=java.nio.file.Files.list(root)){replacement=paths.map(path->path.getFileName().toString()).filter(name->!name.startsWith(".portal-")).sorted().toList();}
        if(replacement.isEmpty()||replacement.stream().anyMatch(name->!dataName(name)))throw new IOException("Unsichere Wiederherstellungsdateien");
        var swap=new DataSwap(operation,dataNames("/data",operation),List.copyOf(replacement));String next="/data/.portal-next-"+operation,previous="/data/.portal-previous-"+operation;boolean oldComplete=false;
        dataHelper(DataTool.COPY,List.of("-R","--","/portal-source/.",next),root,operation);dataHelper(DataTool.MKDIR,List.of("-m","700","--",previous),null,operation);
        try{
            for(String name:swap.originalNames())moveData("/data/"+name,previous+"/"+name,operation);
            if(!dataNames("/data",operation).isEmpty()||!dataNames(previous,operation).equals(swap.originalNames()))throw new IOException();oldComplete=true;
            for(String name:replacement)moveData(next+"/"+name,"/data/"+name,operation);
            if(!dataNames("/data",operation).equals(replacement))throw new IOException();
            if(replacement.contains("server.properties"))dataHelper(DataTool.CHMOD,List.of("600","--","/data/server.properties"),null,operation);
            return swap;
        }catch(Exception failure){
            try{if(oldComplete)rollbackTestData(swap);else{
                var held=dataNames(previous,operation);var current=dataNames("/data",operation);for(String name:held){if(current.contains(name))throw new IOException();moveData(previous+"/"+name,"/data/"+name,operation);}if(!dataNames("/data",operation).equals(swap.originalNames()))throw new IOException();
            }}catch(Exception rollback){throw new DataSwapFailure(false);}throw new DataSwapFailure(true);
        }
    }
    @Override public void rollbackTestData(DataSwap swap)throws Exception{
        java.util.UUID operation=swap.operation();String previous="/data/.portal-previous-"+operation,failed="/data/.portal-failed-"+operation;
        if(!dataNames(previous,operation).equals(swap.originalNames()))throw new IOException("Rückweg unvollständig");
        dataHelper(DataTool.MKDIR,List.of("-m","700","--",failed),null,operation);
        for(String name:dataNames("/data",operation))moveData("/data/"+name,failed+"/"+name,operation);
        for(String name:swap.originalNames())moveData(previous+"/"+name,"/data/"+name,operation);
        if(!dataNames("/data",operation).equals(swap.originalNames()))throw new IOException("Rückweg nicht bestätigt");
    }
    @Override public void bootTestRestore(java.nio.file.Path directory,java.util.UUID operation)throws Exception{
        verifyTarget();var root=directory.toRealPath();if(!root.getFileName().toString().equals("restore-"+operation)||root.toString().contains(","))throw new IOException();
        List<java.nio.file.Path> jars;try(var files=java.nio.file.Files.list(root)){jars=files.filter(file->file.getFileName().toString().matches("fabric-server-mc\\.26\\.3-loader\\.0\\.19\\.5-launcher\\.[0-9.]+\\.jar")).toList();}
        if(jars.size()!=1||!java.nio.file.Files.isRegularFile(jars.getFirst(),java.nio.file.LinkOption.NOFOLLOW_LINKS))throw new IOException("Passender gesicherter Fabric-Launcher fehlt");
        String name="elbracht-smp-restore-probe-"+operation;
        var command=prefix();command.addAll(List.of("create","--name",name,"--label","de.elbracht.smp.environment=isolated-restore-probe","--network","none","--restart","no","--read-only","--cap-drop","ALL","--security-opt","no-new-privileges:true","--pids-limit","128","--memory","3g","--cpus","2","--user","1000:1000","--workdir","/data","--tmpfs","/tmp:rw,noexec,nosuid,size=128m,uid=1000,gid=1000","--mount","type=bind,source="+root+",target=/data","--entrypoint","java","itzg/minecraft-server@sha256:6fd6f9bb6998b7cd06948ec6ab03fabfdf98ba3f2ec030c44069bec9a6bdf09d","-Xms1G","-Xmx2G","-jar",jars.getFirst().getFileName().toString(),"nogui"));
        run(command,Duration.ofSeconds(10));boolean started=false;
        try{
            command=prefix();command.addAll(List.of("start","--",name));run(command,Duration.ofSeconds(10));started=true;
            long deadline=System.nanoTime()+Duration.ofSeconds(150).toNanos();boolean healthy=false;
            while(System.nanoTime()<deadline){command=prefix();command.addAll(List.of("inspect","--format","{{.State.Status}}|{{if .State.Health}}{{.State.Health.Status}}{{end}}","--",name));String state=run(command,Duration.ofSeconds(3)).strip();if(state.equals("running|healthy")){healthy=true;break;}if(state.startsWith("exited|"))break;Thread.sleep(500);}
            if(!healthy)throw new IOException("Isolierter Weltstart nicht bestätigt");
            command=prefix();command.addAll(List.of("logs","--tail","2000","--",name));String logs=new String(runBytes(command,Duration.ofSeconds(3),524_288),StandardCharsets.UTF_8);
            if(!logs.contains("Loading Minecraft 26.3 with Fabric Loader 0.19.5")||!logs.contains("Done ("))throw new IOException("Restore-Laufzeitprofil nicht bestätigt");
        }finally{
            if(started){command=prefix();command.addAll(List.of("stop","--timeout=-1","--",name));run(command,Duration.ofSeconds(35));command=prefix();command.addAll(List.of("inspect","--format","{{.State.Running}}","--",name));if(!"false".equals(run(command,Duration.ofSeconds(3)).strip()))throw new IOException("Restoreprobe nicht sauber beendet");}
        }
    }
    @Override public byte[] readTestProperties() throws Exception {
        verifyTarget();var command=prefix();command.addAll(List.of("exec","--user","1000","--",properties.container(),"/bin/cat","/data/server.properties"));return runBytes(command,Duration.ofSeconds(3),1_048_576);
    }
    @Override public java.util.Set<String> managedTestProperties() throws Exception {
        verifyTarget();var command=prefix();command.addAll(List.of("inspect","--format","{{json .Config.Env}}","--",properties.container()));
        var env=mapper.readTree(new String(runBytes(command,Duration.ofSeconds(3),131_072),StandardCharsets.UTF_8));
        java.util.Set<String> keys=new java.util.HashSet<>();boolean override=true;
        for(var item:env){String entry=item.asText();int at=entry.indexOf('=');if(at<1)continue;String key=entry.substring(0,at);keys.add(key);if(key.equals("OVERRIDE_SERVER_PROPERTIES") && entry.substring(at+1).equalsIgnoreCase("false"))override=false;}
        if(!override)return java.util.Set.of();
        var known=java.util.Map.of("DIFFICULTY","difficulty","MODE","gamemode","PVP","pvp","SPAWN_PROTECTION","spawn-protection","MAX_PLAYERS","max-players","VIEW_DISTANCE","view-distance","SIMULATION_DISTANCE","simulation-distance","MOTD","motd");
        java.util.Set<String> managed=new java.util.HashSet<>();known.forEach((key,value)->{if(keys.contains(key))managed.add(value);});return java.util.Set.copyOf(managed);
    }
    @Override public void writeTestProperties(byte[] content) throws Exception {
        if(content==null || content.length>1_048_576)throw new IOException();verifyTarget();
        String stage="/data/.portal-properties-"+java.util.UUID.randomUUID();
        var command=prefix();command.addAll(List.of("exec","--interactive","--user","1000","--",properties.container(),"/bin/dd","of="+stage,"oflag=nofollow","conv=excl,fsync"));
        var process=new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        try(var pool=Executors.newVirtualThreadPerTaskExecutor()){
            var writer=pool.submit(()->{try(var output=process.getOutputStream()){output.write(content);}return true;});
            try{writer.get(5,TimeUnit.SECONDS);if(!process.waitFor(5,TimeUnit.SECONDS)||process.exitValue()!=0)throw new IOException();}finally{process.destroyForcibly();writer.cancel(true);}
        }
        verifyTarget();command=prefix();command.addAll(List.of("exec","--user","1000","--",properties.container(),"/bin/mv","--",stage,"/data/server.properties"));run(command,Duration.ofSeconds(3));
    }
    @Override public String readTestMods() throws Exception {
        verifyTarget();var command=prefix();command.addAll(List.of("exec","--user","1000","--",properties.container(),"/usr/bin/find","/data/mods","-maxdepth","1","-type","f","-name","*.jar","-printf","%f\\t%s\\n"));return new String(runBytes(command,Duration.ofSeconds(3),65_536),StandardCharsets.UTF_8);
    }
    @Override public synchronized RuntimeProfile readTestRuntimeProfile()throws Exception{
        verifyTarget();var command=prefix();command.addAll(List.of("inspect","--format","{{.State.StartedAt}}","--",properties.container()));String started=run(command,Duration.ofSeconds(3)).strip();java.time.Instant.parse(started);
        if(started.equals(profileStart)&&runtimeProfile!=null)return runtimeProfile;
        command=prefix();command.addAll(List.of("logs","--since",started,"--tail","5000","--",properties.container()));String logs=new String(runBytes(command,Duration.ofSeconds(3),524_288),StandardCharsets.UTF_8);
        var matcher=java.util.regex.Pattern.compile("Loading Minecraft ([A-Za-z0-9._+\\-]{1,64}) with Fabric Loader ([A-Za-z0-9._+\\-]{1,64})").matcher(logs);RuntimeProfile found=null;
        while(matcher.find())found=new RuntimeProfile(matcher.group(1),"FABRIC",matcher.group(2));
        profileStart=started;runtimeProfile=found;return found;
    }
    @Override public String readTestJavaVersion()throws Exception{
        verifyTarget();var command=prefix();command.addAll(List.of("exec","--user","1000","--",properties.container(),"java","--version"));
        String raw=new String(runBytes(command,Duration.ofSeconds(5),16384),StandardCharsets.UTF_8);
        var matcher=java.util.regex.Pattern.compile("^(?:openjdk|java) ([0-9]+(?:\\.[0-9]+)*)(?:[ +\\-]|$)").matcher(raw);if(!matcher.find())throw new IOException("Java-Laufzeit nicht bestätigt");return matcher.group(1);
    }
    @Override public java.util.Map<String,String> readTestModHashes()throws Exception{
        verifyTarget();var command=prefix();command.addAll(List.of("exec","--user","1000","--",properties.container(),"/usr/bin/find","/data/mods","-maxdepth","1","-type","f","-name","*.jar","-exec","/usr/bin/sha256sum","--","{}","+"));
        String raw=new String(runBytes(command,Duration.ofSeconds(8),131072),StandardCharsets.UTF_8);java.util.Map<String,String> result=new java.util.TreeMap<>();
        for(String line:raw.lines().toList()){var matcher=java.util.regex.Pattern.compile("([a-f0-9]{64})  /data/mods/([A-Za-z0-9_.+ -]{1,180}\\.jar)").matcher(line);if(!matcher.matches()||result.size()>=500||result.put(matcher.group(2),matcher.group(1))!=null)throw new IOException("Modinventar nicht eindeutig");}return java.util.Map.copyOf(result);
    }
    @Override public byte[] readTestModManifest()throws Exception{
        verifyTarget();var command=prefix();command.addAll(List.of("exec","--user","1000","--",properties.container(),"/usr/bin/find","/data","-maxdepth","1","-name","portal-mod-manifest.json","-printf","%y"));String type=new String(runBytes(command,Duration.ofSeconds(3),16),StandardCharsets.UTF_8);if(type.isEmpty())return new byte[0];if(!type.equals("f"))throw new IOException("Modmanifest ist keine reguläre Datei");
        command=prefix();command.addAll(List.of("exec","--user","1000","--",properties.container(),"/bin/cat","/data/portal-mod-manifest.json"));byte[] content=runBytes(command,Duration.ofSeconds(3),262144);if(content.length==0)throw new IOException("Leeres Modmanifest benötigt Prüfung");return content;
    }
    private byte[] runBytes(List<String> command, Duration timeout, int limit) throws Exception {
        Process process = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        try (var readers = Executors.newVirtualThreadPerTaskExecutor()) {
            var result = readers.submit(() -> process.getInputStream().readNBytes(limit + 1));
            long deadline = System.nanoTime() + timeout.toNanos();
            try {
                byte[] bytes = result.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
                long left = deadline - System.nanoTime();
                if (bytes.length > limit || left <= 0 || !process.waitFor(left, TimeUnit.NANOSECONDS) || process.exitValue() != 0) throw new IOException("Test Docker operation failed");
                return bytes;
            } finally { process.destroyForcibly(); process.getInputStream().close(); result.cancel(true); }
        }
    }
}
