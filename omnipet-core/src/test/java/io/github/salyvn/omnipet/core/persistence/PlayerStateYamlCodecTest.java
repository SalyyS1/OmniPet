package io.github.salyvn.omnipet.core.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class PlayerStateYamlCodecTest {
    private static final String PLAYER_ID = "eb70fc61-28ae-4a9f-9bda-a44e4d68101c";
    private final PlayerStateYamlCodec codec = new PlayerStateYamlCodec();

    @Test
    void migratesLegacyRawFirstAndIsIdempotent() {
        String legacy = """
                uuid: %s
                pets:
                  - type: missing_definition
                    components:
                      hatching: { rarity: 4, seed: 42 }
                      vendorFuture: { nested: [one, two] }
                    vendorPetKey: keep-me
                currentPetIndex: 0
                currentEgg:
                  type: common
                  timeLeft: 1h
                capacity: 12
                futurePlayerKey: { enabled: true }
                """.formatted(PLAYER_ID);

        PlayerMigrationResult first = codec.decodeWithReport(legacy);
        String encoded = codec.encode(first.envelope());
        PlayerMigrationResult second = codec.decodeWithReport(encoded);

        assertTrue(first.report().migrated());
        assertEquals(1, first.report().assignedInstanceIds().size());
        assertFalse(second.report().migrated());
        assertEquals(first.envelope(), second.envelope());
        assertEquals("keep-me", second.envelope().state().pets().getFirst().extensions().get("vendorPetKey"));
        assertTrue(second.envelope().state().extensions().containsKey("futurePlayerKey"));
    }

    @Test
    void stableIdsDoNotChangeWhenLegacyFixtureIsReadAgain() {
        String legacy = "uuid: %s\npets:\n  - type: example_pet\n    components: { custom: { value: 3 } }\n"
                .formatted(PLAYER_ID);

        UUID first = codec.decode(legacy).state().pets().getFirst().id();
        UUID second = codec.decode(legacy).state().pets().getFirst().id();

        assertEquals(first, second);
    }

    @Test
    void rejectsMalformedAndDuplicateInstanceUuids() {
        assertThrows(IllegalArgumentException.class, () -> codec.decode("uuid: nope\npets: []\n"));
        String duplicate = """
                schemaVersion: 2
                uuid: %s
                revision: 0
                pets:
                  - { id: 1e2951ad-ab78-44f8-8242-cdcc81ff3ae1, definitionId: a, definitionRevision: 0, components: {} }
                  - { id: 1e2951ad-ab78-44f8-8242-cdcc81ff3ae1, definitionId: b, definitionRevision: 0, components: {} }
                """.formatted(PLAYER_ID);
        assertThrows(IllegalArgumentException.class, () -> codec.decode(duplicate));
    }

    @Test
    void rejectsNonFiniteRawValuesAndInvalidCapacity() {
        String nonFinite = "uuid: %s\npets:\n  - { type: pet, components: { stat: .NaN } }\n".formatted(PLAYER_ID);
        assertThrows(IllegalArgumentException.class, () -> codec.decode(nonFinite));
        String capacity = "uuid: %s\npets: []\ncapacity: -1\n".formatted(PLAYER_ID);
        assertThrows(IllegalArgumentException.class, () -> codec.decode(capacity));
    }
}
