package de.elbracht.smp.operations;
import java.util.*;
import de.elbracht.smp.api.ApiException;
public final class ConfigurationPolicy {
 private ConfigurationPolicy(){}
 public static void validate(String revision,Map<String,String> values){
  if(revision==null||!revision.matches("[a-f0-9]{64}")||values==null||values.isEmpty()||values.size()>8)invalid();
  for(var entry:values.entrySet()){
   String key=entry.getKey(),value=entry.getValue();if(value==null||value.length()>120||!value.equals(value.strip())||value.chars().anyMatch(c->c<32||c==127))invalid();
   switch(key){
    case "difficulty" -> {if(!Set.of("peaceful","easy","normal","hard").contains(value))invalid();}
    case "gamemode" -> {if(!Set.of("survival","creative","adventure","spectator").contains(value))invalid();}
    case "pvp" -> {if(!Set.of("true","false").contains(value))invalid();}
    case "motd" -> {}
    case "spawn-protection","max-players","view-distance","simulation-distance" -> {if(!value.matches("[0-9]{1,4}"))invalid();int number=Integer.parseInt(value);int min=key.equals("spawn-protection")?0:key.equals("max-players")?1:2;int max=key.equals("spawn-protection")?128:key.equals("max-players")?1000:32;if(number<min||number>max)invalid();}
    default -> invalid();
   }
  }
 }
 private static void invalid(){throw new ApiException(422,"INVALID_CONFIGURATION","Nur erlaubte Eigenschaften und Werte innerhalb ihrer Grenzen sind zulässig.");}
}
