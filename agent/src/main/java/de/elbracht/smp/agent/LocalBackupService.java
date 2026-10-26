package de.elbracht.smp.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Local encrypted test repository only. No cloud endpoints or production world paths accepted. */
@Service @EnableConfigurationProperties(BackupProperties.class)
public class LocalBackupService {
    public record Snapshot(String id,String createdAt,String integrity,String restoreVerifiedAt,String scope,String bootVerifiedAt) {}
    private final BackupProperties properties;private final DockerControl control;private final ContainerProbe docker;private final MinecraftProbe minecraft;private final JdbcTemplate jdbc;private final ObjectMapper mapper;
    @org.springframework.beans.factory.annotation.Autowired private BackupRetention retention;
    public LocalBackupService(BackupProperties properties,AgentProperties agent,DockerControl control,ContainerProbe docker,MinecraftProbe minecraft,JdbcTemplate jdbc,ObjectMapper mapper) {
        this.properties=properties;this.control=control;this.docker=docker;this.minecraft=minecraft;this.jdbc=jdbc;this.mapper=mapper;
        if(properties.enabled())try {
            if(!"docker-test-control".equals(agent.mode()) || properties.executable()==null || !Files.isRegularFile(properties.executable())
                || properties.repository()==null || properties.workspace()==null || properties.passwordFile()==null || !Files.isRegularFile(properties.passwordFile()))throw new IOException();
            Path repo=properties.repository().toRealPath(),work=properties.workspace().toRealPath(),secret=properties.passwordFile().toRealPath();
            if(!Files.isDirectory(work) || !Files.isRegularFile(repo.resolve("config")) || repo.startsWith(work) || work.startsWith(repo) || secret.startsWith(work) || secret.startsWith(repo))throw new IOException();
        }catch(Exception error){throw new IllegalStateException("Lokale Testbackup-Pfade fehlen oder überlappen");}
    }
    public boolean available(){return properties.enabled();}
    public boolean bootTestsAvailable(){return available()&&properties.bootTestEnabled();}
    public List<Snapshot> snapshots(){return jdbc.query("SELECT * FROM backup_snapshot WHERE integrity='READ_DATA_OK' ORDER BY created_at DESC",(rs,n)->new Snapshot(rs.getString("snapshot_id"),rs.getString("created_at"),rs.getString("integrity"),rs.getString("restore_verified_at"),rs.getString("scope"),rs.getString("boot_verified_at")));}
    public String create(UUID operation) throws Exception {
        return create(operation,false);
    }
    public String createBeforeRestore(UUID operation)throws Exception{return create(operation,true);}
    private String create(UUID operation,boolean keepStopped) throws Exception {
        if(!available())throw new IOException();
        control.verifyTarget();var before=docker.inspect();if(!before.targetMatches() || before.paused() || before.restarting() || before.dead())throw new IOException();
        boolean wasRunning=before.running();var profile=control.readTestRuntimeProfile();Path stage=fresh("backup-",operation);
        String snapshot=null;boolean staged=false;
        try {
            if(wasRunning)control.stop();
            var stopped=docker.inspect();if(!stopped.targetMatches() || stopped.running() || !"exited".equals(stopped.status()))throw new IOException();
            control.verifyCleanTestStop();
            control.copyTestWorld(stage);removeOperationalCopies(stage);validateWorld(stage);
            if(profile==null)throw new IOException("Laufzeitprofil für vollständige Sicherung fehlt");
            Files.writeString(stage.resolve(".portal-snapshot.json"),mapper.writeValueAsString(Map.of("schema",1,"scope","LOCAL_ENCRYPTED_FULL_MINECRAFT_DATA","minecraftVersion",profile.minecraftVersion(),"loader",profile.loader(),"loaderVersion",profile.loaderVersion(),"image","itzg/minecraft-server@sha256:6fd6f9bb6998b7cd06948ec6ab03fabfdf98ba3f2ec030c44069bec9a6bdf09d")),StandardCharsets.UTF_8);
            staged=true;
        }finally{
            if(wasRunning&&(!keepStopped||!staged)){control.start();awaitReady();}
        }
        for(String line:run(stage,List.of("backup","--json","--tag","smp:main","--tag","operation:"+operation,".")).lines().toList()){
            var node=mapper.readTree(line);if("summary".equals(node.path("message_type").asText()))snapshot=node.path("snapshot_id").asText();
        }
        if(snapshot==null || !snapshot.matches("[a-f0-9]{64}"))throw new IOException("Kein bestätigter Snapshot");
        run(stage,List.of("check","--read-data"));
        var now=Instant.now().toString();jdbc.update("INSERT INTO backup_snapshot (snapshot_id,created_at,integrity,scope) VALUES (?,?,'READ_DATA_OK','LOCAL_ENCRYPTED_FULL_MINECRAFT_DATA')",snapshot,now);
        if(!keepStopped && properties.retentionEnabled())retention.afterSuccessfulBackup(operation,snapshot);
        return snapshot;
    }
    Path stageRestorePlan(UUID plan,String snapshot)throws Exception{
        if(!available()||snapshot==null||!snapshot.matches("[a-f0-9]{64}")||jdbc.queryForObject("SELECT COUNT(*) FROM backup_snapshot WHERE snapshot_id=? AND integrity='READ_DATA_OK' AND scope='LOCAL_ENCRYPTED_FULL_MINECRAFT_DATA' AND boot_verified_at IS NOT NULL",Integer.class,snapshot)!=1)throw new IOException("Kein vollständig geprüfter Minecraft-Snapshot");
        Path target=fresh("plan-",plan);try{run(target,List.of("restore",snapshot,"--target",target.toString(),"--verify"));validateWorld(target);return target;}catch(Exception error){discardPlanDirectory(plan);throw error;}
    }
    Path planDirectory(UUID id)throws IOException{Path root=properties.workspace().toRealPath();Path path=root.resolve("plan-"+id).toRealPath();if(!path.getParent().equals(root))throw new IOException();return path;}
    Path newModPlanDirectory(UUID id)throws IOException{return fresh("mod-plan-",id);}
    Path modPlanDirectory(UUID id)throws IOException{Path root=properties.workspace().toRealPath(),path=root.resolve("mod-plan-"+id).toRealPath();if(!path.getParent().equals(root))throw new IOException();return path;}
    Path stoppedModStage(UUID operation)throws IOException{Path root=properties.workspace().toRealPath(),path=root.resolve("backup-"+operation).toRealPath();if(!path.getParent().equals(root))throw new IOException();validateWorld(path);return path;}
    void verifyModStage(Path stage,UUID operation)throws Exception{
        Path target=fresh("restore-",operation);validateWorld(stage);try(var paths=Files.walk(stage)){for(Path path:paths.toList()){if(path.equals(stage))continue;Path to=target.resolve(stage.relativize(path));if(!to.normalize().startsWith(target))throw new IOException();if(Files.isDirectory(path,LinkOption.NOFOLLOW_LINKS))Files.createDirectory(to);else Files.copy(path,to);}}
        control.bootTestRestore(target,operation);
    }
    void discardPlanDirectory(UUID id)throws IOException{
        discardPrivateDirectory("plan-",id);
    }
    void discardModPlanDirectory(UUID id)throws IOException{discardPrivateDirectory("mod-plan-",id);}
    private void discardPrivateDirectory(String prefix,UUID id)throws IOException{
        if(!Set.of("plan-","mod-plan-").contains(prefix))throw new IOException();Path root=properties.workspace().toRealPath(),target=root.resolve(prefix+id).toAbsolutePath().normalize();if(!target.getParent().equals(root))throw new IOException();
        if(!Files.exists(target,LinkOption.NOFOLLOW_LINKS))return;
        try(var paths=Files.walk(target)){for(Path path:paths.sorted(Comparator.reverseOrder()).toList()){
            if(!path.toAbsolutePath().normalize().startsWith(target))throw new IOException();
            if(!Files.isSymbolicLink(path)&&Files.getFileStore(path).supportsFileAttributeView("dos"))Files.setAttribute(path,"dos:readonly",false,LinkOption.NOFOLLOW_LINKS);
            Files.delete(path);
        }}
    }
    static void removeOperationalCopies(Path stage)throws IOException{
        Path root=stage.toRealPath();try(var entries=Files.list(root)){for(Path entry:entries.toList()){
            String name=entry.getFileName().toString();if(!name.matches("\\.portal-(?:next|previous|failed)-[a-f0-9-]{36}"))continue;
            Path target=entry.toAbsolutePath().normalize();if(!target.getParent().equals(root))throw new IOException();
            // Only copies inside this fresh staging directory, never the live data volume.
            try(var paths=Files.walk(target)){for(Path path:paths.sorted(Comparator.reverseOrder()).toList()){if(!path.toAbsolutePath().normalize().startsWith(root))throw new IOException();Files.delete(path);}}
        }}
    }
    public String verifyRestore(UUID operation,String snapshot) throws Exception {
        if(!available() || snapshot==null || !snapshot.matches("[a-f0-9]{64}") || jdbc.queryForObject("SELECT COUNT(*) FROM backup_snapshot WHERE snapshot_id=? AND integrity='READ_DATA_OK'",Integer.class,snapshot)!=1)throw new IOException("Snapshot nicht bestätigt");
        Path target=fresh("restore-",operation);
        run(target,List.of("restore",snapshot,"--target",target.toString(),"--verify"));
        validateWorld(target);
        jdbc.update("UPDATE backup_snapshot SET restore_verified_at=? WHERE snapshot_id=?",Instant.now().toString(),snapshot);
        if(properties.bootTestEnabled()&&Files.isRegularFile(target.resolve(".portal-snapshot.json"))){
            var proof=mapper.readTree(Files.readAllBytes(target.resolve(".portal-snapshot.json")));if(!"26.3".equals(proof.path("minecraftVersion").asText())||!"FABRIC".equals(proof.path("loader").asText())||!"0.19.5".equals(proof.path("loaderVersion").asText())||!TestDockerControl.TEST_IMAGE.equals(proof.path("image").asText()))throw new IOException("Restore-Laufzeitprofil nicht freigegeben");
            control.bootTestRestore(target,operation);jdbc.update("UPDATE backup_snapshot SET boot_verified_at=? WHERE snapshot_id=?",Instant.now().toString(),snapshot);
            return "Vollständige Minecraft-Daten isoliert und mit Restic verifiziert wiederhergestellt. Weltstart mit Minecraft 26.3/Fabric 0.19.5 ohne Netzverbindung und ohne veröffentlichte Ports bestätigt; Probe danach sauber gestoppt. Laufende Ursprungswelt nicht ersetzt.";
        }
        return "Dateien in isoliertes Verzeichnis wiederhergestellt und mit Restic verifiziert. Kein Weltstart und kein Austausch einer laufenden Welt.";
    }
    private Path fresh(String prefix,UUID id)throws IOException {
        Path root=properties.workspace().toRealPath();Path stage=root.resolve(prefix+id).normalize();
        if(!stage.getParent().equals(root) || Files.exists(stage,LinkOption.NOFOLLOW_LINKS))throw new IOException();
        return Files.createDirectory(stage);
    }
    static void validateWorld(Path root)throws IOException {
        if(!Files.isRegularFile(root.resolve("world/level.dat"),LinkOption.NOFOLLOW_LINKS))throw new IOException("Weltmetadaten fehlen");
        for(String file:List.of("whitelist.json","ops.json"))if(!Files.isRegularFile(root.resolve(file),LinkOption.NOFOLLOW_LINKS))throw new IOException();
        try(var paths=Files.walk(root)){var iterator=paths.iterator();int count=0;while(iterator.hasNext()){Path path=iterator.next();if(++count>100_000||Files.isSymbolicLink(path) || (!Files.isDirectory(path,LinkOption.NOFOLLOW_LINKS) && !Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)))throw new IOException("Unsicherer Dateityp oder zu viele Dateien");}}
    }
    private void awaitReady()throws Exception {
        long deadline=System.nanoTime()+Duration.ofSeconds(90).toNanos();
        while(System.nanoTime()<deadline){try{var state=docker.inspect();if(state.targetMatches() && state.running() && !state.paused() && !state.restarting() && !state.dead() && state.portMatches()){minecraft.query();return;}}catch(IOException ignored){}Thread.sleep(500);}
        throw new IOException("Testserver nicht wieder bereit");
    }
    private String run(Path directory,List<String> arguments)throws Exception {
        return ResticCommand.local(properties,directory,arguments);
    }
}
