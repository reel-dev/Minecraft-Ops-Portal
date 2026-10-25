package de.elbracht.smp.identity;

import java.util.Map;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class IdentityController {
    private final Environment environment;
    public IdentityController(Environment environment) { this.environment = environment; }
    @GetMapping("/api/v1/auth")
    public Map<String, String> auth() {
        return Map.of("loginUrl", environment.acceptsProfiles(Profiles.of("prod")) ? "/oauth2/authorization/portal" : "/login");
    }
    @GetMapping("/api/v1/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("headerName", token.getHeaderName(), "token", token.getToken());
    }
    @GetMapping("/api/v1/me")
    public Map<String, String> me(Authentication auth) { return Map.of("name", auth.getName()); }
}
