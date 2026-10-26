package de.elbracht.smp.agent;

import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class OperationService {
    private final AgentProperties properties;
    private final OperationStore store;
    private final DockerControl control;
    private final ContainerProbe docker;
    private final MinecraftProbe minecraft;
    private final TestWhitelistControl whitelist;
    @org.springframework.beans.factory.annotation.Value("${agent.test-console-enabled:false}") private boolean consoleEnabled;
    private final RconPlayerReader rcon;
    private final RconProperties rconProperties;
    @org.springframework.beans.factory.annotation.Autowired private LocalBackupService backups;
    @org.springframework.beans.factory.annotation.Autowired private TestConfigurationService configuration;
    @org.springframework.beans.factory.annotation.Autowired private com.fasterxml.jackson.databind.ObjectMapper mapper;
    @org.springframework.beans.factory.annotation.Autowired private RestoreService restore;
    @org.springframework.beans.factory.annotation.Autowired private ModPlanService mods;
    @org.springframework.beans.factory.annotation.Autowired
    public OperationService(AgentProperties properties, OperationStore store, DockerControl control, ContainerProbe docker, MinecraftProbe minecraft, TestWhitelistControl whitelist,
                            RconPlayerReader rcon, RconProperties rconProperties) {
        this.properties = properties; this.store = store; this.control = control; this.docker = docker; this.minecraft = minecraft;
        this.whitelist = whitelist; this.rcon = rcon; this.rconProperties = rconProperties;
    }
    public OperationService(AgentProperties properties, OperationStore store, DockerControl control, ContainerProbe docker, MinecraftProbe minecraft, TestWhitelistControl whitelist) {
        this(properties, store, control, docker, minecraft, whitelist, null, null);
    }
    /** Genau eine Agentinstanz pro Ledger/Testserver. Keine automatische Wiederholung unbekannter Ergebnisse. */
    public synchronized OperationStore.Result execute(UUID id, OperationStore.Type type) {
        return execute(id, type, null);
    }
    public synchronized OperationStore.Result execute(UUID id, OperationStore.Type type, String targetName) {
        if (type == OperationStore.Type.CONSOLE || type == OperationStore.Type.BACKUP || type == OperationStore.Type.RESTORE_TEST || type==OperationStore.Type.CONFIG_APPLY || type==OperationStore.Type.RESTORE_APPLY || type==OperationStore.Type.MOD_APPLY) throw new OperationRejected(422, "SPECIAL_PAYLOAD_REQUIRED");
        if (!"docker-test-control".equals(properties.mode())) throw new OperationRejected(403, "TEST_CONTROL_DISABLED");
        if (type == null || (type.playerOperation() ? targetName == null || !targetName.matches("[A-Za-z0-9_]{3,16}") : targetName != null))
            throw new OperationRejected(422, "INVALID_PLAYER_TARGET");
        var existing = store.find(id);
        if (existing != null) {
            if (existing.type() != type || !java.util.Objects.equals(existing.targetName(), targetName)) throw new OperationRejected(409, "OPERATION_ID_CONFLICT");
            return existing;
        }
        if (type.operatorOperation() ? !whitelist.operatorsAvailable() : type.playerOperation() && !whitelist.available())
            throw new OperationRejected(503, "TEST_WHITELIST_DISABLED");
        if (type.playerOperation()) store.begin(id, type, targetName);
        else store.begin(id, type);
        try {
            control.verifyTarget();
            if (type.playerOperation()) {
                var current = docker.inspect();
                if (!current.targetMatches() || !current.running() || current.paused() || current.restarting()
                        || current.dead() || !current.portMatches()) throw new IllegalStateException();
                whitelist.apply(type, targetName);
                return store.finish(id, type, "SUCCEEDED");
            }
            if (type == OperationStore.Type.STOP || type == OperationStore.Type.RESTART) {
                control.stop();
                var stopped = docker.inspect();
                if (!stopped.targetMatches() || stopped.running() || !stopped.status().equals("exited")) throw new IllegalStateException();
            }
            if (type != OperationStore.Type.STOP) {
                control.start();
                long deadline = System.nanoTime() + java.time.Duration.ofSeconds(12).toNanos();
                boolean ready = false;
                while (System.nanoTime() < deadline) {
                    try {
                        var current = docker.inspect();
                        if (current.targetMatches() && current.running() && !current.paused() && !current.restarting() && !current.dead() && current.portMatches()) {
                            minecraft.query(); ready = true; break;
                        }
                    } catch (java.io.IOException ignored) { }
                    Thread.sleep(250);
                }
                if (!ready) throw new IllegalStateException();
            }
            return store.finish(id, type, "SUCCEEDED");
        } catch (Exception error) {
            if (error instanceof InterruptedException) Thread.currentThread().interrupt();
            return store.finish(id, type, "UNKNOWN");
        }
    }
    public synchronized OperationStore.Result console(UUID id, String command) {
        if (!"docker-test-control".equals(properties.mode()) || !consoleEnabled || rconProperties == null || !"test-control".equals(rconProperties.mode()))
            throw new OperationRejected(503, "TEST_CONSOLE_DISABLED");
        if (command == null || command.isBlank() || command.length() > 512 || !command.equals(command.strip())
            || command.chars().anyMatch(c -> c < 32 || c == 127) || command.startsWith("/")
            || java.util.Set.of("op", "deop", "whitelist", "stop", "reload", "datapack", "save-off").contains(command.split(" ",2)[0].toLowerCase(java.util.Locale.ROOT)))
            throw new OperationRejected(422, "INVALID_COMMAND");
        var existing = store.find(id);
        if (existing != null) {
            if (existing.type() != OperationStore.Type.CONSOLE || !command.equals(store.command(id))) throw new OperationRejected(409, "OPERATION_ID_CONFLICT");
            return existing;
        }
        store.begin(id, OperationStore.Type.CONSOLE); store.consolePayload(id, command);
        try {
            control.verifyRconTarget(rconProperties.port());
            var target = docker.inspect();
            if (!target.targetMatches() || !target.running() || target.paused() || target.dead() || target.restarting() || !target.portMatches()) throw new IllegalStateException();
            store.consoleResult(id, rcon.testConsole(command));
            return store.finish(id, OperationStore.Type.CONSOLE, "SUCCEEDED");
        } catch (Exception error) {
            if (error instanceof InterruptedException) Thread.currentThread().interrupt();
            return store.finish(id, OperationStore.Type.CONSOLE, "UNKNOWN");
        }
    }
    public synchronized OperationStore.Result backup(UUID id,OperationStore.Type type,String snapshot) {
        if(!"docker-test-control".equals(properties.mode()) || backups==null || !backups.available())throw new OperationRejected(503,"TEST_BACKUP_DISABLED");
        if(type!=OperationStore.Type.BACKUP && type!=OperationStore.Type.RESTORE_TEST || (type==OperationStore.Type.RESTORE_TEST ? snapshot==null || !snapshot.matches("[a-f0-9]{64}") : snapshot!=null))throw new OperationRejected(422,"INVALID_SNAPSHOT");
        var existing=store.find(id);if(existing!=null){if(existing.type()!=type || !java.util.Objects.equals(snapshot,store.snapshot(id)))throw new OperationRejected(409,"OPERATION_ID_CONFLICT");return existing;}
        store.begin(id,type);store.snapshotTarget(id,snapshot);
        try {String result=type==OperationStore.Type.BACKUP ? backups.create(id) : backups.verifyRestore(id,snapshot);store.consoleResult(id,result);return store.finish(id,type,"SUCCEEDED");}
        catch(Exception error){if(error instanceof InterruptedException)Thread.currentThread().interrupt();return store.finish(id,type,"UNKNOWN");}
    }
    public synchronized OperationStore.Result configuration(UUID id,String revision,java.util.Map<String,String> values){
        if(!"docker-test-control".equals(properties.mode())||configuration==null||!configuration.available())throw new OperationRejected(503,"TEST_CONFIGURATION_DISABLED");
        final String payload;
        try{TestConfigurationService.validate(values);if(revision==null||!revision.matches("[a-f0-9]{64}"))throw new IllegalArgumentException();payload=mapper.writeValueAsString(java.util.Map.of("revision",revision,"values",new java.util.TreeMap<>(values)));}
        catch(Exception error){throw new OperationRejected(422,"INVALID_CONFIGURATION");}
        var existing=store.find(id);if(existing!=null){if(existing.type()!=OperationStore.Type.CONFIG_APPLY||!payload.equals(store.configuration(id)))throw new OperationRejected(409,"OPERATION_ID_CONFLICT");return existing;}
        store.begin(id,OperationStore.Type.CONFIG_APPLY);store.configurationTarget(id,payload);
        try{store.consoleResult(id,configuration.apply(revision,values));return store.finish(id,OperationStore.Type.CONFIG_APPLY,"SUCCEEDED");}
        catch(Exception error){if(error instanceof InterruptedException)Thread.currentThread().interrupt();return store.finish(id,OperationStore.Type.CONFIG_APPLY,"UNKNOWN");}
    }
    /** Explicit read-only reconciliation; no operation is retried and no caller chooses the outcome. */
    public synchronized OperationStore.Result reconcile(UUID id){
        if(!"docker-test-control".equals(properties.mode()))throw new OperationRejected(403,"TEST_CONTROL_DISABLED");
        var existing=store.find(id);if(existing==null)throw new OperationRejected(404,"OPERATION_NOT_FOUND");
        if(!"UNKNOWN".equals(existing.state()))return existing;
        if(existing.type()!=OperationStore.Type.CONFIG_APPLY)return existing;
        try{
            control.verifyTarget();var request=mapper.readTree(store.configuration(id));var actual=configuration.read();
            boolean matches=true;var values=request.path("values");if(!values.isObject()||values.isEmpty())return existing;
            var keys=values.fieldNames();while(keys.hasNext()){String key=keys.next();boolean found=false;for(var field:actual.fields())if(key.equals(field.key())&&values.path(key).asText().equals(field.value()))found=true;matches&=found;}
            if(matches)return store.reconcile(id,"SUCCEEDED","Abgleich: Die angeforderten Eigenschaften sind in der tatsächlichen Datei vorhanden. Ein Neustart kann weiterhin erforderlich sein.");
            if(actual.revision().equals(request.path("revision").asText()))return store.reconcile(id,"FAILED","Abgleich: Die tatsächliche Datei entspricht der Ausgangsrevision. Die angeforderte Änderung ist aktuell nicht vorhanden.");
            return existing;
        }catch(Exception error){if(error instanceof InterruptedException)Thread.currentThread().interrupt();return existing;}
    }
    public synchronized OperationStore.Result restore(UUID id,UUID plan){
        if(!"docker-test-control".equals(properties.mode())||restore==null||!restore.available())throw new OperationRejected(503,"TEST_RESTORE_DISABLED");if(plan==null)throw new OperationRejected(422,"RESTORE_PLAN_REQUIRED");
        var existing=store.find(id);if(existing!=null){if(existing.type()!=OperationStore.Type.RESTORE_APPLY||!plan.equals(store.restoreTarget(id)))throw new OperationRejected(409,"OPERATION_ID_CONFLICT");return existing;}
        store.begin(id,OperationStore.Type.RESTORE_APPLY);store.restoreTarget(id,plan);
        try{var outcome=restore.apply(plan,id);store.consoleResult(id,outcome.result());return store.finish(id,OperationStore.Type.RESTORE_APPLY,outcome.state());}
        catch(OperationRejected error){store.consoleResult(id,"Wiederherstellung vor dem Datenwechsel abgewiesen: Plan ist abgelaufen, geändert oder nicht verfügbar.");return store.finish(id,OperationStore.Type.RESTORE_APPLY,"FAILED");}
        catch(Exception error){if(error instanceof InterruptedException)Thread.currentThread().interrupt();return store.finish(id,OperationStore.Type.RESTORE_APPLY,"UNKNOWN");}
    }
    public synchronized OperationStore.Result mods(UUID id,UUID plan){
        if(!"docker-test-control".equals(properties.mode())||mods==null||!mods.available())throw new OperationRejected(503,"TEST_MODS_DISABLED");if(plan==null)throw new OperationRejected(422,"MOD_PLAN_REQUIRED");var existing=store.find(id);if(existing!=null){if(existing.type()!=OperationStore.Type.MOD_APPLY||!plan.equals(store.modTarget(id)))throw new OperationRejected(409,"OPERATION_ID_CONFLICT");return existing;}
        store.begin(id,OperationStore.Type.MOD_APPLY);store.modTarget(id,plan);try{var outcome=mods.apply(plan,id);store.consoleResult(id,outcome.result());return store.finish(id,OperationStore.Type.MOD_APPLY,outcome.state());}
        catch(OperationRejected error){store.consoleResult(id,"Modänderung vor dem Datenwechsel abgewiesen: Plan, Laufzeit oder Modinventar nicht mehr bestätigt.");return store.finish(id,OperationStore.Type.MOD_APPLY,"FAILED");}
        catch(Exception error){if(error instanceof InterruptedException)Thread.currentThread().interrupt();return store.finish(id,OperationStore.Type.MOD_APPLY,"UNKNOWN");}
    }
}
