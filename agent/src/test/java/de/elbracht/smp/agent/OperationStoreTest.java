package de.elbracht.smp.agent;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import static org.assertj.core.api.Assertions.*;

class OperationStoreTest {
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
