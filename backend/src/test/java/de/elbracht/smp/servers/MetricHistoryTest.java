package de.elbracht.smp.servers;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class MetricHistoryTest {
 HostAgent.Diagnostics source(Instant time,String cpu)throws Exception{return new HostAgent.Diagnostics("main",time,new ObjectMapper().readTree("{\"CPUPerc\":\""+cpu+"\",\"MemUsage\":\"1.5GiB / 3GiB\",\"NetIO\":\"1.2MB / 9kB\",\"BlockIO\":\"0B / 4.2MiB\",\"PIDs\":\"32\"}"),null);}
 @Test void realMultiCorePercentAndDockerUnitsRemainFaithful()throws Exception {var now=Instant.now();var sample=MetricHistory.parse(source(now,"212.5%"),now);assertThat(sample).isNotNull();assertThat(sample.cpuPercent()).isEqualTo(212.5);assertThat(sample.memoryUsed()).isEqualTo(1610612736L);assertThat(sample.networkReceived()).isEqualTo(1200000);assertThat(sample.networkSent()).isEqualTo(9000);assertThat(sample.diskRead()).isZero();}
 @Test void unavailableMalformedAndStaleValuesNeverBecomeZeroSamples()throws Exception {var now=Instant.now();assertThat(MetricHistory.parse(source(now,"unknown"),now)).isNull();assertThat(MetricHistory.parse(source(now.minusSeconds(61),"12.0%"),now)).isNull();assertThat(MetricHistory.parse(source(now.plusSeconds(11),"12.0%"),now)).isNull();assertThat(MetricHistory.parse(null,now)).isNull();}
 @Test void negativeOrUnknownUnitsAndOverflowAreRejected(){for(String value:java.util.List.of("-1MB","InfinityB","12EB","999999999999999TB","http://host"))assertThatThrownBy(()->MetricHistory.bytes(value)).isInstanceOf(RuntimeException.class);assertThat(MetricHistory.bytes("1GiB")).isEqualTo(1073741824);assertThat(MetricHistory.bytes("1GB")).isEqualTo(1000000000);}
 @Test void fractionalCurrentDockerUnitsAreAccepted()throws Exception{var now=Instant.now();var data=new ObjectMapper().readTree("{\"CPUPerc\":\"0.92%\",\"MemUsage\":\"1.271GiB / 3GiB\",\"NetIO\":\"63.6kB / 10.9kB\",\"BlockIO\":\"73.6MB / 3.86MB\",\"PIDs\":\"39\"}");assertThat(MetricHistory.parse(new HostAgent.Diagnostics("main",now,data,null),now)).isNotNull();}
}
