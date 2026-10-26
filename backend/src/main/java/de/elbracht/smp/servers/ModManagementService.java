package de.elbracht.smp.servers;
import com.fasterxml.jackson.databind.*;
import de.elbracht.smp.api.ApiException;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
@Service
public class ModManagementService {
 public record Pin(long projectId,long fileId,long bytes,String sha256,String sha1,List<Long> required,String provenance){}
 public record Manifest(int schema,List<ModCatalog.Root> roots,List<Pin> files){}
 public record State(boolean managed,String revision,Manifest manifest,Map<String,String> actualFiles,String reasonCode){}
 public record Plan(UUID planId,String createdAt,String expiresAt,String minecraftVersion,String loaderVersion,String javaVersion,List<Pin> files,List<String> changes,String state){}
 public record Assignment(String name,long projectId,long fileId){}
 private final HostAgent agent;private final ModCatalog catalog;private final ObjectMapper mapper;
 public ModManagementService(HostAgent agent,ModCatalog catalog,ObjectMapper mapper){this.agent=agent;this.catalog=catalog;this.mapper=mapper;}
 public boolean available(){return agent.modsAvailable();}
 public State state(String server){
  require();try{var node=agent.modRpc(server,"/mods/state","GET",null,Duration.ofSeconds(15));if(!node.path("managed").isBoolean())throw new Exception();var state=mapper.treeToValue(node,State.class);if(state.revision()==null||!state.revision().matches("[a-f0-9]{64}")||state.manifest()==null||state.manifest().schema()!=1||state.manifest().roots()==null||state.manifest().roots().size()>32||state.manifest().files()==null||state.manifest().files().size()>32||state.actualFiles()==null||state.actualFiles().size()>500)throw new Exception();for(var root:state.manifest().roots())if(root==null||root.projectId()<=0||root.fileId()<=0)throw new Exception();for(var pin:state.manifest().files())validate(pin);for(var entry:state.actualFiles().entrySet())if(!entry.getKey().matches("[A-Za-z0-9_.+ -]{1,180}\\.jar")||!entry.getValue().matches("[a-f0-9]{64}"))throw new Exception();if(state.reasonCode()!=null&&!Set.of("MANUAL_MODS_REQUIRE_IMPORT","MOD_MANIFEST_NOT_VERIFIED").contains(state.reasonCode()))throw new Exception();return state;}
  catch(ApiException error){throw error;}catch(Exception error){throw new ApiException(503,"MOD_STATE_UNAVAILABLE","Bestätigtes Modinventar nicht verfügbar.");}
 }
 public Plan prepare(String server,String action,long project,Long file){
  var current=state(server);if(!current.managed())throw new ApiException(409,"MANUAL_MODS_REQUIRE_IMPORT","Vorhandene manuelle Dateien müssen zuerst zugeordnet werden. Es wird keine unbekannte Datei gelöscht.");List<ModCatalog.Root> roots=new ArrayList<>(current.manifest().roots());boolean exists=roots.stream().anyMatch(root->root.projectId()==project);
  if(!Set.of("INSTALL","UPDATE","REMOVE").contains(action)||project<=0||(!action.equals("REMOVE")&&(file==null||file<=0))||action.equals("REMOVE")&&file!=null)throw new ApiException(422,"INVALID_MOD_CHANGE","Modaktion und konkretes Dateiziel passen nicht zusammen.");
  if(action.equals("INSTALL")&&exists||!action.equals("INSTALL")&&!exists)throw new ApiException(409,"MOD_ROOT_CONFLICT","Installationsziel stimmt nicht mit den vorhandenen Hauptmods überein. Benötigte Bibliotheken lassen sich nicht einzeln entfernen.");
  roots.removeIf(root->root.projectId()==project);if(!action.equals("REMOVE"))roots.add(new ModCatalog.Root(project,file));
  var bundle=catalog.downloadBundle(server,roots);return prepareVerified(server,current,bundle,Map.of());
 }
 public Plan prepareImport(String server,List<Assignment> assignments){
  var current=state(server);if(current.managed()||!"MANUAL_MODS_REQUIRE_IMPORT".equals(current.reasonCode()))throw new ApiException(409,"MOD_IMPORT_NOT_APPLICABLE","Nur ein vollständiger bislang nicht zugeordneter Modbestand lässt sich importieren. Ein widersprüchliches Manifest zuerst prüfen.");
  if(assignments==null||assignments.isEmpty()||assignments.size()>32||assignments.stream().anyMatch(item->item==null||item.name()==null||!item.name().matches("[A-Za-z0-9_.+ -]{1,180}\\.jar")||item.projectId()<=0||item.fileId()<=0))throw new ApiException(422,"INVALID_MOD_IMPORT","Dateinamen und konkrete Projekt-/Datei-IDs erforderlich.");
  Map<String,Long> imports=new LinkedHashMap<>();List<ModCatalog.Root> roots=new ArrayList<>();Set<Long> projects=new HashSet<>();for(var assignment:assignments){if(imports.put(assignment.name(),assignment.projectId())!=null||!projects.add(assignment.projectId()))throw new ApiException(422,"MOD_IMPORT_DUPLICATE","Jede vorhandene Datei und jedes Projekt genau einmal zuordnen.");roots.add(new ModCatalog.Root(assignment.projectId(),assignment.fileId()));}
  if(!imports.keySet().equals(current.actualFiles().keySet()))throw new ApiException(422,"MOD_IMPORT_INCOMPLETE","Alle tatsächlich vorhandenen JAR-Dateien müssen ausdrücklich zugeordnet sein.");
  var bundle=catalog.downloadBundle(server,roots);for(var assignment:assignments){var artifact=bundle.artifacts().stream().filter(item->item.pin().projectId()==assignment.projectId()&&item.pin().fileId()==assignment.fileId()).findFirst().orElseThrow(()->new ApiException(422,"MOD_IMPORT_PIN_MISSING","Konkrete zugeordnete Datei nicht bestätigt."));if(!artifact.sha256().equals(current.actualFiles().get(assignment.name())))throw new ApiException(422,"MOD_IMPORT_HASH_MISMATCH","Vorhandene JAR und bestätigte CurseForge-Datei sind nicht bytegleich. Keine Datei wird ersetzt.");}
  return prepareVerified(server,current,bundle,imports);
 }
 private Plan prepareVerified(String server,State current,ModCatalog.Bundle bundle,Map<String,Long> imports){
  List<Map<String,Object>> packages=new ArrayList<>();List<Pin> expectedFiles=new ArrayList<>();for(var artifact:bundle.artifacts()){var pin=artifact.pin();var expected=new Pin(pin.projectId(),pin.fileId(),pin.bytes(),artifact.sha256(),pin.sha1(),pin.required(),"CURSEFORGE");expectedFiles.add(expected);packages.add(Map.of("pin",expected,"content",artifact.content()));}
  Map<String,Object> payload=new LinkedHashMap<>();payload.put("minecraftVersion",bundle.version());payload.put("loaderVersion",bundle.loaderVersion());payload.put("javaVersion",bundle.javaVersion());payload.put("revision",current.revision());payload.put("roots",bundle.roots());payload.put("packages",packages);payload.put("imports",imports);
  try{var plan=parse(agent.modRpc(server,"/mods/plans","POST",payload,Duration.ofSeconds(90)),null);if(!plan.state().equals("PLANNED")||!plan.minecraftVersion().equals(bundle.version())||!plan.loaderVersion().equals(bundle.loaderVersion())||!plan.javaVersion().equals(bundle.javaVersion())||plan.files().size()!=expectedFiles.size()||!new HashSet<>(plan.files()).equals(new HashSet<>(expectedFiles)))throw new IllegalStateException();return plan;}
  catch(ApiException error){throw error;}catch(Exception error){throw new ApiException(503,"MOD_PLAN_UNAVAILABLE","Modplan nicht bestätigt. Keine Serveränderung ausgeführt.");}
 }
 public Plan find(String server,UUID id){require();try{return parse(agent.modRpc(server,"/mods/plans/"+id,"GET",null,Duration.ofSeconds(10)),id);}catch(ApiException error){throw error;}catch(Exception error){throw new ApiException(503,"MOD_PLAN_UNAVAILABLE","Modplan nicht verfügbar.");}}
 public void cancel(String server,UUID id){require();try{var plan=parse(agent.modRpc(server,"/mods/plans/"+id,"DELETE",null,Duration.ofSeconds(10)),id);if(!plan.state().equals("CANCELLED"))throw new ApiException(409,"MOD_PLAN_NOT_CANCELLED","Abbruch nicht bestätigt.");}catch(ApiException error){throw error;}catch(Exception error){throw new ApiException(503,"MOD_PLAN_UNAVAILABLE","Modplan konnte nicht abgebrochen werden.");}}
 private Plan parse(JsonNode node,UUID expected){try{var plan=mapper.treeToValue(node,Plan.class);if(plan.planId()==null||expected!=null&&!expected.equals(plan.planId())||plan.files()==null||plan.files().size()>32||plan.changes()==null||plan.changes().isEmpty()||plan.changes().size()>65||plan.changes().stream().anyMatch(change->change==null||change.length()>220)||plan.minecraftVersion()==null||!plan.minecraftVersion().matches("[A-Za-z0-9._+-]{1,64}")||plan.loaderVersion()==null||!plan.loaderVersion().matches("[A-Za-z0-9._+-]{1,64}")||plan.javaVersion()==null||!plan.javaVersion().matches("[0-9]+(?:\\.[0-9]+){0,3}")||!Set.of("PLANNED","APPLYING","SUCCEEDED","ROLLED_BACK","UNKNOWN","CANCELLED","EXPIRED").contains(plan.state()))throw new Exception();Instant created=Instant.parse(plan.createdAt()),expires=Instant.parse(plan.expiresAt());if(!expires.isAfter(created)||expires.isAfter(created.plusSeconds(901))||created.isAfter(Instant.now().plusSeconds(10)))throw new Exception();for(var pin:plan.files())validate(pin);return plan;}catch(Exception error){throw new ApiException(503,"MOD_PLAN_INVALID","Modplan ist unvollständig oder widersprüchlich.");}}
 private static void validate(Pin pin){if(pin==null||pin.projectId()<=0||pin.fileId()<=0||pin.bytes()<1||pin.bytes()>8L*1024*1024||pin.sha256()==null||!pin.sha256().matches("[a-f0-9]{64}")||pin.sha1()==null||!pin.sha1().matches("[a-f0-9]{40}")||pin.required()==null||pin.required().size()>100||pin.required().stream().anyMatch(id->id==null||id<=0)||!Set.of("CURSEFORGE","LOCAL_ACCEPTANCE_FIXTURE").contains(pin.provenance()))throw new IllegalArgumentException();}
 private void require(){if(!available())throw new ApiException(503,"MOD_CONTROL_NOT_CONFIGURED","Kontrollierte Modverwaltung für diesen Anschluss nicht freigeschaltet.");}
}

