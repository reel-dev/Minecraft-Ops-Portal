package de.elbracht.smp.identity;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    private HttpSecurity common(HttpSecurity http, String uiUrl) throws Exception {
        return http.authorizeHttpRequests(auth -> auth
                // Finish only an already authorized SSE response after logout. Neither initial
                // requests nor any other asynchronous endpoint match this server-only marker.
                .requestMatchers(LogStreamAuthorization::isAuthorizedCompletion).permitAll()
                .requestMatchers("/api/v1/auth", "/api/v1/csrf", "/sign-in", "/login", "/error", "/oauth2/**", "/login/**").permitAll()
                .anyRequest().authenticated())
            .csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()))
            .exceptionHandling(errors -> errors
                .defaultAuthenticationEntryPointFor((req, res, e) -> res.sendError(401),
                    req -> req.getServletPath().startsWith("/api/")))
            .logout(logout -> logout.logoutUrl("/api/v1/logout").logoutSuccessHandler((req, res, auth) -> res.setStatus(204)))
            .requestCache(cache -> cache.disable());
    }

    @Bean @Profile("!prod")
    SecurityFilterChain localSecurity(HttpSecurity http, @Value("${portal.ui-url}") String uiUrl) throws Exception {
        return common(http, uiUrl).formLogin(login -> login.loginPage("/sign-in").loginProcessingUrl("/login").failureUrl("/sign-in?error").defaultSuccessUrl(uiUrl, true)).build();
    }

    @Bean @Profile("!prod")
    UserDetailsService localUsers(@Value("${portal.local-password}") String password) {
        if (password.isBlank()) throw new IllegalStateException("PORTAL_LOCAL_PASSWORD muss lokal gesetzt sein.");
        return new InMemoryUserDetailsManager(User.withUsername("local-admin")
            .password("{bcrypt}" + new BCryptPasswordEncoder().encode(password)).roles("LOCAL_ADMIN").build());
    }

    @Bean @Profile("prod")
    SecurityFilterChain productionSecurity(HttpSecurity http, @Value("${portal.ui-url}") String uiUrl) throws Exception {
        return common(http, uiUrl).oauth2Login(login -> login.defaultSuccessUrl(uiUrl, true)).build();
    }
}
