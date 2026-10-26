package de.elbracht.smp.agent;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class TestConfigurationServiceTest {
 @Test void mergePreservesUnknownPropertiesAndCommentsAndEscapesUnicode()throws Exception{
  byte[] before="# Existing comment\r\nrcon.password=opaque-test-value\r\nspawn-protection=16\r\nmotd=Original\r\n".getBytes(StandardCharsets.ISO_8859_1);
  byte[] after=TestConfigurationService.merge(before,Map.of("spawn-protection","12","motd","Grüße"));String text=new String(after,StandardCharsets.ISO_8859_1);
  assertThat(text).contains("# Existing comment\r\n","rcon.password=opaque-test-value\r\n","spawn-protection=12\r\n","motd=Gr\\u00fc\\u00dfe");
  var properties=new java.util.Properties();properties.load(new java.io.ByteArrayInputStream(after));assertThat(properties.getProperty("motd")).isEqualTo("Grüße");
 }
 @Test void credentialsAndInvalidValuesCannotEnterTheEditableSchema(){
  for(var values:java.util.List.of(Map.of("rcon.password","changed"),Map.of("view-distance","999"),Map.of("pvp","yes"),Map.of("motd","line\nnext"),Map.of("difficulty","invalid")))
   assertThatThrownBy(()->TestConfigurationService.validate(values)).isInstanceOf(java.io.IOException.class);
 }
 @Test void duplicateKeysAreRejectedRatherThanSilentlyEditingAmbiguousConfig(){
  assertThatThrownBy(()->TestConfigurationService.merge("pvp=true\npvp=false\n".getBytes(StandardCharsets.ISO_8859_1),Map.of("pvp","false"))).isInstanceOf(java.io.IOException.class);
 }
 @Test void existingUtf8TextIsPreservedWhenAnUnrelatedSettingChanges()throws Exception{
  String before="# Grüße\nmotd=Elbracht SMP – Test\nspawn-protection=16\n";
  String after=new String(TestConfigurationService.merge(before.getBytes(StandardCharsets.UTF_8),Map.of("spawn-protection","12")),StandardCharsets.UTF_8);
  assertThat(after).contains("# Grüße\n","motd=Elbracht SMP – Test\n","spawn-protection=12\n");
 }
}
