package de.elbracht.smp.servers;
import com.fasterxml.jackson.databind.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class CurseForgePlanResolverTest {
 final ObjectMapper mapper=new ObjectMapper();
 JsonNode file(long project,long id,List<Map<String,Object>> deps)throws Exception{return mapper.valueToTree(Map.of("id",id,"modId",project,"fileLength",123,"isAvailable",true,"gameVersions",List.of("26.3","Fabric"),"hashes",List.of(Map.of("algo",1,"value","a".repeat(40))),"downloadUrl","https://edge.forgecdn.net/files/1234/567/fixture.jar","dependencies",deps));}
 class Source implements CurseForgePlanResolver.Source {
  Map<Long,JsonNode> files=new HashMap<>();Set<Long> denied=new HashSet<>();
  public JsonNode project(long id){return mapper.valueToTree(Map.of("id",id,"name","Explicit fixture "+id,"gameId",432,"classId",6,"allowModDistribution",!denied.contains(id)));}
  public JsonNode file(long project,long id){return files.get(project);}
  public JsonNode files(long project){var array=mapper.createArrayNode();if(files.containsKey(project))array.add(files.get(project));return array;}
 }
 @Test void recursiveGraphDeduplicatesCyclesAndKeepsOptionalSeparate()throws Exception {
  var source=new Source();source.files.put(1L,file(1,11,List.of(Map.of("modId",2,"relationType",3),Map.of("modId",3,"relationType",2))));source.files.put(2L,file(2,22,List.of(Map.of("modId",1,"relationType",3))));
  var pins=new CurseForgePlanResolver(source,"26.3").resolve(1,11);assertThat(pins).hasSize(2);assertThat(pins.getFirst().optional()).containsExactly(3L);assertThat(pins).extracting(CurseForgePlanResolver.Pinned::fileId).containsExactly(11L,22L);
 }
 @Test void missingExactDependencyAndClientOnlyTagsNeverProducePartialPlan()throws Exception {
  var source=new Source();source.files.put(1L,file(1,11,List.of(Map.of("modId",2,"relationType",3))));
  assertThatThrownBy(()->new CurseForgePlanResolver(source,"26.3").resolve(1,11)).hasMessage("EXACT_DEPENDENCY_MISSING");
  var wrong=(com.fasterxml.jackson.databind.node.ObjectNode)file(2,22,List.of());wrong.set("gameVersions",mapper.valueToTree(List.of("26.2","Fabric")));source.files.put(2L,wrong);
  assertThatThrownBy(()->new CurseForgePlanResolver(source,"26.3").resolve(1,11)).hasMessage("EXACT_DEPENDENCY_MISSING");
 }
 @Test void distributionHashAndExplicitIncompatibilitiesBlockPlanning()throws Exception {
  var source=new Source();source.files.put(1L,file(1,11,List.of()));source.denied.add(1L);assertThatThrownBy(()->new CurseForgePlanResolver(source,"26.3").resolve(1,11)).hasMessage("MOD_DISTRIBUTION_NOT_CONFIRMED");source.denied.clear();
  var missing=(com.fasterxml.jackson.databind.node.ObjectNode)file(1,11,List.of());missing.remove("hashes");source.files.put(1L,missing);assertThatThrownBy(()->new CurseForgePlanResolver(source,"26.3").resolve(1,11)).hasMessage("HASH_MISSING");
  source.files.put(1L,file(1,11,List.of(Map.of("modId",2,"relationType",3),Map.of("modId",2,"relationType",5))));source.files.put(2L,file(2,22,List.of()));assertThatThrownBy(()->new CurseForgePlanResolver(source,"26.3").resolve(1,11)).hasMessage("INCOMPATIBLE_DEPENDENCY");
 }
 @Test void callerCannotReplaceThePinnedFileOrDownloadOrigin()throws Exception {
  var source=new Source();source.files.put(1L,file(1,12,List.of()));assertThatThrownBy(()->new CurseForgePlanResolver(source,"26.3").resolve(1,11)).hasMessage("FILE_PROFILE_MISMATCH");
  for(String url:List.of("http://edge.forgecdn.net/files/1/2/a.jar","https://evil.example/files/1/2/a.jar","https://edge.forgecdn.net.evil.example/files/1/2/a.jar","https://edge.forgecdn.net:443/files/1/2/a.jar","https://edge.forgecdn.net/files/1/2/a.jar?token=x","https://edge.forgecdn.net/files/1/2/%2Fa.jar"))assertThatThrownBy(()->CurseForgePlanResolver.downloadUri(url)).hasMessage("DOWNLOAD_NOT_ALLOWED");
 }
 @Test void explicitlyPinnedRootAlsoWinsWhenItIsARequiredDependency()throws Exception {
  JsonNode pinned=file(2,22,List.of());var source=new Source(){@Override public JsonNode file(long project,long id){return project==2&&id==22?pinned:files.get(project);}};source.files.put(1L,file(1,11,List.of(Map.of("modId",2,"relationType",3))));source.files.put(2L,file(2,23,List.of()));
  var resolver=new CurseForgePlanResolver(source,"26.3",Map.of(1L,11L,2L,22L));resolver.resolve(1,11);assertThat(resolver.resolve(2,22)).extracting(CurseForgePlanResolver.Pinned::fileId).containsExactly(11L,22L);
 }
}
