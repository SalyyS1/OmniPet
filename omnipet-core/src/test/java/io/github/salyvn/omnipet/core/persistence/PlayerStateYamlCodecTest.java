package io.github.salyvn.omnipet.core.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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
        assertEquals(4, first.envelope().schemaVersion());
        assertEquals(12, first.envelope().state().vaultCapacity());
        assertEquals(1, first.envelope().state().activeSlotCount());
        assertEquals(
                first.envelope().state().pets().getFirst().id(),
                first.envelope().state().desiredActivePetIds().getFirst());
        assertTrue(first.report().appliedMigrations().contains("CAPACITY_TO_VAULT_CAPACITY"));
        assertTrue(first.report().appliedMigrations().contains("CURRENT_INDEX_TO_DESIRED_ACTIVE_ID"));
        assertFalse(encoded.contains("currentPetIndex:"));
        assertFalse(encoded.contains("\ncapacity:"));
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
                schemaVersion: 4
                uuid: %s
                revision: 0
                pets:
                  - { id: 1e2951ad-ab78-44f8-8242-cdcc81ff3ae1, definitionId: a, definitionRevision: 0, components: {} }
                  - { id: 1e2951ad-ab78-44f8-8242-cdcc81ff3ae1, definitionId: b, definitionRevision: 0, components: {} }
                """.formatted(PLAYER_ID);
        assertThrows(IllegalArgumentException.class, () -> codec.decode(duplicate));
    }

    @Test
    void handlesLegacyCapacitySentinelAndRejectsInvalidCapacity() {
        String nonFinite = "uuid: %s\npets:\n  - { type: pet, components: { stat: .NaN } }\n".formatted(PLAYER_ID);
        assertThrows(IllegalArgumentException.class, () -> codec.decode(nonFinite));
        String sentinel = "uuid: %s\npets: []\ncapacity: -1\n".formatted(PLAYER_ID);
        PlayerMigrationResult sentinelResult = codec.decodeWithReport(sentinel);
        assertEquals(0, sentinelResult.envelope().state().vaultCapacity());
        assertTrue(sentinelResult.report().warnings().stream().anyMatch(value -> value.contains("sentinel")));

        String capacity = "uuid: %s\npets: []\ncapacity: -2\n".formatted(PLAYER_ID);
        assertThrows(IllegalArgumentException.class, () -> codec.decode(capacity));
    }

    @Test
    void migratesSchemaTwoCurrentIndexAndPreservesEgg() {
        String schemaTwo = """
                schemaVersion: 2
                uuid: %s
                revision: 7
                pets:
                  - id: 1e2951ad-ab78-44f8-8242-cdcc81ff3ae1
                    definitionId: fox
                    definitionRevision: 2
                    components: {}
                currentPetIndex: 0
                currentEgg: { type: rare, timeLeft: 15m }
                capacity: 9
                """.formatted(PLAYER_ID);

        PlayerMigrationResult result = codec.decodeWithReport(schemaTwo);

        assertTrue(result.report().migrated());
        assertEquals(9, result.envelope().state().vaultCapacity());
        assertEquals(UUID.fromString("1e2951ad-ab78-44f8-8242-cdcc81ff3ae1"),
                result.envelope().state().desiredActivePetIds().getFirst());
        assertEquals("rare", result.envelope().state().legacyCurrentEgg().get("type"));
    }

    @Test
    void roundTripsSlotEntitlementExtensionsAndExplicitNulls() {
        String current = """
                schemaVersion: 4
                uuid: %s
                revision: 2
                pets: []
                vaultCapacity: 30
                activeSlotCount: 2
                desiredActivePetIds: []
                slotEntitlements:
                  - slot: 2
                    source: PURCHASE
                    referenceId: tx-123
                    providerEvidence: null
                futurePlayerKey: null
                """.formatted(PLAYER_ID);

        PlayerMigrationResult first = codec.decodeWithReport(current);
        PlayerMigrationResult second = codec.decodeWithReport(codec.encode(first.envelope()));

        assertFalse(first.report().migrated());
        assertEquals(first.envelope(), second.envelope());
        assertTrue(second.envelope().state().extensions().containsKey("futurePlayerKey"));
        assertNull(second.envelope().state().extensions().get("futurePlayerKey"));
        assertTrue(second.envelope().state().slotEntitlements().getFirst().extensions().containsKey("providerEvidence"));
        assertNull(second.envelope().state().slotEntitlements().getFirst().extensions().get("providerEvidence"));
    }

    @Test
    void rejectsInvalidDesiredIdsAndDuplicateEntitlementSlots() {
        String missingDesired = """
                schemaVersion: 3
                uuid: %s
                pets: []
                desiredActivePetIds: [1e2951ad-ab78-44f8-8242-cdcc81ff3ae1]
                """.formatted(PLAYER_ID);
        assertThrows(IllegalArgumentException.class, () -> codec.decode(missingDesired));

        String duplicateSlots = """
                schemaVersion: 3
                uuid: %s
                pets: []
                activeSlotCount: 2
                slotEntitlements:
                  - { slot: 2, source: ADMIN, referenceId: one }
                  - { slot: 2, source: PURCHASE, referenceId: two }
                """.formatted(PLAYER_ID);
        assertThrows(IllegalArgumentException.class, () -> codec.decode(duplicateSlots));
    }

    @Test
    void rejectsDesiredAndEntitlementStateBeyondPersistedActiveSlots() {
        String activeOverflow = """
                schemaVersion: 3
                uuid: %s
                pets:
                  - { id: 1e2951ad-ab78-44f8-8242-cdcc81ff3ae1, definitionId: fox, definitionRevision: 0, components: {} }
                  - { id: 32027170-d159-4888-b95e-40f7cf48d4ca, definitionId: wolf, definitionRevision: 0, components: {} }
                activeSlotCount: 1
                desiredActivePetIds:
                  - 1e2951ad-ab78-44f8-8242-cdcc81ff3ae1
                  - 32027170-d159-4888-b95e-40f7cf48d4ca
                """.formatted(PLAYER_ID);
        assertThrows(IllegalArgumentException.class, () -> codec.decode(activeOverflow));

        String entitlementOverflow = """
                schemaVersion: 3
                uuid: %s
                pets: []
                activeSlotCount: 1
                slotEntitlements:
                  - { slot: 2, source: PURCHASE, referenceId: tx-1 }
                """.formatted(PLAYER_ID);
        assertThrows(IllegalArgumentException.class, () -> codec.decode(entitlementOverflow));
    }
}
