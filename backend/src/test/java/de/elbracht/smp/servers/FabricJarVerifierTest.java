package de.elbracht.smp.servers;
import com.fasterxml.jackson.databind.*;
import java.io.*;
import java.security.*;
import java.util.*;
import java.util.zip.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class FabricJarVerifierTest {
 final ObjectMapper mapper=new ObjectMapper();
 byte[] jar(String json,Map<String,byte[]> extras)throws Exception {var bytes=new ByteArrayOutputStream();try(var zip=new ZipOutputStream(bytes)){if(json!=null){zip.putNextEntry(new ZipEntry("fabric.mod.json"));zip.write(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));zip.closeEntry();}for(var entry:extras.entrySet()){zip.putNextEntry(new ZipEntry(entry.getKey()));zip.write(entry.getValue());zip.closeEntry();}}return bytes.toByteArray();}
 String descriptor(String id,String environment,Map<String,String> depends){return mapper.valueToTree(Map.of("schemaVersion",1,"id",id,"version","1.2.0","environment",environment,"depends",depends)).toString();}
 FabricJarVerifier.Archive verify(byte[] bytes)throws Exception{return new FabricJarVerifier().verify(bytes,bytes.length,HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(bytes)));}
 @Test void serverMetadataAndNestedLibrariesAreReadWithoutExtracting()throws Exception {
  byte[] library=jar(descriptor("fixture_library","*",Map.of()),Map.of());var root=(com.fasterxml.jackson.databind.node.ObjectNode)mapper.readTree(descriptor("fixture_root","server",Map.of("fixture_library",">=1.0.0","minecraft","26.3","fabricloader",">=0.19.5")));root.set("jars",mapper.valueToTree(List.of(Map.of("file","META-INF/jars/library.jar"))));
  var archive=verify(jar(root.toString(),Map.of("META-INF/jars/library.jar",library)));assertThat(archive.mods()).extracting(FabricJarVerifier.Mod::id).containsExactly("fixture_root","fixture_library");assertThat(archive.sha256()).matches("[a-f0-9]{64}");assertThat(FabricJarVerifier.verifySet(List.of(archive),"26.3","0.19.5","25")).isEmpty();
 }
 @Test void pureClientAndAbsentFabricMetadataAndTraversalAreBlocked()throws Exception {
  assertThatThrownBy(()->verify(jar(descriptor("fixture_client","client",Map.of()),Map.of()))).hasMessage("SERVER_ENVIRONMENT_NOT_CONFIRMED");
  assertThatThrownBy(()->verify(jar(null,Map.of("file.txt",new byte[]{1})))).hasMessage("FABRIC_METADATA_MISSING");
  assertThatThrownBy(()->verify(jar(descriptor("fixture_root","*",Map.of()),Map.of("../escape",new byte[]{1})))).hasMessage("UNSAFE_JAR_PATH");
 }
 @Test void wrongHashAndMissingNestedJarAreBlocked()throws Exception {
  byte[] bytes=jar(descriptor("fixture_root","*",Map.of()),Map.of());assertThatThrownBy(()->new FabricJarVerifier().verify(bytes,bytes.length,"0".repeat(40))).hasMessage("JAR_HASH_MISMATCH");
  var node=(com.fasterxml.jackson.databind.node.ObjectNode)mapper.readTree(descriptor("fixture_root","*",Map.of()));node.set("jars",mapper.valueToTree(List.of(Map.of("file","missing.jar"))));assertThatThrownBy(()->verify(jar(node.toString(),Map.of()))).hasMessage("NESTED_JAR_MISSING");
 }
 @Test void unsatisfiedAndDuplicateModIdentitiesAreBlocked()throws Exception {
  var archive=verify(jar(descriptor("fixture_root","*",Map.of("minecraft",">=26.4")),Map.of()));assertThatThrownBy(()->FabricJarVerifier.verifySet(List.of(archive),"26.3","0.19.5","25")).hasMessage("FABRIC_DEPENDENCY_UNSATISFIED");
  var valid=verify(jar(descriptor("fixture_other","*",Map.of()),Map.of()));assertThatThrownBy(()->FabricJarVerifier.verifySet(List.of(valid,valid),"26.3","0.19.5","25")).hasMessage("DUPLICATE_MOD_ID");
 }
 @Test void versionRangesAreConservativeAndSupportFabricAndOrRules()throws Exception {
  for(String predicate:List.of(">=26.2 <26.4","26.3","26.3.0","26.x","^26.1","~26.3","*"))assertThat(FabricJarVerifier.matches("26.3",mapper.valueToTree(predicate))).as(predicate).isTrue();
  assertThat(FabricJarVerifier.matches("26.3",mapper.valueToTree(List.of("26.2","26.3")))).isTrue();assertThat(FabricJarVerifier.matches("26.3",mapper.valueToTree(">26.3"))).isFalse();
  assertThatThrownBy(()->FabricJarVerifier.matches("26.3",mapper.valueToTree(">=26.3-beta"))).hasMessage("FABRIC_RANGE_REVIEW_REQUIRED");
 }
 @Test void duplicateJsonFieldsAndUnknownEnvironmentNeverPass()throws Exception {
  assertThatThrownBy(()->verify(jar("{\"schemaVersion\":1,\"id\":\"fixture_root\",\"version\":\"1\",\"environment\":\"server\",\"environment\":\"client\"}",Map.of()))).isInstanceOf(IOException.class);
  assertThatThrownBy(()->verify(jar(descriptor("fixture_root","unknown",Map.of()),Map.of()))).hasMessage("SERVER_ENVIRONMENT_NOT_CONFIRMED");
 }
}
