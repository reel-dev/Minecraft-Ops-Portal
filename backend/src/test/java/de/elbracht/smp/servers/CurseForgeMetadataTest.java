package de.elbracht.smp.servers;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class CurseForgeMetadataTest {
 @Test void exactFabricAvailableFileOfTheSameProjectIsRequired()throws Exception{
  var mapper=new ObjectMapper();String json="{\"id\":12,\"modId\":7,\"fileLength\":100,\"isAvailable\":true,\"gameVersions\":[\"26.3\",\"Fabric\"]}";
  assertThat(CurseForgeMetadata.matches(mapper.readTree(json),"26.3",7)).isTrue();
  for(String wrong:new String[]{json.replace("26.3","26.2"),json.replace("Fabric","Forge"),json.replace("true","false"),json.replace("\"modId\":7","\"modId\":8")})assertThat(CurseForgeMetadata.matches(mapper.readTree(wrong),"26.3",7)).isFalse();
 }
}
