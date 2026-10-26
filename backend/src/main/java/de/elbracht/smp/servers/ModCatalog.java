package de.elbracht.smp.servers;
import com.fasterxml.jackson.databind.*;
import de.elbracht.smp.api.ApiException;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Approved server-side API use; fixed origin, no credential or remote body returned to clients. */
@Service
public class ModCatalog {
 public record File(long id,String name,long bytes,List<Long> requiredDependencies){}
 public record Mod(long id,String name,String summary,File file,String serverSuitability){}
 public record Search(String version,String loader,boolean configured,List<Mod> items,String reasonCode){}
 public record PlannedFile(long projectId,long fileId,String name,long bytes,String sha1,List<Long> required,List<Long> optional){}
 public record DependencyPlan(String version,String loader,List<PlannedFile> files,long totalBytes,String state,List<String> remainingChecks){}
 public record InspectedFile(long projectId,long fileId,String name,long bytes,String sha256,List<String> fabricMods){}
 public record Inspection(String version,String loader,String loaderVersion,String javaVersion,List<InspectedFile> files,String state,List<String> warnings,List<String> remainingChecks){}
 private record Graph(String version,String loaderVersion,List<CurseForgePlanResolver.Pinned> pins){}
 public record Root(long projectId,long fileId){}
 record Artifact(CurseForgePlanResolver.Pinned pin,String sha256,String content){}
 record Bundle(String version,String loaderVersion,String javaVersion,List<Root> roots,List<Artifact> artifacts){}
 private final CurseForgePackageDownloader downloader=new CurseForgePackageDownloader();
 private final java.util.concurrent.Semaphore inspectionSlot=new java.util.concurrent.Semaphore(1);
 private final HostAgent agent;private final ObjectMapper mapper;private final Path keyFile;
 private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).followRedirects(HttpClient.Redirect.NEVER)
  .proxy(new ProxySelector(){public List<Proxy> select(URI uri){return List.of(Proxy.NO_PROXY);}public void connectFailed(URI uri,SocketAddress address,java.io.IOException error){}}).build();
 public ModCatalog(HostAgent agent,ObjectMapper mapper,@Value("${portal.curseforge-key-file:}")String keyFile){this.agent=agent;this.mapper=mapper.copy().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);this.keyFile=keyFile.isBlank()?null:Path.of(keyFile);}
 public boolean configured(){return keyFile!=null&&Files.isRegularFile(keyFile);}
 public Search search(String server,String query){
  var status=agent.observe(server);String version=status.minecraftVersion();
  if(!configured())return new Search(version,status.loader(),false,List.of(),"CURSEFORGE_NOT_CONFIGURED");
  if(version==null||!version.matches("[0-9A-Za-z._-]{1,64}")||status.lifecycle()!=HostAgent.Lifecycle.RUNNING)return new Search(null,status.loader(),true,List.of(),"PROFILE_NOT_VERIFIED");
  if(!"FABRIC".equalsIgnoreCase(status.loader()))return new Search(version,status.loader(),true,List.of(),"FABRIC_NOT_VERIFIED");
  String key=readKey();
  long deadline=System.nanoTime()+Duration.ofSeconds(12).toNanos();
  try{
   String encoded=URLEncoder.encode(version,StandardCharsets.UTF_8);
   var projects=get("/mods/search?gameId=432&classId=6&modLoaderType=4&gameVersion="+encoded+"&pageSize=8&searchFilter="+URLEncoder.encode(query,StandardCharsets.UTF_8),key,deadline).path("data");
   if(!projects.isArray()||projects.size()>8)throw new Exception();List<Mod> items=new ArrayList<>();
   for(var project:projects){long id=project.path("id").asLong();if(id<=0||project.path("gameId").asInt()!=432||project.path("classId").asInt()!=6)continue;
    var files=get("/mods/"+id+"/files?gameVersion="+encoded+"&modLoaderType=4&pageSize=50",key,deadline).path("data");if(!files.isArray()||files.size()>50)throw new Exception();
    for(var file:files)if(CurseForgeMetadata.matches(file,version,id)){
     List<Long> deps=new ArrayList<>();for(var dependency:file.path("dependencies"))if(dependency.path("relationType").asInt()==3){long dependencyId=dependency.path("modId").asLong();if(dependencyId<=0||deps.size()>=100)throw new Exception();deps.add(dependencyId);}
     items.add(new Mod(id,bounded(project.path("name").asText(),160),bounded(project.path("summary").asText(),700),new File(file.path("id").asLong(),bounded(file.path("displayName").asText(),200),file.path("fileLength").asLong(),List.copyOf(deps)),"REQUIRES_SERVER_REVIEW"));break;
    }
   }
   return new Search(version,"Fabric",true,List.copyOf(items),null);
  }catch(ApiException error){throw error;}catch(Exception error){if(error instanceof InterruptedException)Thread.currentThread().interrupt();throw new ApiException(503,"CURSEFORGE_UNAVAILABLE","CurseForge-Abfrage nicht bestätigt. Keine Ersatztreffer verwendet.");}
 }
 public DependencyPlan preview(String server,long project,long file){
  var graph=resolve(server,project,file);var pins=graph.pins();return new DependencyPlan(graph.version(),"Fabric",pins.stream().map(p->new PlannedFile(p.projectId(),p.fileId(),p.name(),p.bytes(),p.sha1(),p.required(),p.optional())).toList(),pins.stream().mapToLong(CurseForgePlanResolver.Pinned::bytes).sum(),"METADATA_VERIFIED",List.of("Dateidownload und Hashprüfung","Fabric-JAR-Metadaten und Serverumgebung","Installierter Modsatz und Versionsbedingungen","Vorabsicherung, Laufzeittest und bestätigter Ausführungsplan"));
 }
 public Inspection inspect(String server,long project,long file){
  if(!inspectionSlot.tryAcquire())throw new ApiException(429,"MOD_INSPECTION_BUSY","Eine Dateiprüfung läuft bereits. Später erneut versuchen.");
  try{return inspectFiles(server,project,file);}finally{inspectionSlot.release();}
 }
 private Inspection inspectFiles(String server,long project,long file){
  var graph=resolve(server,project,file);var runtime=agent.diagnostics(server,"runtime");String java=runtime==null||runtime.data()==null?null:runtime.data().path("javaVersion").asText();
  if(java==null||!java.matches("[0-9]+(?:\\.[0-9]+){0,3}")||graph.loaderVersion()==null)throw new ApiException(409,"RUNTIME_NOT_VERIFIED","Tatsächliche Java- und Fabric-Laufzeit vor der JAR-Prüfung bestätigen.");
  List<FabricJarVerifier.Archive> archives=new ArrayList<>();List<InspectedFile> files=new ArrayList<>();long deadline=System.nanoTime()+Duration.ofSeconds(90).toNanos();
  try{
   for(var pin:graph.pins()){byte[] bytes=downloader.download(pin,deadline);var archive=new FabricJarVerifier().verify(bytes,pin.bytes(),pin.sha1());archives.add(archive);files.add(new InspectedFile(pin.projectId(),pin.fileId(),pin.name(),pin.bytes(),archive.sha256(),archive.mods().stream().map(mod->mod.id()+"@"+mod.version()).toList()));}
   var warnings=FabricJarVerifier.verifySet(archives,graph.version(),graph.loaderVersion(),java);var status=agent.observe(server);
   if(status.lifecycle()!=HostAgent.Lifecycle.RUNNING||!graph.version().equals(status.minecraftVersion())||!"FABRIC".equalsIgnoreCase(status.loader())||!graph.loaderVersion().equals(status.loaderVersion()))throw new java.io.IOException();
   return new Inspection(graph.version(),"Fabric",graph.loaderVersion(),java,List.copyOf(files),"FILES_VERIFIED",warnings,List.of("Abgleich mit dem installierten Modsatz und Manifest","Vollständige Vorabsicherung und isolierter Laufzeittest","Befristeter Ausführungsplan und ausdrückliche Adminbestätigung"));
  }catch(ApiException error){throw error;}catch(Exception error){if(error instanceof InterruptedException)Thread.currentThread().interrupt();throw new ApiException(422,"MOD_FILES_NOT_VERIFIED","Dateidownload, Prüfsumme, Fabric-Metadaten oder Versionsbedingungen nicht vollständig bestätigt. Keine Installation ausgeführt.");}
 }
 private Graph resolve(String server,long project,long file){return resolveRoots(server,List.of(new Root(project,file)));}
 Bundle downloadBundle(String server,List<Root> roots){
  if(!inspectionSlot.tryAcquire())throw new ApiException(429,"MOD_INSPECTION_BUSY","Eine Dateiprüfung läuft bereits.");
  try{var graph=resolveRoots(server,roots);var runtime=agent.diagnostics(server,"runtime");String java=runtime==null||runtime.data()==null?null:runtime.data().path("javaVersion").asText();if(java==null||!java.matches("[0-9]+(?:\\.[0-9]+){0,3}")||graph.loaderVersion()==null)throw new ApiException(409,"RUNTIME_NOT_VERIFIED","Java- und Fabric-Laufzeit zuerst bestätigen.");
   if(graph.pins().stream().mapToLong(CurseForgePlanResolver.Pinned::bytes).sum()>8L*1024*1024)throw new ApiException(422,"MOD_PLAN_SIZE_LIMIT","Der vollständige Modsatz überschreitet das aktuelle Planlimit von 8 MiB.");
   long deadline=System.nanoTime()+Duration.ofSeconds(90).toNanos();List<FabricJarVerifier.Archive> archives=new ArrayList<>();List<Artifact> artifacts=new ArrayList<>();for(var pin:graph.pins()){byte[] bytes=downloader.download(pin,deadline);var archive=new FabricJarVerifier().verify(bytes,pin.bytes(),pin.sha1());archives.add(archive);artifacts.add(new Artifact(pin,archive.sha256(),Base64.getEncoder().encodeToString(bytes)));}FabricJarVerifier.verifySet(archives,graph.version(),graph.loaderVersion(),java);
   var status=agent.observe(server);if(status.lifecycle()!=HostAgent.Lifecycle.RUNNING||!graph.version().equals(status.minecraftVersion())||!graph.loaderVersion().equals(status.loaderVersion())||!"FABRIC".equalsIgnoreCase(status.loader()))throw new java.io.IOException();return new Bundle(graph.version(),graph.loaderVersion(),java,List.copyOf(roots),List.copyOf(artifacts));
  }catch(ApiException error){throw error;}catch(Exception error){if(error instanceof InterruptedException)Thread.currentThread().interrupt();throw new ApiException(422,"MOD_FILES_NOT_VERIFIED","Vollständiger Modsatz, Hashes, Serverumgebung oder Versionsbedingungen nicht bestätigt. Keine Installation ausgeführt.");}finally{inspectionSlot.release();}
 }
 private Graph resolveRoots(String server,List<Root> roots){
  var status=agent.observe(server);String version=status.minecraftVersion();
  if(status.lifecycle()!=HostAgent.Lifecycle.RUNNING||version==null||!version.matches("[0-9A-Za-z._-]{1,64}")||!"FABRIC".equalsIgnoreCase(status.loader()))throw new ApiException(409,"PROFILE_NOT_VERIFIED","Tatsächliche Minecraft-Version und Fabric müssen zuerst bestätigt sein.");
  if(roots==null||roots.size()>32||roots.stream().anyMatch(root->root==null||root.projectId()<=0||root.fileId()<=0)||roots.stream().map(Root::projectId).distinct().count()!=roots.size())throw new ApiException(422,"INVALID_MOD_ROOTS","Ungültiger vollständiger Mod-Sollzustand.");if(roots.isEmpty())return new Graph(version,status.loaderVersion(),List.of());
  if(!configured())throw new ApiException(503,"CURSEFORGE_NOT_CONFIGURED","CurseForge ist noch nicht verbunden.");
  String key=readKey();long deadline=System.nanoTime()+Duration.ofSeconds(30).toNanos();String encoded=URLEncoder.encode(version,StandardCharsets.UTF_8);
  var source=new CurseForgePlanResolver.Source(){
   public JsonNode project(long id)throws Exception{return get("/mods/"+id,key,deadline).path("data");}
   public JsonNode file(long id,long file)throws Exception{return get("/mods/"+id+"/files/"+file,key,deadline).path("data");}
   public JsonNode files(long id)throws Exception{return get("/mods/"+id+"/files?gameVersion="+encoded+"&modLoaderType=4&pageSize=50",key,deadline).path("data");}
  };
  try{Map<Long,Long> preferred=new LinkedHashMap<>();for(var root:roots)preferred.put(root.projectId(),root.fileId());var resolver=new CurseForgePlanResolver(source,version,preferred);List<CurseForgePlanResolver.Pinned> pins=List.of();for(var root:roots)pins=resolver.resolve(root.projectId(),root.fileId());return new Graph(version,status.loaderVersion(),pins);}
  catch(ApiException error){throw error;}catch(IllegalArgumentException error){throw new ApiException(422,"MOD_PLAN_BLOCKED","Modplan blockiert: exaktes Versionsprofil, Downloadfreigabe, Prüfsumme oder vollständige Abhängigkeiten nicht bestätigt.");}
  catch(Exception error){if(error instanceof InterruptedException)Thread.currentThread().interrupt();throw new ApiException(503,"CURSEFORGE_UNAVAILABLE","Abhängigkeitsplan konnte nicht vollständig bestätigt werden.");}
 }
 private String readKey(){
  try(var in=Files.newInputStream(keyFile)){byte[] raw=in.readNBytes(4097);String key=new String(raw,StandardCharsets.UTF_8).strip();if(raw.length>4096||key.isBlank()||key.chars().anyMatch(c->c<33||c>126))throw new Exception();return key;}
  catch(Exception error){throw new ApiException(503,"CURSEFORGE_SECRET_UNAVAILABLE","CurseForge-Schlüssel nicht verfügbar.");}
 }
 private JsonNode get(String path,String key,long deadline)throws Exception{
  long remaining=deadline-System.nanoTime();if(remaining<=0)throw new java.io.IOException();
  var request=HttpRequest.newBuilder(URI.create("https://api.curseforge.com/v1"+path)).timeout(Duration.ofNanos(Math.min(remaining,Duration.ofSeconds(5).toNanos()))).header("x-api-key",key).header("Accept","application/json").GET().build();
  var pending=client.sendAsync(request,info->new LimitedBodySubscriber(2_097_152));
  try{var response=pending.get(remaining,TimeUnit.NANOSECONDS);
   if(response.statusCode()==401||response.statusCode()==403)throw new ApiException(503,"CURSEFORGE_AUTH_FAILED","CurseForge hat den serverseitigen Schlüssel abgewiesen.");
   if(response.statusCode()==429)throw new ApiException(503,"CURSEFORGE_RATE_LIMITED","CurseForge-Limit erreicht. Später erneut versuchen.");
   if(response.statusCode()!=200||!response.headers().firstValue("Content-Type").orElse("").split(";",2)[0].strip().equalsIgnoreCase("application/json"))throw new java.io.IOException();
   return mapper.readTree(response.body());
  }finally{if(!pending.isDone())pending.cancel(true);}
 }
 private static String bounded(String text,int max){return text.substring(0,Math.min(max,text.length()));}
}
