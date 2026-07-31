package io.github.salyvn.omnipet.core.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.github.salyvn.omnipet.core.domain.HeadIcon;
import io.github.salyvn.omnipet.core.domain.PetTier;
import io.github.salyvn.omnipet.core.domain.PlayerState;
import io.github.salyvn.omnipet.core.domain.PlayerStateEnvelope;
import io.github.salyvn.omnipet.core.domain.incubation.IncubationOutcome;
import io.github.salyvn.omnipet.core.domain.incubation.IncubationState;
import io.github.salyvn.omnipet.core.domain.incubation.IncubationStatus;
import io.github.salyvn.omnipet.core.domain.incubation.RealizedStat;
import io.github.salyvn.omnipet.core.studio.StatModifierType;

class PlayerIncubationYamlCodecTest {
    private final PlayerStateYamlCodec codec = new PlayerStateYamlCodec();

    @Test
    void schemaFourRoundTripsTypedOutcomeAndUnknownNodesWithoutRerolling() {
        UUID playerId = UUID.randomUUID();
        UUID incubationId = UUID.randomUUID();
        UUID token = UUID.randomUUID();
        IncubationOutcome outcome = new IncubationOutcome(
                UUID.randomUUID(),
                "ember_fox",
                7,
                PetTier.A,
                new HeadIcon("BASE64", "texture-value"),
                Map.of("catalogId", "head-42"),
                "LEGENDARY",
                92.5,
                -1942,
                "splitmix64-v1",
                List.of(new RealizedStat(
                        "mythiclib.attack_damage", StatModifierType.FLAT, 18.75, Map.of("provider", "MythicLib"))),
                120_000,
                Map.of("futureOutcome", List.of("keep")));
        IncubationState incubation = new IncubationState(
                incubationId,
                "tier_a_egg",
                outcome,
                90_000,
                IncubationStatus.INCUBATING,
                List.of(token),
                Map.of("futureIncubation", true));
        PlayerStateEnvelope first = new PlayerStateEnvelope(
                PlayerStateEnvelope.CURRENT_SCHEMA_VERSION,
                PlayerState.empty(playerId).withIncubation(incubation));

        PlayerStateEnvelope second = codec.decode(codec.encode(first));

        assertEquals(first, second);
        assertEquals(incubationId, second.state().incubation().id());
        assertEquals(-1942, second.state().incubation().outcome().seed());
        assertTrue(second.state().incubation().extensions().containsKey("futureIncubation"));
        assertTrue(second.state().incubation().outcome().extensions().containsKey("futureOutcome"));
        assertEquals("head-42", second.state().incubation().outcome().iconExtensions().get("catalogId"));
    }

    @Test
    void rejectsMalformedIncubationAndDuplicateActionTokens() {
        UUID playerId = UUID.randomUUID();
        UUID incubationId = UUID.randomUUID();
        UUID petId = UUID.randomUUID();
        UUID token = UUID.randomUUID();
        String malformed = """
                schemaVersion: 4
                uuid: %s
                pets: []
                incubation:
                  id: %s
                  eggId: tier_d_egg
                  remainingActiveMillis: 10
                  status: INCUBATING
                  appliedActionTokens: [%s, %s]
                  outcome:
                    petInstanceId: %s
                    definitionId: fox
                    definitionRevision: 1
                    tier: D
                    icon: { source: BASE64, value: texture }
                    rarityId: COMMON
                    qualityScore: .NaN
                    seed: 42
                    algorithmId: splitmix64-v1
                    realizedStats: []
                    totalActiveMillis: 100
                """.formatted(playerId, incubationId, token, token, petId);

        assertThrows(IllegalArgumentException.class, () -> codec.decode(malformed));
    }

    @Test
    void schemaThreeArbitraryIncubationNodeIsPreservedWithoutTypedParsing() {
        UUID playerId = UUID.randomUUID();
        String schemaThree = """
                schemaVersion: 3
                uuid: %s
                pets: []
                incubation:
                  vendor: old-plugin
                  arbitrary: [one, two]
                legacySchemaNodes:
                  incubation: { keep: first }
                  incubationFromSchema1To3: { keep: second }
                """.formatted(playerId);

        PlayerMigrationResult first = codec.decodeWithReport(schemaThree);
        PlayerMigrationResult second = codec.decodeWithReport(codec.encode(first.envelope()));

        assertEquals(4, first.envelope().schemaVersion());
        assertNull(first.envelope().state().incubation());
        assertTrue(first.report().appliedMigrations().contains("PRESERVE_LEGACY_INCUBATION_NODE"));
        Map<?, ?> legacyNodes = (Map<?, ?>) first.envelope().state().extensions().get("legacySchemaNodes");
        assertEquals(Map.of("keep", "first"), legacyNodes.get("incubation"));
        assertEquals(Map.of("keep", "second"), legacyNodes.get("incubationFromSchema1To3"));
        assertEquals(Map.of("vendor", "old-plugin", "arbitrary", List.of("one", "two")),
                legacyNodes.get("incubationFromSchema1To3_2"));
        assertEquals(first.envelope(), second.envelope());
        assertFalse(second.report().migrated());
    }
}
