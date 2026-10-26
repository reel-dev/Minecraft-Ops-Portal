package de.elbracht.smp.agent;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
@ConfigurationProperties("agent.test-restore")
public record RestoreProperties(boolean enabled,@DefaultValue("none")String fault){
 public RestoreProperties{if(!java.util.Set.of("none","after-swap").contains(fault))throw new IllegalStateException("Ungültige Testfehlerstelle");}
}
