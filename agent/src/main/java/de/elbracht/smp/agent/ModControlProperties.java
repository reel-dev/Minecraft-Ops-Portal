package de.elbracht.smp.agent;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
@ConfigurationProperties("agent.test-mods")
public record ModControlProperties(boolean enabled,boolean fixturesEnabled,@DefaultValue("none")String fault){
 public ModControlProperties{if(!java.util.Set.of("none","after-swap").contains(fault))throw new IllegalStateException("Ungültige lokale Modfehlerstelle");}
}
