package de.elbracht.smp.agent;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class ConfigurationReconciliationTest {
 @Test void unchangedFileCanResolveAConfigFailureWithoutAnyMutation()throws Exception{
  var id=UUID.randomUUID();var store=mock(OperationStore.class);var control=mock(DockerControl.class);var config=mock(TestConfigurationService.class);
  when(store.find(id)).thenReturn(new OperationStore.Result(id,OperationStore.Type.CONFIG_APPLY,"UNKNOWN"));
  when(store.configuration(id)).thenReturn("{\"revision\":\""+"a".repeat(64)+"\",\"values\":{\"spawn-protection\":\"12\"}}");
  when(config.read()).thenReturn(new TestConfigurationService.Snapshot("a".repeat(64),List.of(new TestConfigurationService.Field("spawn-protection","Spawnschutz","integer",0,128,List.of(),"16",false)),true,true,"server.properties"));
  when(store.reconcile(eq(id),eq("FAILED"),anyString())).thenReturn(new OperationStore.Result(id,OperationStore.Type.CONFIG_APPLY,"FAILED"));
  var service=new OperationService(StatusServiceTest.properties(25585,"docker-test-control"),store,control,null,null,null);ReflectionTestUtils.setField(service,"configuration",config);ReflectionTestUtils.setField(service,"mapper",new ObjectMapper());
  assertThat(service.reconcile(id).state()).isEqualTo("FAILED");verify(control).verifyTarget();verify(control,never()).writeTestProperties(any());verify(control,never()).start();verify(control,never()).stop();
 }
}
