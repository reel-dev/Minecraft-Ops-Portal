package de.elbracht.smp.servers;
import de.elbracht.smp.api.ApiException;
import de.elbracht.smp.identity.ServerAccess;
import jakarta.servlet.http.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.Semaphore;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Bounded log stream with resumable window and repeated identity/role validation. */
@RestController
public class LogStreamController {
 private final HostAgent agent;private final ServerAccess access;private final Semaphore streams=new Semaphore(8);
 public LogStreamController(HostAgent agent,ServerAccess access){this.agent=agent;this.access=access;}
 @GetMapping(value="/api/v1/servers/{serverId}/logs/stream",produces=MediaType.TEXT_EVENT_STREAM_VALUE)
 public ResponseEntity<SseEmitter> stream(@PathVariable String serverId,@RequestHeader(value="Last-Event-ID",required=false)String lastEventId,@RequestParam(required=false)String after,Authentication auth,HttpServletRequest request){
  access.require(auth,serverId,true);String cursor=lastEventId!=null&&!lastEventId.isBlank()?lastEventId:after;
  if(cursor!=null&&!cursor.matches("[a-f0-9]{64}"))throw new ApiException(422,"INVALID_LOG_CURSOR","Logcursor ungültig.");
  var session=request.getSession(false);if(session==null)throw new ApiException(401,"SESSION_REQUIRED","Anmeldung erforderlich.");
  if(!streams.tryAcquire())throw new ApiException(429,"LOG_STREAM_LIMIT","Zu viele offene Logverbindungen. Eine andere Ansicht schließen.");
  request.setAttribute(de.elbracht.smp.identity.LogStreamAuthorization.AUTHORIZED,Boolean.TRUE);
  var emitter=new SseEmitter(120_000L);var stopped=new java.util.concurrent.atomic.AtomicBoolean();emitter.onCompletion(()->stopped.set(true));emitter.onTimeout(()->stopped.set(true));emitter.onError(error->stopped.set(true));
  String initial=cursor;Thread.ofVirtual().name("smp-log-stream").start(()->{
   String current=initial;long deadline=System.nanoTime()+java.time.Duration.ofSeconds(115).toNanos();
   try{
    while(!stopped.get()&&System.nanoTime()<deadline){
     if(!validSession(session))break;access.require(auth,serverId,true);
     var snapshot=agent.diagnostics(serverId,"logs");
     if(snapshot==null||snapshot.data()==null||!snapshot.data().path("lines").isArray())emitter.send(SseEmitter.event().name("status").reconnectTime(5000).data(Map.of("connected",false)));
     else{
      var values=snapshot.data().path("lines");if(values.size()>200)throw new IllegalStateException();List<String> lines=new ArrayList<>();
      for(var line:values){if(!line.isTextual()||line.asText().length()>2100)throw new IllegalStateException();lines.add(line.asText());}
      int start=resumeIndex(lines,current);if(current!=null&&start<0){emitter.send(SseEmitter.event().name("gap").data(Map.of("reason","LOG_WINDOW_EXPIRED")));start=0;}
      if(start<0)start=0;
      for(int index=start;index<lines.size();index++){if(stopped.get())break;String line=lines.get(index);current=cursor(line);emitter.send(SseEmitter.event().name("log").id(current).reconnectTime(5000).data(Map.of("text",line)));}
      emitter.send(SseEmitter.event().name("status").data(Map.of("connected",true,"observedAt",snapshot.observedAt()==null?"":snapshot.observedAt().toString())));
     }
     Thread.sleep(5000);
    }
    emitter.complete();
   }catch(Exception error){if(error instanceof InterruptedException)Thread.currentThread().interrupt();emitter.complete();}
   finally{streams.release();}
  });
  return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Accel-Buffering","no").body(emitter);
 }
 static boolean validSession(HttpSession session){try{int seconds=session.getMaxInactiveInterval();return session.getCreationTime()>0&&(seconds<=0||System.currentTimeMillis()-session.getLastAccessedTime()<seconds*1000L);}catch(IllegalStateException error){return false;}}
 static String cursor(String line){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(line.getBytes(StandardCharsets.UTF_8)));}catch(Exception error){throw new IllegalStateException();}}
 static int resumeIndex(List<String> lines,String cursor){if(cursor==null)return 0;for(int index=lines.size()-1;index>=0;index--)if(cursor(lines.get(index)).equals(cursor))return index+1;return -1;}
}
