package de.elbracht.smp.agent;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import static org.assertj.core.api.Assertions.*;

class OperationStoreTest {
    @Test void additiveSchemaKeepsOldLifecycleLedgerEntries() {
        var database = new EmbeddedDatabaseBuilder().generateUniqueName(true).setType(EmbeddedDatabaseType.H2).addScript("schema.sql").build();
        try {
            var jdbc = new JdbcTemplate(database); var id = UUID.randomUUID();
            jdbc.execute("ALTER TABLE agent_operation DROP COLUMN target_name");
            jdbc.update("INSERT INTO agent_operation (id,operation_type,state,updated_at) VALUES (?, 'STOP', 'SUCCEEDED', CURRENT_TIMESTAMP)", id);
            new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(new org.springframework.core.io.ClassPathResource("schema.sql")).execute(database);
            var entry = new OperationStore(jdbc).find(id);
            assertThat(entry.state()).isEqualTo("SUCCEEDED"); assertThat(entry.targetName()).isNull();
        } finally { database.shutdown(); }
    }
    @Test void durableLedgerSurvivesServiceRecreationAndBlocksUnknownOperations() {
        var database = new EmbeddedDatabaseBuilder().generateUniqueName(true).setType(EmbeddedDatabaseType.H2).addScript("schema.sql").build();
        try {
            var jdbc = new JdbcTemplate(database); var store = new OperationStore(jdbc); var id = UUID.randomUUID();
            store.begin(id, OperationStore.Type.RESTART);
            assertThatThrownBy(() -> store.begin(UUID.randomUUID(), OperationStore.Type.START)).isInstanceOf(OperationRejected.class);
            var recovered = new OperationStore(jdbc); recovered.recover();
            assertThat(recovered.find(id).state()).isEqualTo("UNKNOWN");
            assertThatThrownBy(() -> recovered.begin(UUID.randomUUID(), OperationStore.Type.START)).isInstanceOf(OperationRejected.class);
        } finally { database.shutdown(); }
    }
}
