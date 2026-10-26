package de.elbracht.smp.agent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class DiagnosticsTest {
    @Test void logsAreTextBoundedAndSensitiveLinesAreDropped() {
        var lines=DiagnosticsController.redactLogs("Done (2s)!\nlogin 192.168.1.20:25565\npassword=private\n<player> private chat\n\u001b[31mWARN\u001b[0m\n"+"x".repeat(3000));
        assertThat(lines.get(0)).contains("Done");assertThat(lines.get(1)).doesNotContain("192.168");
        assertThat(lines.get(2)).doesNotContain("private");assertThat(lines.get(3)).doesNotContain("private");assertThat(lines.get(4)).isEqualTo("WARN");
        assertThat(lines.get(5).length()).isLessThan(2010);
    }
    @Test void metricsWhitelistNeverPassesThroughUnexpectedDockerFields() throws Exception {
        var parsed=DiagnosticsController.metrics("{\"CPUPerc\":\"1.2%\",\"MemUsage\":\"2MiB / 3GiB\",\"MemPerc\":\"2%\",\"NetIO\":\"3MB / 1MB\",\"BlockIO\":\"1MB / 1MB\",\"PIDs\":\"30\",\"Secret\":\"private\"}",new ObjectMapper());
        assertThat(parsed).hasSize(6).doesNotContainKey("Secret");
    }
    @Test void ipv6IsRedactedWithoutRemovingLogClockTimes(){
        var values=DiagnosticsController.redactLogs("[21:50:00] connection [2001:db8::1234]\n[21:50:01] connection ::1\n[21:50:02] download https://user:opaque@example.test/file");
        assertThat(values.get(0)).contains("21:50:00","[IP]").doesNotContain("2001:db8");assertThat(values.get(1)).contains("21:50:01").doesNotContain("::1");assertThat(values.get(2)).doesNotContain("opaque");
    }
    @Test void nearbyMetricReadersShareTheActualMeasurementTimestamp()throws Exception{
        var docker=mock(DockerControl.class);when(docker.readTestStats()).thenReturn("{\"CPUPerc\":\"1.2%\",\"MemUsage\":\"2MiB / 3GiB\",\"MemPerc\":\"2%\",\"NetIO\":\"3MB / 1MB\",\"BlockIO\":\"1MB / 1MB\",\"PIDs\":\"30\"}");var controller=new DiagnosticsController(StatusServiceTest.properties(25585,"docker-test-control"),docker,null,new ObjectMapper());var first=controller.read("main","metrics");var second=controller.read("main","metrics");assertThat(first.getStatusCode().value()).isEqualTo(200);assertThat(second.getBody()).isEqualTo(first.getBody());verify(docker,times(1)).readTestStats();assertThat(controller.read("other","metrics").getStatusCode().value()).isEqualTo(404);
    }
}
