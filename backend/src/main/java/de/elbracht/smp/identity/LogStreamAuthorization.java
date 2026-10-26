package de.elbracht.smp.identity;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
/** Internal completion only; clients cannot supply a servlet request attribute. */
public final class LogStreamAuthorization {
 private LogStreamAuthorization(){}
 public static final String AUTHORIZED="de.elbracht.smp.authorized-log-stream";
 public static boolean isAuthorizedCompletion(HttpServletRequest request){
  return request.getDispatcherType()==DispatcherType.ASYNC&&"GET".equals(request.getMethod())
   &&request.getServletPath().matches("/api/v1/servers/[A-Za-z0-9_-]{1,64}/logs/stream")
   &&Boolean.TRUE.equals(request.getAttribute(AUTHORIZED));
 }
}
