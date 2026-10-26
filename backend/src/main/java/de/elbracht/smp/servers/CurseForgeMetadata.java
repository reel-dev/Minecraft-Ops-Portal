package de.elbracht.smp.servers;
import com.fasterxml.jackson.databind.JsonNode;
/** Pure metadata validation; deliberately performs no network or credential access. */
public final class CurseForgeMetadata {
 private CurseForgeMetadata(){}
 public static boolean matches(JsonNode file,String version,long project){
  if(file==null||version==null||project<=0)return false;
  if(!file.isObject()||!file.path("gameVersions").isArray()||file.path("gameVersions").size()>200)return false;
  for(String field:java.util.List.of("modId","id","fileLength"))if(!file.path(field).isIntegralNumber()||!file.path(field).canConvertToLong())return false;
  boolean exact=false,fabric=false;for(var tag:file.path("gameVersions")){if(!tag.isTextual()||tag.asText().length()>100)return false;exact|=version.equals(tag.asText());fabric|="Fabric".equals(tag.asText());}
  return exact&&fabric&&file.path("modId").asLong()==project&&file.path("id").asLong()>0&&file.path("fileLength").asLong()>0&&file.path("isAvailable").isBoolean()&&file.path("isAvailable").asBoolean();
 }
}
