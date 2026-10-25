package de.elbracht.smp.servers;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("portal.agent")
public record AgentProperties(
    @DefaultValue("disconnected") String mode,
    URI baseUrl,
    Path tokenFile,
    @DefaultValue("main") String serverId,
    @DefaultValue("2s") Duration connectTimeout,
    @DefaultValue("3s") Duration requestTimeout,
    @DefaultValue("30s") Duration maxStatusAge
) {}
