package io.github.salyvn.omnipet.core.migration.legacy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class LegacyPlayerStateReaderTest {
    private final LegacyPlayerStateReader reader = new LegacyPlayerStateReader();

    @Test
    void acceptsSchemaOneAndReturnsCurrentEnvelope() {
        UUID playerId = UUID.randomUUID();

        var result = reader.read("uuid: " + playerId + "\npets: []\ncapacity: 4\n");

        assertEquals(1, result.report().sourceSchemaVersion());
        assertEquals(3, result.envelope().schemaVersion());
        assertEquals(4, result.envelope().state().vaultCapacity());
        assertTrue(result.report().migrated());
    }

    @Test
    void rejectsSchemaTwoAndCurrentSchema() {
        UUID playerId = UUID.randomUUID();
        String schemaTwo = "schemaVersion: 2\nuuid: " + playerId + "\npets: []\n";
        String schemaThree = "schemaVersion: 3\nuuid: " + playerId + "\npets: []\n";

        assertThrows(IllegalArgumentException.class, () -> reader.read(schemaTwo));
        assertThrows(IllegalArgumentException.class, () -> reader.read(schemaThree));
    }
}
