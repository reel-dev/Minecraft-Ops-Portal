package de.elbracht.smp.servers;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import java.io.*;
import java.security.*;
import java.util.*;
import java.util.zip.*;

/** Inspects untrusted archives without extracting or executing their code. */
final class FabricJarVerifier {
 record Mod(String id,String version,List<String> provides,Map<String,JsonNode> depends,Map<String,JsonNode> breaks,Map<String,JsonNode> conflicts,Map<String,JsonNode> recommends){}
 record Archive(String sha256,String sha1,List<Mod> mods){}
 private final ObjectMapper mapper=new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
 private long expanded;private int entries;
 Archive verify(byte[] jar,long expectedBytes,String expectedSha1)throws Exception {
  if(jar==null||jar.length!=expectedBytes||jar.length>32*1024*1024||expectedSha1==null||!expectedSha1.matches("[a-f0-9]{40}"))throw new IOException("JAR_SIZE_OR_HASH_MISSING");
  String sha1=hash("SHA-1",jar);if(!MessageDigest.isEqual(sha1.getBytes(java.nio.charset.StandardCharsets.US_ASCII),expectedSha1.getBytes(java.nio.charset.StandardCharsets.US_ASCII)))throw new IOException("JAR_HASH_MISMATCH");
  expanded=0;entries=0;List<Mod> mods=new ArrayList<>();inspect(jar,0,mods,true);return new Archive(hash("SHA-256",jar),sha1,List.copyOf(mods));
 }
 private void inspect(byte[] bytes,int depth,List<Mod> result,boolean root)throws Exception {
  if(depth>4||result.size()>256)throw new IOException("NESTED_JAR_LIMIT");
  Map<String,byte[]> nested=new HashMap<>();byte[] descriptor=null;Set<String> seen=new HashSet<>();
  try(var zip=new ZipInputStream(new ByteArrayInputStream(bytes))){ZipEntry entry;
   while((entry=zip.getNextEntry())!=null){String name=entry.getName();if(++entries>20000||!safePath(name)||!seen.add(name))throw new IOException("UNSAFE_JAR_PATH");
    boolean keep=!entry.isDirectory()&&(name.equals("fabric.mod.json")||name.endsWith(".jar"));var data=keep?new ByteArrayOutputStream():null;long size=0;byte[] buffer=new byte[8192];int count;
    while((count=zip.read(buffer))!=-1){size+=count;expanded+=count;if(size>32L*1024*1024||expanded>64L*1024*1024||(name.equals("fabric.mod.json")&&size>262144))throw new IOException("JAR_EXPANSION_LIMIT");if(keep)data.write(buffer,0,count);}
    if(entry.isDirectory()){if(size!=0)throw new IOException("INVALID_JAR_DIRECTORY");continue;}
    if(name.equals("fabric.mod.json"))descriptor=data.toByteArray();else if(keep)nested.put(name,data.toByteArray());
   }
  }
  if(descriptor==null)throw new IOException("FABRIC_METADATA_MISSING");JsonNode metadata=mapper.readTree(descriptor);
  if(!metadata.isObject()||metadata.path("schemaVersion").asInt()!=1)throw new IOException("FABRIC_SCHEMA_UNSUPPORTED");
  JsonNode env=metadata.path("environment");boolean server=env.isMissingNode()||(env.isTextual()&&Set.of("*","server").contains(env.asText()))||(env.isArray()&&java.util.stream.StreamSupport.stream(env.spliterator(),false).anyMatch(value->value.isTextual()&&Set.of("*","server").contains(value.asText())));
  if(!server){if(!root&&((env.isTextual()&&env.asText().equals("client"))||(env.isArray()&&env.size()==1&&env.get(0).asText().equals("client"))))return;throw new IOException("SERVER_ENVIRONMENT_NOT_CONFIRMED");}
  String id=metadata.path("id").asText(),version=metadata.path("version").asText();if(!id.matches("[a-z][a-z0-9_-]{1,63}")||version.isBlank()||version.length()>128)throw new IOException("INVALID_FABRIC_IDENTITY");
  List<String> provides=new ArrayList<>();JsonNode aliases=metadata.path("provides");if(!aliases.isMissingNode()){if(!aliases.isArray()||aliases.size()>100)throw new IOException();for(var alias:aliases){if(!alias.isTextual()||!alias.asText().matches("[a-z][a-z0-9_-]{1,63}"))throw new IOException();provides.add(alias.asText());}}
  if(result.size()>=256)throw new IOException("NESTED_JAR_LIMIT");result.add(new Mod(id,version,List.copyOf(provides),constraints(metadata,"depends"),constraints(metadata,"breaks"),constraints(metadata,"conflicts"),constraints(metadata,"recommends")));
  JsonNode jars=metadata.path("jars");if(!jars.isMissingNode()){if(!jars.isArray()||jars.size()>128)throw new IOException("NESTED_JAR_LIMIT");Set<String> references=new HashSet<>();for(var item:jars){String name=item.path("file").asText();if(!safePath(name)||!name.endsWith(".jar")||!references.add(name)||!nested.containsKey(name))throw new IOException("NESTED_JAR_MISSING");inspect(nested.get(name),depth+1,result,false);}}
 }
 private static Map<String,JsonNode> constraints(JsonNode metadata,String field)throws IOException {
  JsonNode node=metadata.path(field);if(node.isMissingNode())return Map.of();if(!node.isObject()||node.size()>128)throw new IOException("INVALID_FABRIC_CONSTRAINTS");Map<String,JsonNode> result=new LinkedHashMap<>();var it=node.fields();while(it.hasNext()){var entry=it.next();if(!entry.getKey().matches("[a-z][a-z0-9_-]{1,63}")||!validRange(entry.getValue()))throw new IOException("INVALID_FABRIC_CONSTRAINTS");result.put(entry.getKey(),entry.getValue());}return Map.copyOf(result);
 }
 private static boolean validRange(JsonNode range){if(range.isTextual())return !range.asText().isBlank()&&range.asText().length()<=256;if(!range.isArray()||range.isEmpty()||range.size()>32)return false;for(var item:range)if(!item.isTextual()||!validRange(item))return false;return true;}
 static List<String> verifySet(List<Archive> archives,String minecraft,String loader,String javaVersion)throws IOException {
  Map<String,String> versions=new HashMap<>(Map.of("minecraft",minecraft,"fabricloader",loader,"java",javaVersion));List<Mod> mods=archives.stream().flatMap(a->a.mods().stream()).toList();
  for(var mod:mods){List<String> ids=new ArrayList<>(mod.provides());ids.addFirst(mod.id());for(String id:ids)if(versions.putIfAbsent(id,mod.version())!=null)throw new IOException("DUPLICATE_MOD_ID");}
  List<String> warnings=new ArrayList<>();
  for(var mod:mods){for(var dep:mod.depends().entrySet())if(!versions.containsKey(dep.getKey())||!matches(versions.get(dep.getKey()),dep.getValue()))throw new IOException("FABRIC_DEPENDENCY_UNSATISFIED");for(var conflict:mod.breaks().entrySet())if(versions.containsKey(conflict.getKey())&&matches(versions.get(conflict.getKey()),conflict.getValue()))throw new IOException("FABRIC_INCOMPATIBLE_MOD");for(var dep:mod.recommends().entrySet())if(!versions.containsKey(dep.getKey())||!matches(versions.get(dep.getKey()),dep.getValue()))warnings.add(mod.id()+": Empfehlung "+dep.getKey()+" nicht erfüllt");for(var conflict:mod.conflicts().entrySet())if(versions.containsKey(conflict.getKey())&&matches(versions.get(conflict.getKey()),conflict.getValue()))warnings.add(mod.id()+": Konflikthinweis "+conflict.getKey());}
  return List.copyOf(warnings);
 }
 /** Conservative subset: unsupported expressions fail closed, never assert compatibility. */
 static boolean matches(String actual,JsonNode range)throws IOException{if(range.isArray()){for(var item:range)if(matches(actual,item))return true;return false;}if(!range.isTextual())throw new IOException("FABRIC_RANGE_REVIEW_REQUIRED");String text=range.asText().strip();if(text.isEmpty())throw new IOException("FABRIC_RANGE_REVIEW_REQUIRED");for(String term:text.split("\\s+"))if(!term(actual,term))return false;return true;}
 private static boolean term(String actual,String expression)throws IOException{
  if(expression.equals("*"))return true;var match=java.util.regex.Pattern.compile("(>=|<=|>|<|=|\\^|~)?(.+)").matcher(expression);if(!match.matches())return false;String op=match.group(1),value=match.group(2);if(op==null)op="=";
  if(op.equals("=")&&(actual.equals(value)||actual.split("\\+",2)[0].equals(value.split("\\+",2)[0])))return true;
  if(op.equals("=")&&value.matches("[0-9]+(?:\\.[0-9]+)*\\.[xX*](?:\\.[xX*])*")){if(!actual.matches("[0-9]+(?:\\.[0-9]+)*(?:\\+[A-Za-z0-9.-]+)?"))throw new IOException("FABRIC_RANGE_REVIEW_REQUIRED");var parts=value.split("\\.");String[] real=actual.split("[.+]");for(int i=0;i<parts.length;i++){if(parts[i].matches("[xX*]"))return true;if(i>=real.length||!parts[i].equals(real[i]))return false;}return false;}
  if(!actual.matches("[0-9]+(?:\\.[0-9]+)*(?:\\+[A-Za-z0-9.-]+)?")||!value.matches("[0-9]+(?:\\.[0-9]+)*(?:\\+[A-Za-z0-9.-]+)?"))throw new IOException("FABRIC_RANGE_REVIEW_REQUIRED");
  int comparison=compare(actual,value);return switch(op){case "="->comparison==0;case ">="->comparison>=0;case "<="->comparison<=0;case ">"->comparison>0;case "<"->comparison<0;case "^"->comparison>=0&&component(actual,0).equals(component(value,0));case "~"->comparison>=0&&component(actual,0).equals(component(value,0))&&component(actual,1).equals(component(value,1));default->false;};
 }
 private static int compare(String a,String b){for(int i=0;i<Math.max(a.split("\\.").length,b.split("\\.").length);i++){int c=component(a,i).compareTo(component(b,i));if(c!=0)return c;}return 0;}
 private static java.math.BigInteger component(String value,int index){String[] parts=value.split("\\+",2)[0].split("\\.");return index<parts.length?new java.math.BigInteger(parts[index]):java.math.BigInteger.ZERO;}
 private static boolean safePath(String path){if(path.isBlank()||path.length()>512||path.startsWith("/")||path.contains("\\")||path.contains(":")||path.chars().anyMatch(c->c<32))return false;for(String part:path.split("/"))if(part.equals("..")||part.equals("."))return false;return true;}
 private static String hash(String name,byte[] bytes)throws NoSuchAlgorithmException{return HexFormat.of().formatHex(MessageDigest.getInstance(name).digest(bytes));}
}
