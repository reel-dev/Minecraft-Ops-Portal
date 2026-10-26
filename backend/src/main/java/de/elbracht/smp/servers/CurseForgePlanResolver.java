package de.elbracht.smp.servers;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.util.*;

/** Pins the entire required graph before any download or host mutation. */
final class CurseForgePlanResolver {
 interface Source { JsonNode project(long id)throws Exception;JsonNode file(long project,long file)throws Exception;JsonNode files(long project)throws Exception; }
 record Pinned(long projectId,long fileId,String name,long bytes,String sha1,URI download,List<Long> required,List<Long> optional,List<Long> incompatible){}
 private final Source source;private final String version;
 private final Map<Long,Long> preferred;
 private final Map<Long,Pinned> pinned=new LinkedHashMap<>();private long total;
 CurseForgePlanResolver(Source source,String version){this(source,version,Map.of());}
 CurseForgePlanResolver(Source source,String version,Map<Long,Long> preferred){this.source=source;this.version=version;this.preferred=Map.copyOf(preferred);}
 List<Pinned> resolve(long project,long file)throws Exception {
  if(project<=0||file<=0)throw new IllegalArgumentException("INVALID_MOD_ID");visit(project,file);
  for(var item:pinned.values())for(long conflict:item.incompatible())if(pinned.containsKey(conflict))throw new IllegalArgumentException("INCOMPATIBLE_DEPENDENCY");
  return List.copyOf(pinned.values());
 }
 private void visit(long id,Long exact)throws Exception {
  if(exact==null)exact=preferred.get(id);
  if(pinned.containsKey(id)){if(exact!=null&&pinned.get(id).fileId()!=exact)throw new IllegalArgumentException("CONFLICTING_FILE_PINS");return;}
  if(id<=0||pinned.size()>=32)throw new IllegalArgumentException("DEPENDENCY_LIMIT");
  JsonNode project=source.project(id);
  if(project.path("id").asLong()!=id||project.path("gameId").asInt()!=432||project.path("classId").asInt()!=6||!project.path("allowModDistribution").isBoolean()||!project.path("allowModDistribution").asBoolean())throw new IllegalArgumentException("MOD_DISTRIBUTION_NOT_CONFIRMED");
  JsonNode file;
  if(exact!=null)file=source.file(id,exact);
  else {JsonNode files=source.files(id);if(!files.isArray()||files.size()>50)throw new IllegalArgumentException("INVALID_FILE_LIST");file=null;for(var candidate:files)if(CurseForgeMetadata.matches(candidate,version,id)){file=candidate;break;}if(file==null)throw new IllegalArgumentException("EXACT_DEPENDENCY_MISSING");}
  if(!CurseForgeMetadata.matches(file,version,id)||(exact!=null&&file.path("id").asLong()!=exact))throw new IllegalArgumentException("FILE_PROFILE_MISMATCH");
  long size=file.path("fileLength").asLong();if(size<=0||size>32L*1024*1024||total+size>64L*1024*1024)throw new IllegalArgumentException("MOD_DOWNLOAD_LIMIT");
  String sha1=null;JsonNode hashes=file.path("hashes");if(!hashes.isArray()||hashes.size()>8)throw new IllegalArgumentException("HASH_MISSING");
  for(var hash:hashes)if(hash.path("algo").asInt()==1){String value=hash.path("value").asText().toLowerCase(Locale.ROOT);if(!value.matches("[a-f0-9]{40}")||(sha1!=null&&!sha1.equals(value)))throw new IllegalArgumentException("INVALID_HASH");sha1=value;}
  if(sha1==null)throw new IllegalArgumentException("SHA1_MISSING");URI url=downloadUri(file.path("downloadUrl").asText());
  Set<Long> required=new LinkedHashSet<>(),optional=new LinkedHashSet<>(),incompatible=new LinkedHashSet<>();JsonNode deps=file.path("dependencies");if(!deps.isArray()||deps.size()>100)throw new IllegalArgumentException("INVALID_DEPENDENCIES");
  for(var dependency:deps){long dep=dependency.path("modId").asLong();int relation=dependency.path("relationType").asInt();if(dep<=0||relation<1||relation>6)throw new IllegalArgumentException("INVALID_DEPENDENCIES");switch(relation){case 3->required.add(dep);case 2->optional.add(dep);case 5->incompatible.add(dep);default->{}}}
  String name=project.path("name").asText();if(name.isBlank()||name.length()>160)throw new IllegalArgumentException("INVALID_PROJECT_NAME");
  var value=new Pinned(id,file.path("id").asLong(),name,size,sha1,url,List.copyOf(required),List.copyOf(optional),List.copyOf(incompatible));pinned.put(id,value);total+=size;
  for(long dep:required)visit(dep,null);
 }
 static URI downloadUri(String value){
  URI uri;try{uri=URI.create(value);}catch(Exception error){throw new IllegalArgumentException("DOWNLOAD_NOT_ALLOWED");}
  if(!"https".equals(uri.getScheme())||!Set.of("edge.forgecdn.net","mediafilez.forgecdn.net").contains(uri.getHost())||uri.getUserInfo()!=null||uri.getPort()!=-1||uri.getRawQuery()!=null||uri.getRawFragment()!=null||uri.getRawPath()==null||!uri.getRawPath().matches("/files/[0-9]+/[0-9]+/[A-Za-z0-9._~%+()! -]+\\.jar")||uri.getRawPath().contains("%2f")||uri.getRawPath().contains("%2F")||uri.getRawPath().contains("%5c")||uri.getRawPath().contains("%5C"))throw new IllegalArgumentException("DOWNLOAD_NOT_ALLOWED");
  return uri;
 }
}
