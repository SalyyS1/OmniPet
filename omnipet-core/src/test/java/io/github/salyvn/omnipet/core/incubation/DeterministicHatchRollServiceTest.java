package io.github.salyvn.omnipet.core.incubation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.github.salyvn.omnipet.core.domain.DisplayDefinition;
import io.github.salyvn.omnipet.core.domain.HeadIcon;
import io.github.salyvn.omnipet.core.domain.PetDefinition;
import io.github.salyvn.omnipet.core.domain.PetTier;
import io.github.salyvn.omnipet.core.domain.incubation.EggDefinition;
import io.github.salyvn.omnipet.core.domain.incubation.HatchCandidate;
import io.github.salyvn.omnipet.core.persistence.RegistrySnapshot;

class DeterministicHatchRollServiceTest {
    private final DeterministicHatchRollService rolls = new DeterministicHatchRollService();

    @Test
    void sameSeedAndInputsReplayExactly() {
        UUID incubationId = UUID.randomUUID();

        var first = rolls.roll(incubationId, IncubationTestFixtures.egg(10_000),
                IncubationTestFixtures.registry(), 123456789L);
        var second = rolls.roll(incubationId, IncubationTestFixtures.egg(10_000),
                IncubationTestFixtures.registry(), 123456789L);

        assertEquals(first, second);
        assertEquals(DeterministicHatchRollService.ALGORITHM_ID, first.outcome().algorithmId());
    }

    @Test
    void tenThousandFixturesRemainInsideRarityAndStatBounds() {
        for (long seed = 0; seed < 10_000; seed++) {
            var outcome = rolls.roll(UUID.nameUUIDFromBytes(Long.toString(seed).getBytes()),
                    IncubationTestFixtures.egg(10_000), IncubationTestFixtures.registry(), seed).outcome();
            if (outcome.rarityId().equals("COMMON")) {
                assertTrue(outcome.qualityScore() >= 0 && outcome.qualityScore() <= 60);
                assertEquals(10_000, outcome.totalActiveMillis());
            } else {
                assertEquals("LEGENDARY", outcome.rarityId());
                assertTrue(outcome.qualityScore() >= 80 && outcome.qualityScore() <= 100);
                assertEquals(15_000, outcome.totalActiveMillis());
            }
            assertTrue(outcome.realizedStats().stream()
                    .filter(stat -> stat.id().equals("attack_damage"))
                    .allMatch(stat -> stat.value() >= 5 && stat.value() <= 20));
            assertTrue(outcome.realizedStats().stream()
                    .filter(stat -> stat.id().equals("movement_speed"))
                    .allMatch(stat -> stat.value() >= 0.05 && stat.value() <= 0.25));
        }
    }

    @Test
    void snapshotsReleasePolicyIntoTheDeterministicOutcome() {
        RegistrySnapshot base = IncubationTestFixtures.registry();
        PetDefinition source = base.definitions().get("ember_fox");
        Map<String, Object> raw = new LinkedHashMap<>(source.rawNode());
        raw.put("release", Map.of("mode", "RECYCLE", "rewards", Map.of(
                "materials", Map.of("BONE", 3))));
        PetDefinition definition = new PetDefinition(
                source.id(), source.revision(), source.tier(), source.icon(), source.display(), raw);

        var outcome = rolls.roll(
                UUID.randomUUID(), IncubationTestFixtures.egg(10_000),
                new RegistrySnapshot(base.generation(), Map.of(definition.id(), definition)), 42).outcome();

        assertEquals(raw.get("release"), outcome.extensions().get("release"));
    }

    @Test
    void rejectsInvalidPoolsWeightsProfilesAndDurationsBeforeMutation() {
        assertThrows(IllegalArgumentException.class, () -> new EggDefinition(
                "bad", PetTier.D, 1000, List.of(), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new HatchCandidate("ember_fox", -1, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new HatchCandidate("ember_fox", Double.NaN, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> IncubationTestFixtures.egg(0));

        PetDefinition zeroRarity = new PetDefinition(
                "ember_fox",
                1,
                PetTier.D,
                new HeadIcon("BASE64", "texture"),
                new DisplayDefinition(DisplayDefinition.Provider.HEAD, null),
                Map.of("rarity", Map.of("bands", List.of(
                        Map.of("id", "COMMON", "qualityMin", 0, "qualityMax", 100,
                                "weight", 0, "hatchMultiplier", 1)))));
        RegistrySnapshot invalid = new RegistrySnapshot(1, Map.of("ember_fox", zeroRarity));
        assertThrows(IllegalArgumentException.class, () -> rolls.roll(
                UUID.randomUUID(), IncubationTestFixtures.egg(1000), invalid, 1));
    }
}
