package de.elbracht.smp.servers;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

@Configuration
@EnableConfigurationProperties(AgentProperties.class)
public class AgentConfiguration {
    @Bean
    HostAgent hostAgent(AgentProperties properties, Environment environment,
                       com.fasterxml.jackson.databind.ObjectMapper mapper) {
        return switch (properties.mode()) {
            case "disconnected" -> new DisconnectedHostAgent();
            case "http-read-only" -> new HttpStatusHostAgent(properties, mapper, Clock.systemUTC(),
                environment.acceptsProfiles(Profiles.of("local")) && !environment.acceptsProfiles(Profiles.of("prod")));
            default -> throw new IllegalStateException("Unbekannter portal.agent.mode");
        };
    }
}
