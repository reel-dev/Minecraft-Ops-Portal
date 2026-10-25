package de.elbracht.smp.agent;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
public class SecurityConfig {
    @Bean
    SecurityFilterChain security(HttpSecurity http, AgentProperties properties) throws Exception {
        final byte[] expected;
        try (var stream = Files.newInputStream(properties.tokenFile())) {
            byte[] raw = stream.readNBytes(4097);
            String token = new String(raw, StandardCharsets.UTF_8).strip();
            if (raw.length > 4096 || !token.matches("[A-Za-z0-9._~-]{32,4096}")) throw new IOException();
            expected = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
        } catch (Exception error) { throw new IllegalStateException("Agent-Token-Datei fehlt oder ist ungültig"); }
        var bearer = new OncePerRequestFilter() {
            @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
                String header = request.getHeader("Authorization");
                boolean accepted = false;
                if (header != null && header.startsWith("Bearer ") && header.length() <= 4103) {
                    try { accepted = MessageDigest.isEqual(expected, MessageDigest.getInstance("SHA-256").digest(header.substring(7).getBytes(StandardCharsets.UTF_8))); }
                    catch (Exception ignored) { }
                }
                if (!accepted) {
                    response.setStatus(401); response.setContentType("application/json"); response.getWriter().write("{\"code\":\"AGENT_UNAUTHORIZED\"}"); return;
                }
                SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                    "portal", null, List.of(new SimpleGrantedAuthority("ROLE_AGENT"))));
                chain.doFilter(request, response);
            }
        };
        return http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // Maschinen-API mit Bearer-Header, ohne Cookieauthentisierung; alle Nicht-GETs bleiben verboten.
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> {
                auth.requestMatchers(HttpMethod.GET, "/agent/v1/servers/*/status").hasRole("AGENT");
                if ("docker-test-control".equals(properties.mode())) auth.requestMatchers(HttpMethod.POST, "/agent/v1/servers/*/operations").hasRole("AGENT");
                auth.anyRequest().denyAll();
            })
            .addFilterBefore(bearer, UsernamePasswordAuthenticationFilter.class)
            .build();
    }
}
