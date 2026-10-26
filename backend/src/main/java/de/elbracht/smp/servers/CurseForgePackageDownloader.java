package de.elbracht.smp.servers;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
/** CDN requests never carry the API key, cookies, redirects, or inherited proxy configuration. */
final class CurseForgePackageDownloader {
 private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).proxy(new ProxySelector(){public List<Proxy> select(URI uri){return List.of(Proxy.NO_PROXY);}public void connectFailed(URI uri,SocketAddress address,java.io.IOException error){}}).build();
 byte[] download(CurseForgePlanResolver.Pinned pin,long deadline)throws Exception {
  if(pin.bytes()<=0||pin.bytes()>32L*1024*1024)throw new java.io.IOException("DOWNLOAD_SIZE_LIMIT");
  URI uri=CurseForgePlanResolver.downloadUri(pin.download().toString());long remaining=deadline-System.nanoTime();if(remaining<=0)throw new java.io.IOException("DOWNLOAD_TIMEOUT");
  var request=HttpRequest.newBuilder(uri).timeout(Duration.ofNanos(Math.min(remaining,Duration.ofSeconds(25).toNanos()))).header("Accept","application/java-archive, application/octet-stream").GET().build();
  var pending=client.sendAsync(request,info->new LimitedBodySubscriber(Math.toIntExact(pin.bytes()+1)));
  try{var response=pending.get(remaining,TimeUnit.NANOSECONDS);if(response.statusCode()!=200||response.body().length!=pin.bytes())throw new java.io.IOException("DOWNLOAD_NOT_CONFIRMED");return response.body();}finally{if(!pending.isDone())pending.cancel(true);}
 }
}
