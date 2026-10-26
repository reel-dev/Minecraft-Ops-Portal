package de.elbracht.smp;
import de.elbracht.smp.identity.LogStreamAuthorization;
import jakarta.servlet.DispatcherType;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.assertj.core.api.Assertions.*;
class LogStreamAuthorizationTest {
 @Test void initialRequestsOtherEndpointsAndUnmarkedDispatchesNeverBypassAuthentication(){
  var request=new MockHttpServletRequest("GET","/api/v1/servers/main/logs/stream");request.setServletPath("/api/v1/servers/main/logs/stream");
  assertThat(LogStreamAuthorization.isAuthorizedCompletion(request)).isFalse();request.setDispatcherType(DispatcherType.ASYNC);
  assertThat(LogStreamAuthorization.isAuthorizedCompletion(request)).isFalse();request.setAttribute(LogStreamAuthorization.AUTHORIZED,Boolean.TRUE);
  assertThat(LogStreamAuthorization.isAuthorizedCompletion(request)).isTrue();request.setServletPath("/api/v1/servers/main/configuration");assertThat(LogStreamAuthorization.isAuthorizedCompletion(request)).isFalse();
  request.setServletPath("/api/v1/servers/main/logs/stream");request.setMethod("POST");assertThat(LogStreamAuthorization.isAuthorizedCompletion(request)).isFalse();request.setMethod("GET");request.setDispatcherType(DispatcherType.REQUEST);assertThat(LogStreamAuthorization.isAuthorizedCompletion(request)).isFalse();
 }
}
