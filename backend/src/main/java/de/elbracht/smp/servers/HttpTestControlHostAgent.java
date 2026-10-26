package de.elbracht.smp.servers;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.elbracht.smp.operations.OperationType;
import java.net.*;
import java.net.http.*;
import java.nio.file.Files;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Nur für das explizite lokale Testserverprofil, niemals für Produktion. */
public final class HttpTestControlHostAgent implements HostAgent, AutoCloseable {
    private final AgentProperties properties;
    private final ObjectMapper mapper;
    private final HttpStatusHostAgent reader;
    private final HttpClient client;
    private final String authorization;
    private final boolean whitelistEnabled;
    private boolean operatorEnabled;
    private boolean consoleEnabled;
    private boolean backupsEnabled;
    private boolean configurationEnabled;
    private boolean restoreEnabled;
    private boolean modsEnabled;
    public HttpTestControlHostAgent withMods(boolean enabled){this.modsEnabled=enabled;return this;}
    public boolean modsAvailable(){return modsEnabled;}
    public com.fasterxml.jackson.databind.JsonNode modRpc(String server,String path,String method,Map<String,?> body,Duration timeout){
        boolean plan=path.matches("/mods/plans/[a-f0-9-]{36}");boolean allowed=method.equals("GET")&&(path.equals("/mods/state")||plan)||method.equals("DELETE")&&plan||method.equals("POST")&&(path.equals("/mods/plans")||path.equals("/operations")&&body!=null&&"MOD_APPLY".equals(body.get("type")));
        if(!modsEnabled||!properties.serverId().equals(server)||!allowed||timeout.isNegative()||timeout.compareTo(Duration.ofMinutes(10))>0)throw new IllegalStateException("Modverwaltung deaktiviert oder Anfrage ungültig");java.util.concurrent.CompletableFuture<HttpResponse<byte[]>> pending=null;
        try{byte[] payload=body==null?null:mapper.writeValueAsBytes(body);if(payload!=null&&payload.length>12*1024*1024)throw new IllegalStateException();var request=HttpRequest.newBuilder(properties.baseUrl().resolve("/agent/v1/servers/"+server+path)).timeout(timeout).header("Authorization",authorization).header("Content-Type","application/json").method(method,payload==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofByteArray(payload)).build();pending=client.sendAsync(request,info->new LimitedBodySubscriber(131072));var response=pending.get(timeout.toMillis(),TimeUnit.MILLISECONDS);if(response.statusCode()!=200||!response.headers().firstValue("Content-Type").orElse("").split(";",2)[0].strip().equalsIgnoreCase("application/json"))throw new IllegalStateException();return mapper.readTree(response.body());}
        catch(Exception error){if(error instanceof InterruptedException)Thread.currentThread().interrupt();throw new IllegalStateException("Modantwort nicht bestätigt");}finally{if(pending!=null&&!pending.isDone())pending.cancel(true);}
    }
    public String applyMods(String server,UUID operation,UUID plan){
        var node=modRpc(server,"/operations","POST",Map.of("operationId",operation,"type","MOD_APPLY","modPlanId",plan),Duration.ofMinutes(10));if(!operation.toString().equals(node.path("operationId").asText())||!"MOD_APPLY".equals(node.path("type").asText())||!node.path("resultText").isTextual()||node.path("resultText").asText().length()>16384)throw new IllegalStateException();String result=node.path("resultText").asText();if(node.path("state").asText().equals("FAILED"))throw new KnownFailure("MOD_CHANGE_FAILED",result);if(!node.path("state").asText().equals("SUCCEEDED")||reader.observe(server).lifecycle()!=Lifecycle.RUNNING)throw new IllegalStateException();return result;
    }
    public HttpTestControlHostAgent withRestoreControl(boolean enabled){this.restoreEnabled=enabled;return this;}
    public boolean restoreAvailable(){return restoreEnabled;}
    public RestorePlan prepareRestore(String server,String snapshot,boolean startAfter){
        if(snapshot==null||!snapshot.matches("[a-f0-9]{64}"))throw new IllegalStateException();var plan=parseRestorePlan(restoreRpc(server,"/restore-plans","POST",Map.of("snapshotId",snapshot,"startAfterRestore",startAfter),Duration.ofSeconds(90)));
        if(!snapshot.equals(plan.snapshotId())||!"PLANNED".equals(plan.state()))throw new IllegalStateException();return plan;
    }
    public RestorePlan restorePlan(String server,UUID plan){var value=parseRestorePlan(restoreRpc(server,"/restore-plans/"+plan,"GET",null,Duration.ofSeconds(10)));if(!plan.equals(value.planId()))throw new IllegalStateException();return value;}
    public void cancelRestore(String server,UUID plan){var value=parseRestorePlan(restoreRpc(server,"/restore-plans/"+plan,"DELETE",null,Duration.ofSeconds(10)));if(!plan.equals(value.planId())||!"CANCELLED".equals(value.state()))throw new IllegalStateException();}
    public String restore(String server,UUID operation,UUID plan){
        var expected=restorePlan(server,plan);
        var node=restoreRpc(server,"/operations","POST",Map.of("operationId",operation,"type","RESTORE_APPLY","restorePlanId",plan),Duration.ofMinutes(8));
        if(!operation.toString().equals(node.path("operationId").asText())||!"RESTORE_APPLY".equals(node.path("type").asText())||!node.path("resultText").isTextual()||node.path("resultText").asText().length()>16384)throw new IllegalStateException();
        String state=node.path("state").asText(),result=node.path("resultText").asText();if("FAILED".equals(state))throw new KnownFailure("RESTORE_FAILED_WITH_ORIGINAL_STATE",result);if(!"SUCCEEDED".equals(state))throw new IllegalStateException();
        if(reader.observe(server).lifecycle()!=(expected.startAfterRestore()?Lifecycle.RUNNING:Lifecycle.OFFLINE))throw new IllegalStateException();return result;
    }
    private RestorePlan parseRestorePlan(com.fasterxml.jackson.databind.JsonNode node){
        try{var plan=mapper.treeToValue(node,RestorePlan.class);
            if(plan==null||plan.planId()==null||plan.snapshotId()==null||!plan.snapshotId().matches("[a-f0-9]{64}")||plan.components()==null||plan.components().isEmpty()||plan.components().size()>10||plan.components().stream().anyMatch(value->value==null||value.length()>100)||!java.util.Set.of("PLANNED","APPLYING","SUCCEEDED","ROLLED_BACK","UNKNOWN","CANCELLED","EXPIRED").contains(plan.state()))throw new IllegalStateException();
            java.time.Instant.parse(plan.createdAt());java.time.Instant.parse(plan.expiresAt());return plan;
        }catch(Exception error){throw new IllegalStateException("Restoreplan nicht bestätigt");}
    }
    private com.fasterxml.jackson.databind.JsonNode restoreRpc(String server,String path,String method,Map<String,?> body,Duration timeout){
        if(!restoreEnabled||!properties.serverId().equals(server))throw new IllegalStateException("Wiederherstellung deaktiviert");java.util.concurrent.CompletableFuture<HttpResponse<byte[]>> pending=null;
        try{var request=HttpRequest.newBuilder(properties.baseUrl().resolve("/agent/v1/servers/"+server+path)).timeout(timeout).header("Authorization",authorization).header("Content-Type","application/json").method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofByteArray(mapper.writeValueAsBytes(body))).build();
            pending=client.sendAsync(request,info->new LimitedBodySubscriber(32_768));var response=pending.get(timeout.toMillis(),TimeUnit.MILLISECONDS);
            if(response.statusCode()!=200||!response.headers().firstValue("Content-Type").orElse("").split(";",2)[0].strip().equalsIgnoreCase("application/json"))throw new IllegalStateException();return mapper.readTree(response.body());
        }catch(Exception error){if(error instanceof InterruptedException)Thread.currentThread().interrupt();throw new IllegalStateException("Wiederherstellungsantwort nicht bestätigt");}finally{if(pending!=null&&!pending.isDone())pending.cancel(true);}
    }
    public HttpTestControlHostAgent withConfiguration(boolean enabled){this.configurationEnabled=enabled;return this;}
    public boolean configurationAvailable(){return configurationEnabled;}
    public Reconciled reconcile(String server,UUID id){
        if(!properties.serverId().equals(server))throw new IllegalStateException();java.util.concurrent.CompletableFuture<HttpResponse<byte[]>> pending=null;
        try{var request=HttpRequest.newBuilder(properties.baseUrl().resolve("/agent/v1/servers/"+server+"/operations/"+id+"/reconcile")).timeout(Duration.ofSeconds(12)).header("Authorization",authorization).POST(HttpRequest.BodyPublishers.noBody()).build();
            pending=client.sendAsync(request,info->new LimitedBodySubscriber(32_768));var response=pending.get(12,TimeUnit.SECONDS);if(response.statusCode()!=200)throw new IllegalStateException();var result=mapper.readValue(response.body(),Reconciled.class);if(result==null||!id.equals(result.operationId())||result.type()==null||!java.util.Set.of("SUCCEEDED","FAILED","UNKNOWN").contains(result.state())||result.resultText()!=null&&result.resultText().length()>16384)throw new IllegalStateException();return result;
        }catch(Exception error){if(error instanceof InterruptedException)Thread.currentThread().interrupt();throw new IllegalStateException("Abgleich nicht bestätigt");}finally{if(pending!=null&&!pending.isDone())pending.cancel(true);}
    }
    public String configuration(String server,UUID id,String revision,String configurationJson){
        if(!configurationEnabled||!properties.serverId().equals(server))throw new IllegalStateException();
        java.util.concurrent.CompletableFuture<HttpResponse<byte[]>> pending=null;
        try{
            var payload=Map.of("operationId",id,"type","CONFIG_APPLY","revision",revision,"configuration",mapper.readTree(configurationJson));
            var request=HttpRequest.newBuilder(properties.baseUrl().resolve("/agent/v1/servers/"+server+"/operations")).timeout(Duration.ofSeconds(20)).header("Authorization",authorization).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofByteArray(mapper.writeValueAsBytes(payload))).build();
            pending=client.sendAsync(request,info->new LimitedBodySubscriber(32_768));var response=pending.get(20,TimeUnit.SECONDS);var node=mapper.readTree(response.body());
            if(response.statusCode()!=200||!id.toString().equals(node.path("operationId").asText())||!"CONFIG_APPLY".equals(node.path("type").asText())||!"SUCCEEDED".equals(node.path("state").asText())||!node.path("resultText").isTextual()||node.path("resultText").asText().length()>16384)throw new IllegalStateException();
            return node.path("resultText").asText();
        }catch(Exception error){if(error instanceof InterruptedException)Thread.currentThread().interrupt();throw new IllegalStateException("Konfigurationsergebnis nicht bestätigt");}finally{if(pending!=null&&!pending.isDone())pending.cancel(true);}
    }
    public HttpTestControlHostAgent withBackups(boolean enabled) { this.backupsEnabled=enabled; return this; }
    public boolean backupsAvailable(){return backupsEnabled;}
    public String backup(String server,UUID id,OperationType type,String snapshot) {
        if(!backupsEnabled || !properties.serverId().equals(server) || (type!=OperationType.BACKUP && type!=OperationType.RESTORE_TEST))throw new IllegalStateException();
        java.util.concurrent.CompletableFuture<HttpResponse<byte[]>> pending=null;
        try {
            var payload=new java.util.LinkedHashMap<String,Object>();payload.put("operationId",id);payload.put("type",type);if(snapshot!=null)payload.put("snapshotId",snapshot);
            var request=HttpRequest.newBuilder(properties.baseUrl().resolve("/agent/v1/servers/"+server+"/operations")).timeout(Duration.ofMinutes(8)).header("Authorization",authorization).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofByteArray(mapper.writeValueAsBytes(payload))).build();
            pending=client.sendAsync(request,info->new LimitedBodySubscriber(32_768));var response=pending.get(8,TimeUnit.MINUTES);var node=mapper.readTree(response.body());
            if(response.statusCode()!=200 || !id.toString().equals(node.path("operationId").asText()) || !type.name().equals(node.path("type").asText()) || !"SUCCEEDED".equals(node.path("state").asText()) || !node.path("resultText").isTextual() || node.path("resultText").asText().length()>16384)throw new IllegalStateException();
            return node.path("resultText").asText();
        }catch(Exception error){if(error instanceof InterruptedException)Thread.currentThread().interrupt();throw new IllegalStateException("Backupergebnis nicht bestätigt");}
        finally{if(pending!=null && !pending.isDone())pending.cancel(true);}
    }
    public HttpTestControlHostAgent withConsoleControl(boolean enabled) { this.consoleEnabled = enabled; return this; }
    public boolean consoleAvailable() { return consoleEnabled; }
    public String console(String server, UUID id, String command) {
        if (!consoleEnabled || !properties.serverId().equals(server)) throw new IllegalStateException("Konsole deaktiviert");
        java.util.concurrent.CompletableFuture<HttpResponse<byte[]>> pending = null;
        try {
            var request = HttpRequest.newBuilder(properties.baseUrl().resolve("/agent/v1/servers/" + server + "/operations"))
                .timeout(Duration.ofSeconds(12)).header("Authorization", authorization).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(mapper.writeValueAsBytes(Map.of("operationId", id, "type", "CONSOLE", "commandText", command)))).build();
            pending = client.sendAsync(request, info -> new LimitedBodySubscriber(131_072));
            var response = pending.get(12, TimeUnit.SECONDS); var node = mapper.readTree(response.body());
            if (response.statusCode() != 200 || !id.toString().equals(node.path("operationId").asText())
                || !"CONSOLE".equals(node.path("type").asText()) || !"SUCCEEDED".equals(node.path("state").asText())
                || !node.path("resultText").isTextual() || node.path("resultText").asText().length() > 16384) throw new IllegalStateException();
            return node.path("resultText").asText();
        } catch (Exception error) {
            if (error instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new IllegalStateException("Konsolenergebnis nicht bestätigt");
        } finally { if (pending != null && !pending.isDone()) pending.cancel(true); }
    }
    public HttpTestControlHostAgent withOperatorControl(boolean enabled) { this.operatorEnabled = enabled; return this; }
    public HttpTestControlHostAgent(AgentProperties properties, ObjectMapper mapper) {
        this(properties, mapper, false);
    }
    public HttpTestControlHostAgent(AgentProperties properties, ObjectMapper mapper, boolean whitelistEnabled) {
        this.whitelistEnabled = whitelistEnabled;
        this.properties = properties; this.mapper = mapper.copy().enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        if (properties.baseUrl() == null || !"127.0.0.1".equals(properties.baseUrl().getHost())) throw new IllegalStateException("Teststeuerung nur über Loopback");
        this.reader = new HttpStatusHostAgent(properties, mapper, Clock.systemUTC(), true);
        try { this.authorization = "Bearer " + Files.readString(properties.tokenFile()).strip(); }
        catch (Exception error) { throw new IllegalStateException("Agent-Secret nicht verfügbar"); }
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).followRedirects(HttpClient.Redirect.NEVER)
            .proxy(new ProxySelector() {
                public List<Proxy> select(URI uri) { return List.of(Proxy.NO_PROXY); }
                public void connectFailed(URI uri, SocketAddress address, java.io.IOException error) { }
            }).build();
    }
    public boolean operationsAvailable() { return true; }
    public boolean playerOperationsAvailable() { return whitelistEnabled; }
    public boolean operatorOperationsAvailable() { return whitelistEnabled && operatorEnabled; }
    public String statusSource() { return "HOST_AGENT_TEST_CONTROL"; }
    public Status observe(String server) { return reader.observe(server); }
    public OnlinePlayers onlinePlayers(String server) { return reader.onlinePlayers(server); }
    public Diagnostics diagnostics(String server, String kind) {
        if (!properties.serverId().equals(server) || !java.util.Set.of("players", "metrics", "logs", "backups", "retention", "configuration", "mods", "runtime").contains(kind))
            return new Diagnostics(server, null, null, "SOURCE_NOT_CONFIGURED");
        java.util.concurrent.CompletableFuture<HttpResponse<byte[]>> pending = null;
        try {
            int seconds=kind.equals("retention")?30:8;
            var request = HttpRequest.newBuilder(properties.baseUrl().resolve("/agent/v1/servers/" + server + "/diagnostics/" + kind))
                .timeout(Duration.ofSeconds(seconds)).header("Authorization", authorization).GET().build();
            pending = client.sendAsync(request, info -> new LimitedBodySubscriber(kind.equals("retention")?2_097_152:524_288));
            var response = pending.get(seconds, TimeUnit.SECONDS);
            if (response.statusCode() != 200 || !response.headers().firstValue("Content-Type").orElse("").startsWith("application/json")) throw new IllegalStateException();
            var node = mapper.readTree(response.body());
            var at = java.time.Instant.parse(node.path("observedAt").asText());
            if (!server.equals(node.path("serverId").asText()) || !node.path("data").isObject()
                || at.isBefore(java.time.Instant.now().minusSeconds(30)) || at.isAfter(java.time.Instant.now().plusSeconds(5))) throw new IllegalStateException();
            return new Diagnostics(server, at, node.path("data"), null);
        } catch (Exception error) {
            if (error instanceof InterruptedException) Thread.currentThread().interrupt();
            return new Diagnostics(server, null, null, "SOURCE_UNAVAILABLE");
        } finally { if (pending != null && !pending.isDone()) pending.cancel(true); }
    }
    public void execute(String server, OperationType operation) { throw new IllegalStateException("Persistente Job-ID erforderlich"); }
    public void execute(String server, OperationType operation, UUID id) {
        execute(server, operation, id, null);
    }
    public void execute(String server, OperationType operation, UUID id, String targetName) {
        if (!properties.serverId().equals(server)) throw new IllegalStateException("Falscher Zielserver");
        if (operation.operatorOperation() && !operatorOperationsAvailable()) throw new IllegalStateException("OP-Steuerung deaktiviert");
        if (operation.playerOperation() ? !whitelistEnabled || targetName == null || !targetName.matches("[A-Za-z0-9_]{3,16}") : targetName != null)
            throw new IllegalStateException("Ungültige oder deaktivierte Spieleroperation");
        java.util.concurrent.CompletableFuture<HttpResponse<byte[]>> pending = null;
        try {
            var uri = properties.baseUrl().resolve("/agent/v1/servers/" + server + "/operations");
            var body = new java.util.LinkedHashMap<String, Object>(); body.put("operationId", id); body.put("type", operation);
            if (targetName != null) body.put("targetName", targetName);
            var request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(55)).header("Authorization", authorization)
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofByteArray(mapper.writeValueAsBytes(body))).build();
            pending = client.sendAsync(request, info -> new LimitedBodySubscriber(16_384));
            var result = pending.get(55, TimeUnit.SECONDS);
            if (!result.headers().firstValue("Content-Type").orElse("").split(";", 2)[0].trim().equalsIgnoreCase("application/json"))
                throw new IllegalStateException();
            var json = mapper.readTree(result.body());
            if (result.statusCode() != 200 || !id.toString().equals(json.path("operationId").asText())
                || !operation.name().equals(json.path("type").asText()) || !"SUCCEEDED".equals(json.path("state").asText())) throw new IllegalStateException();
            if (!java.util.Objects.equals(targetName, json.path("targetName").isTextual() ? json.path("targetName").asText() : null)) throw new IllegalStateException();
            var status = reader.observe(server);
            if (status.lifecycle() != (operation == OperationType.STOP ? Lifecycle.OFFLINE : Lifecycle.RUNNING)) throw new IllegalStateException();
        } catch (Exception error) {
            if (error instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new IllegalStateException("Testergebnis konnte nicht bestätigt werden");
        } finally { if (pending != null && !pending.isDone()) pending.cancel(true); }
    }
    public void close() { client.close(); reader.close(); }
}
