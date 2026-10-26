package de.elbracht.smp.agent;

import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("agent.rcon")
public record RconProperties(@DefaultValue("disconnected") String mode,
        @DefaultValue("127.0.0.1") String host, @DefaultValue("25575") int port,
        Path passwordFile, @DefaultValue("1500ms") Duration timeout) {
    public RconProperties {
        if (!"disconnected".equals(mode) && !"read-only".equals(mode) && !"test-control".equals(mode)) throw new IllegalStateException("Ungültiger RCON-Modus");
        // First integration is deliberately loopback-only; no DNS or production network changes.
        if (!"127.0.0.1".equals(host) || port < 1024 || port > 65535 || timeout == null
                || timeout.compareTo(Duration.ofMillis(50)) < 0 || timeout.compareTo(Duration.ofSeconds(5)) > 0
                || (!"disconnected".equals(mode) && passwordFile == null))
            throw new IllegalStateException("RCON benötigt lokale feste Zuordnung, Secret-Datei und begrenzte Laufzeit");
    }
}
