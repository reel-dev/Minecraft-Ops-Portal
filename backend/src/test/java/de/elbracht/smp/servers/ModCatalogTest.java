package de.elbracht.smp.servers;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
class ModCatalogTest {
 @TempDir Path root;
 @Test void searchUsesFixedOriginAndRevalidatesConcreteFilesWithoutExposingTheKey()throws Exception{
  var agent=mock(HostAgent.class);when(agent.observe("main")).thenReturn(new HostAgent.Status("main",HostAgent.Lifecycle.RUNNING,HostAgent.Health.OK,HostAgent.Activity.NONE,"26.3","FABRIC","0.19.5",Instant.now(),null,0));
  Path secret=root.resolve("key");Files.writeString(secret,"test-only-not-a-real-api-key");var mapper=new ObjectMapper();var catalog=new ModCatalog(agent,mapper,secret.toString());
  var client=mock(HttpClient.class);ReflectionTestUtils.setField(catalog,"client",client);List<HttpRequest> requests=new ArrayList<>();
  when(client.sendAsync(any(HttpRequest.class),any(HttpResponse.BodyHandler.class))).thenAnswer(call->{HttpRequest request=call.getArgument(0);requests.add(request);
   String json=request.uri().getPath().endsWith("/search")?"{\"data\":[{\"id\":7,\"gameId\":432,\"classId\":6,\"name\":\"Explicit test fixture\",\"summary\":\"Fixture metadata, no real mod claim\"}]}":"{\"data\":[{\"id\":12,\"modId\":7,\"displayName\":\"fixture.jar\",\"fileLength\":100,\"isAvailable\":true,\"gameVersions\":[\"26.3\",\"Fabric\"],\"dependencies\":[]}]}";
   HttpResponse<byte[]> response=mock(HttpResponse.class);when(response.statusCode()).thenReturn(200);when(response.body()).thenReturn(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));when(response.headers()).thenReturn(HttpHeaders.of(Map.of("Content-Type",List.of("application/json")),(a,b)->true));return CompletableFuture.completedFuture(response);
  });
  var result=catalog.search("main","fixture & escape");assertThat(result.items()).hasSize(1);assertThat(result.items().getFirst().file().id()).isEqualTo(12);
  assertThat(requests).hasSize(2).allSatisfy(request->{assertThat(request.uri().getScheme()).isEqualTo("https");assertThat(request.uri().getHost()).isEqualTo("api.curseforge.com");assertThat(request.uri().getRawQuery()).contains("gameVersion=26.3","modLoaderType=4");assertThat(request.headers().firstValue("x-api-key")).contains("test-only-not-a-real-api-key");assertThat(request.uri().toString()).doesNotContain("test-only-not-a-real-api-key");});
  assertThat(mapper.writeValueAsString(result)).doesNotContain("test-only-not-a-real-api-key");
 }
 @Test void absentSecretAndUnverifiedLoaderMakeNoExternalRequest()throws Exception{
  var agent=mock(HostAgent.class);when(agent.observe("main")).thenReturn(new HostAgent.Status("main",HostAgent.Lifecycle.RUNNING,HostAgent.Health.OK,HostAgent.Activity.NONE,"26.3",null,null,Instant.now(),null,0));
  var catalog=new ModCatalog(agent,new ObjectMapper(),"");assertThat(catalog.search("main","").reasonCode()).isEqualTo("CURSEFORGE_NOT_CONFIGURED");
  Path secret=root.resolve("key");Files.writeString(secret,"fixture-secret-not-real");catalog=new ModCatalog(agent,new ObjectMapper(),secret.toString());var client=mock(HttpClient.class);ReflectionTestUtils.setField(catalog,"client",client);
  assertThat(catalog.search("main","").reasonCode()).isEqualTo("FABRIC_NOT_VERIFIED");verifyNoInteractions(client);
 }
 @Test void completeInspectionKeepsApiKeyOutOfCdnAndReturnsOnlyVerifiedIdentities()throws Exception{
  var mapper=new ObjectMapper();var agent=mock(HostAgent.class);when(agent.observe("main")).thenReturn(new HostAgent.Status("main",HostAgent.Lifecycle.RUNNING,HostAgent.Health.OK,HostAgent.Activity.NONE,"26.3","FABRIC","0.19.5",Instant.now(),null,0));when(agent.diagnostics("main","runtime")).thenReturn(new HostAgent.Diagnostics("main",Instant.now(),mapper.readTree("{\"javaVersion\":\"25.0.1\"}"),null));
  byte[] jar;var buffer=new java.io.ByteArrayOutputStream();try(var zip=new java.util.zip.ZipOutputStream(buffer)){zip.putNextEntry(new java.util.zip.ZipEntry("fabric.mod.json"));zip.write("{\"schemaVersion\":1,\"id\":\"explicit_fixture\",\"version\":\"1.0.0\",\"environment\":\"server\",\"depends\":{\"minecraft\":\"26.3\",\"fabricloader\":\">=0.19.5\",\"java\":\">=25\"}}".getBytes(java.nio.charset.StandardCharsets.UTF_8));zip.closeEntry();}jar=buffer.toByteArray();String sha1=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-1").digest(jar));
  Path secret=root.resolve("key");Files.writeString(secret,"explicit-fixture-key-not-real");var catalog=new ModCatalog(agent,mapper,secret.toString());var api=mock(HttpClient.class);var cdn=mock(HttpClient.class);ReflectionTestUtils.setField(catalog,"client",api);ReflectionTestUtils.setField(ReflectionTestUtils.getField(catalog,"downloader"),"client",cdn);List<HttpRequest> apiRequests=new ArrayList<>(),cdnRequests=new ArrayList<>();
  var project=Map.of("data",Map.of("id",7,"gameId",432,"classId",6,"name","Explicit test fixture","allowModDistribution",true));var file=Map.of("data",Map.of("id",12,"modId",7,"fileLength",jar.length,"isAvailable",true,"gameVersions",List.of("26.3","Fabric"),"hashes",List.of(Map.of("algo",1,"value",sha1)),"downloadUrl","https://edge.forgecdn.net/files/1/2/fixture.jar","dependencies",List.of()));
  when(api.sendAsync(any(HttpRequest.class),any(HttpResponse.BodyHandler.class))).thenAnswer(call->{HttpRequest request=call.getArgument(0);apiRequests.add(request);HttpResponse<byte[]> response=mock(HttpResponse.class);when(response.statusCode()).thenReturn(200);when(response.body()).thenReturn(mapper.writeValueAsBytes(request.uri().getPath().equals("/v1/mods/7")?project:file));when(response.headers()).thenReturn(HttpHeaders.of(Map.of("Content-Type",List.of("application/json")),(a,b)->true));return CompletableFuture.completedFuture(response);});
  when(cdn.sendAsync(any(HttpRequest.class),any(HttpResponse.BodyHandler.class))).thenAnswer(call->{cdnRequests.add(call.getArgument(0));HttpResponse<byte[]> response=mock(HttpResponse.class);when(response.statusCode()).thenReturn(200);when(response.body()).thenReturn(jar);return CompletableFuture.completedFuture(response);});
  var result=catalog.inspect("main",7,12);assertThat(result.state()).isEqualTo("FILES_VERIFIED");assertThat(result.files().getFirst().fabricMods()).containsExactly("explicit_fixture@1.0.0");assertThat(result.files().getFirst().sha256()).matches("[a-f0-9]{64}");assertThat(result.remainingChecks()).isNotEmpty();
  assertThat(apiRequests).hasSize(2).allSatisfy(request->assertThat(request.headers().firstValue("x-api-key")).contains("explicit-fixture-key-not-real"));assertThat(cdnRequests).hasSize(1).allSatisfy(request->assertThat(request.headers().map().keySet()).containsExactly("Accept"));assertThat(mapper.writeValueAsString(result)).doesNotContain("explicit-fixture-key-not-real","https://");
 }
}
