package de.elbracht.smp.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class TestRconBindingTest {
    @Test void onlySingleExactLoopbackRconBindingCanAuthorizeTestMutation() throws Exception {
        var mapper = new ObjectMapper();
        String valid = "{\"25575/tcp\":[{\"HostIp\":\"127.0.0.1\",\"HostPort\":\"25595\"}]}";
        assertThat(TestDockerControl.rconBindingMatches(mapper.readTree(valid), 25595)).isTrue();
        for (String invalid : new String[]{valid.replace("127.0.0.1", "0.0.0.0"), valid.replace("25595", "25575"), "{}",
                "{\"25575/tcp\":[{\"HostIp\":\"127.0.0.1\",\"HostPort\":\"25595\"},{\"HostIp\":\"::\",\"HostPort\":\"25595\"}]}"})
            assertThat(TestDockerControl.rconBindingMatches(mapper.readTree(invalid), 25595)).isFalse();
    }
}
