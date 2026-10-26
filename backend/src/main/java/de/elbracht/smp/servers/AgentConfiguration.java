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
                       com.fasterxml.jackson.databind.ObjectMapper mapper,
                       @org.springframework.beans.factory.annotation.Value("${portal.test-whitelist-enabled:false}") boolean whitelistEnabled,
                       @org.springframework.beans.factory.annotation.Value("${portal.test-op-enabled:false}") boolean operatorEnabled,
                       @org.springframework.beans.factory.annotation.Value("${portal.test-console-enabled:false}") boolean consoleEnabled,
                       @org.springframework.beans.factory.annotation.Value("${portal.test-backup-enabled:false}") boolean backupsEnabled,
                       @org.springframework.beans.factory.annotation.Value("${portal.test-configuration-enabled:false}") boolean configurationEnabled,
                       @org.springframework.beans.factory.annotation.Value("${portal.test-restore-enabled:false}") boolean restoreEnabled,
                       @org.springframework.beans.factory.annotation.Value("${portal.test-mods-enabled:false}") boolean modsEnabled) {
        return switch (properties.mode()) {
            case "disconnected" -> new DisconnectedHostAgent();
            case "http-read-only" -> new HttpStatusHostAgent(properties, mapper, Clock.systemUTC(),
                environment.acceptsProfiles(Profiles.of("local")) && !environment.acceptsProfiles(Profiles.of("prod")));
            case "http-test-control" -> {
                if (!environment.acceptsProfiles(Profiles.of("local")) || !environment.acceptsProfiles(Profiles.of("test-server")) || environment.acceptsProfiles(Profiles.of("prod")))
                    throw new IllegalStateException("Teststeuerung erfordert ausschließlich local,test-server ohne prod");
                yield new HttpTestControlHostAgent(properties, mapper, whitelistEnabled).withOperatorControl(operatorEnabled).withConsoleControl(consoleEnabled).withBackups(backupsEnabled).withConfiguration(configurationEnabled).withRestoreControl(restoreEnabled).withMods(modsEnabled);
            }
            default -> throw new IllegalStateException("Unbekannter portal.agent.mode");
        };
    }
}
