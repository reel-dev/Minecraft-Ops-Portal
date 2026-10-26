package de.elbracht.smp.servers;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
class CurseForgePackageDownloaderTest {
 CurseForgePlanResolver.Pinned pin(){return new CurseForgePlanResolver.Pinned(1,2,"Explicit fixture",3,"a".repeat(40),URI.create("https://edge.forgecdn.net/files/1/2/fixture.jar"),List.of(),List.of(),List.of());}
 @Test void cdnRequestCarriesNoCredentialsAndRedirectIsNotFollowed()throws Exception {
  var client=mock(HttpClient.class);var downloader=new CurseForgePackageDownloader();ReflectionTestUtils.setField(downloader,"client",client);List<HttpRequest> requests=new ArrayList<>();
  when(client.sendAsync(any(HttpRequest.class),any(HttpResponse.BodyHandler.class))).thenAnswer(call->{HttpRequest request=call.getArgument(0);requests.add(request);HttpResponse<byte[]> response=mock(HttpResponse.class);when(response.statusCode()).thenReturn(302);when(response.body()).thenReturn(new byte[0]);return CompletableFuture.completedFuture(response);});
  assertThatThrownBy(()->downloader.download(pin(),System.nanoTime()+Duration.ofSeconds(5).toNanos())).hasMessage("DOWNLOAD_NOT_CONFIRMED");assertThat(requests).hasSize(1);assertThat(requests.getFirst().headers().map().keySet()).containsExactly("Accept");
 }
 @Test void wrongLengthIsRejectedAndAnExpiredBudgetSendsNothing()throws Exception {
  var client=mock(HttpClient.class);var downloader=new CurseForgePackageDownloader();ReflectionTestUtils.setField(downloader,"client",client);HttpResponse<byte[]> response=mock(HttpResponse.class);when(response.statusCode()).thenReturn(200);when(response.body()).thenReturn(new byte[]{1});when(client.sendAsync(any(HttpRequest.class),any(HttpResponse.BodyHandler.class))).thenReturn(CompletableFuture.completedFuture(response));
  assertThatThrownBy(()->downloader.download(pin(),System.nanoTime()+Duration.ofSeconds(5).toNanos())).hasMessage("DOWNLOAD_NOT_CONFIRMED");reset(client);assertThatThrownBy(()->downloader.download(pin(),System.nanoTime()-1)).hasMessage("DOWNLOAD_TIMEOUT");verifyNoInteractions(client);
 }
}
