package de.elbracht.smp.agent;

import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("agent")
public record AgentProperties(String mode, String serverId, Path tokenFile, String container, String dockerContext,
                              int minecraftPort, Duration dockerTimeout, Duration minecraftTimeout) {
    public AgentProperties {
        if (!"disconnected".equals(mode) && !"docker-read-only".equals(mode) && !"docker-test-control".equals(mode)) throw new IllegalStateException("Unbekannter Agentmodus");
        if ("docker-test-control".equals(mode) && !"elbracht-smp-control-test-minecraft".equals(container))
            throw new IllegalStateException("Teststeuerung ist ausschließlich für den fest benannten Testcontainer zulässig");
        if (serverId == null || !serverId.matches("[A-Za-z0-9_-]{1,64}") || container == null || !container.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}")
            || dockerContext == null || !dockerContext.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,63}")) throw new IllegalStateException("Ungültige feste Agentzuordnung");
        if (minecraftPort < 1024 || minecraftPort > 65535 || tokenFile == null) throw new IllegalStateException("Agentport und Secret-Datei müssen konfiguriert sein");
        for (Duration timeout : new Duration[] {dockerTimeout, minecraftTimeout})
            if (timeout == null || timeout.compareTo(Duration.ofMillis(50)) < 0 || timeout.compareTo(Duration.ofSeconds(10)) > 0)
                throw new IllegalStateException("Agent-Zeitgrenzen müssen zwischen 50 ms und 10 s liegen");
    }
}
