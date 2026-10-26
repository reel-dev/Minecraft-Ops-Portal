package de.elbracht.smp.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@EnableConfigurationProperties(AgentProperties.class)
@org.springframework.scheduling.annotation.EnableScheduling
public class HostAgentApplication {
    public static void main(String[] args) { SpringApplication.run(HostAgentApplication.class, args); }
}
