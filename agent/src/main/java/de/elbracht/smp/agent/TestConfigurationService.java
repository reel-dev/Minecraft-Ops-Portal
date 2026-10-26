package de.elbracht.smp.agent;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
@Service
public class TestConfigurationService {
 public record Field(String key,String label,String type,Integer min,Integer max,List<String> options,String value,boolean managedByEnvironment){}
 public record Snapshot(String revision,List<Field> fields,boolean writable,boolean restartRequired,String source){}
 private record Spec(String label,String type,Integer min,Integer max,List<String> options){}
 private static final Map<String,Spec> SCHEMA;
 static {var schema=new LinkedHashMap<String,Spec>();schema.put("difficulty",new Spec("Schwierigkeit","enum",null,null,List.of("peaceful","easy","normal","hard")));schema.put("gamemode",new Spec("Spielmodus","enum",null,null,List.of("survival","creative","adventure","spectator")));schema.put("pvp",new Spec("PvP","enum",null,null,List.of("true","false")));schema.put("spawn-protection",new Spec("Spawnschutz","integer",0,128,List.of()));schema.put("max-players",new Spec("Maximale Spielerzahl","integer",1,1000,List.of()));schema.put("view-distance",new Spec("Sichtweite","integer",2,32,List.of()));schema.put("simulation-distance",new Spec("Simulationsdistanz","integer",2,32,List.of()));schema.put("motd",new Spec("Serverbeschreibung","text",null,null,List.of()));SCHEMA=Collections.unmodifiableMap(schema);}
 private final DockerControl docker;private final ContainerProbe probe;private final boolean enabled;
 public boolean available(){return enabled;}
 public TestConfigurationService(DockerControl docker,ContainerProbe probe,AgentProperties agent,@Value("${agent.test-configuration-enabled:false}") boolean enabled){this.docker=docker;this.probe=probe;this.enabled=enabled;if(enabled&&!"docker-test-control".equals(agent.mode()))throw new IllegalStateException("Konfiguration nur am Testanschluss");}
 public Snapshot read()throws Exception{
  byte[] bytes=docker.readTestProperties();var values=new Properties();values.load(new StringReader(decode(bytes)));var managed=docker.managedTestProperties();List<Field> fields=new ArrayList<>();
  SCHEMA.forEach((key,spec)->fields.add(new Field(key,spec.label,spec.type,spec.min,spec.max,spec.options,values.getProperty(key),managed.contains(key))));return new Snapshot(hash(bytes),List.copyOf(fields),enabled,true,"server.properties");
 }
 public String apply(String revision,Map<String,String> changes)throws Exception{
  if(!enabled||revision==null||!revision.matches("[a-f0-9]{64}"))throw new IOException("Konfigurationsänderung deaktiviert");
  validate(changes);docker.verifyTarget();var state=probe.inspect();if(!state.targetMatches()||!state.running()||state.paused()||state.dead()||state.restarting())throw new IOException("Testcontainer nicht bereit");
  // Minecraft reloads these settings only on next restart; no restart is hidden in a save.
  byte[] current=docker.readTestProperties();if(!hash(current).equals(revision))throw new IOException("Konfigurationsrevision hat sich geändert");
  var existing=new Properties();existing.load(new StringReader(decode(current)));if(changes.keySet().stream().anyMatch(key->existing.getProperty(key)==null))throw new IOException("Eigenschaft vom Server nicht bereitgestellt");
  if(changes.keySet().stream().anyMatch(docker.managedTestProperties()::contains))throw new IOException("Wert wird von Docker vorgegeben");
  byte[] next=merge(current,changes);docker.writeTestProperties(next);if(!Arrays.equals(next,docker.readTestProperties()))throw new IOException("Gespeicherte Konfiguration nicht bestätigt");
  return "Konfiguration atomar gespeichert. Ein kontrollierter Neustart ist erforderlich, bevor Minecraft die geänderten Werte verwendet.";
 }
 static void validate(Map<String,String> changes)throws IOException{
  if(changes==null||changes.isEmpty()||changes.size()>SCHEMA.size())throw new IOException("Keine gültige Änderung");
  for(var entry:changes.entrySet()){var spec=SCHEMA.get(entry.getKey());String value=entry.getValue();if(spec==null||value==null||value.length()>120||!value.equals(value.strip())||value.chars().anyMatch(c->c<32||c==127))throw new IOException("Ungültiger Konfigurationswert");
   if(spec.type.equals("enum")&&!spec.options.contains(value))throw new IOException("Ungültige Auswahl");
   if(spec.type.equals("integer")){if(!value.matches("[0-9]{1,4}"))throw new IOException();int number=Integer.parseInt(value);if(number<spec.min||number>spec.max)throw new IOException("Wert außerhalb des Bereichs");}
  }
 }
 static byte[] merge(byte[] current,Map<String,String> changes)throws IOException{
  validate(changes);String raw=decode(current);String newline=raw.contains("\r\n")?"\r\n":"\n";var lines=new ArrayList<>(Arrays.asList(raw.split("\\r?\\n",-1)));Set<String> seen=new HashSet<>();
  for(int i=0;i<lines.size();i++){String line=lines.get(i);for(var entry:changes.entrySet())if(line.matches("\\s*"+entry.getKey()+"\\s*[=:].*")){if(!seen.add(entry.getKey())||line.endsWith("\\"))throw new IOException("Mehrdeutige Eigenschaft");lines.set(i,entry.getKey()+"="+escape(entry.getValue()));}}
  for(var entry:changes.entrySet())if(!seen.contains(entry.getKey()))lines.add(entry.getKey()+"="+escape(entry.getValue()));return String.join(newline,lines).getBytes(StandardCharsets.UTF_8);
 }
 private static String escape(String text){StringBuilder result=new StringBuilder();for(char ch:text.toCharArray()){if(ch=='\\')result.append("\\\\");else if(ch>126)result.append(String.format("\\u%04x",(int)ch));else result.append(ch);}return result.toString();}
 static String hash(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
 private static String decode(byte[] bytes)throws java.nio.charset.CharacterCodingException{return StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes)).toString();}
}
