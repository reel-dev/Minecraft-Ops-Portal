package de.elbracht.smp.servers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.*;

class AgentConfigurationTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
        .withUserConfiguration(AgentConfiguration.class).withBean(ObjectMapper.class, ObjectMapper::new);

    @Test void defaultNeverReadsSecretOrConnectsEvenWithEndpointPresent() {
        context.withPropertyValues("portal.agent.base-url=https://example.invalid", "portal.agent.token-file=does-not-exist")
            .run(app -> {
                assertThat(app).hasNotFailed();
                assertThat(app.getBean(HostAgent.class)).isInstanceOf(DisconnectedHostAgent.class);
                assertThat(app.getBean(HostAgent.class).operationsAvailable()).isFalse();
            });
    }

    @Test void unknownModeFailsClosed() {
        context.withPropertyValues("portal.agent.mode=http-write")
            .run(app -> assertThat(app).hasFailed());
    }

    @Test void localProfileCannotWeakenProductionTransport() {
        context.withInitializer(app -> app.getEnvironment().setActiveProfiles("local", "prod"))
            .withPropertyValues("portal.agent.mode=http-read-only", "portal.agent.base-url=http://127.0.0.1:1", "portal.agent.token-file=unused")
            .run(app -> assertThat(app).hasFailed());
    }
    @Test void mutationAdapterRequiresBothExplicitLocalTestProfilesAndRejectsProduction() {
        for (String[] profiles : new String[][] { {"local"}, {"test-server"}, {"local", "test-server", "prod"} })
            context.withInitializer(app -> app.getEnvironment().setActiveProfiles(profiles))
                .withPropertyValues("portal.agent.mode=http-test-control", "portal.agent.base-url=http://127.0.0.1:8091", "portal.agent.token-file=unused")
                .run(app -> assertThat(app).hasFailed());
    }
}
