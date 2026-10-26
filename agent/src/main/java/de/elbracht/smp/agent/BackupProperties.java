package de.elbracht.smp.agent;
import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
@ConfigurationProperties("agent.test-backup")
public record BackupProperties(boolean enabled, Path executable, Path repository, Path passwordFile, Path workspace,boolean bootTestEnabled,boolean retentionEnabled) {
 @org.springframework.boot.context.properties.bind.ConstructorBinding
 public BackupProperties {}
 public BackupProperties(boolean enabled,Path executable,Path repository,Path passwordFile,Path workspace){this(enabled,executable,repository,passwordFile,workspace,false,false);}
 public BackupProperties(boolean enabled,Path executable,Path repository,Path passwordFile,Path workspace,boolean bootTestEnabled){this(enabled,executable,repository,passwordFile,workspace,bootTestEnabled,false);}
}
