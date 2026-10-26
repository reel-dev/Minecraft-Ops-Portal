package de.elbracht.smp.agent;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("agent.test-whitelist")
public record TestWhitelistProperties(@DefaultValue("false") boolean enabled, Path dataDirectory,
                                      @DefaultValue("directory") String source) {}
