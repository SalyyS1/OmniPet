package io.github.salyvn.omnipet.paper.gui.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.github.salyvn.omnipet.core.domain.PetInstance;

/**
 * A renderer must never throw on a malformed pet: one bad legacy component would otherwise blank the
 * whole vault page. Every case here asserts a clean degrade, not an exception.
 */
class VaultPetSummaryTest {
    @Test
    void readsLevelAndRarityFromAFullyPopulatedPet() {
        VaultPetSummary summary = VaultPetSummary.of(pet(Map.of(
                "progression", Map.of("level", 7),
                "hatching", Map.of("rarityId", "LEGENDARY"))));

        assertEquals(7, summary.level().orElseThrow());
        assertEquals("LEGENDARY", summary.rarity().orElseThrow());
    }

    @Test
    void aPetWithNoComponentsAtAllReadsAsLevelOne() {
        // The freshly-hatched case, and the bug this test was changed to catch. Hatching writes hatching,
        // stats, appearance, and release components but no progression node, so every new pet showed no
        // level in the vault until its first cultivation action created one. Absent progression means
        // never-cultivated, which is level 1 -- the same answer ProgressionState.initial gives.
        VaultPetSummary summary = assertDoesNotThrow(() -> VaultPetSummary.of(pet(Map.of())));

        assertEquals(1, summary.level().orElseThrow(),
                "a pet nothing has cultivated is level 1, not a pet with no level");
        assertTrue(summary.rarity().isEmpty());
    }

    @Test
    void aNonNumericLevelIsOmittedRatherThanThrowing() {
        // PetProgressionProjection.read throws on exactly this input, which is why the renderer
        // must not use it.
        VaultPetSummary summary = assertDoesNotThrow(() -> VaultPetSummary.of(
                pet(Map.of("progression", Map.of("level", "seven")))));

        assertTrue(summary.level().isEmpty());
    }

    @Test
    void aFractionalLevelIsOmittedInsteadOfRounded() {
        assertTrue(VaultPetSummary.of(pet(Map.of("progression", Map.of("level", 3.5))))
                .level().isEmpty());
    }

    @Test
    void anIntegerValuedDoubleLevelIsAccepted() {
        assertEquals(4, VaultPetSummary.of(pet(Map.of("progression", Map.of("level", 4.0))))
                .level().orElseThrow());
    }

    @Test
    void anOutOfRangeLevelIsOmitted() {
        assertTrue(VaultPetSummary.of(pet(Map.of("progression", Map.of("level", Long.MAX_VALUE))))
                .level().isEmpty());
    }

    @Test
    void aComponentThatIsNotAMapIsIgnored() {
        // Not a map is not a progression component at all, so it reads the same as absent: level 1.
        VaultPetSummary summary = assertDoesNotThrow(() -> VaultPetSummary.of(pet(Map.of(
                "progression", "not-a-map",
                "hatching", 42))));

        assertEquals(1, summary.level().orElseThrow());
        assertTrue(summary.rarity().isEmpty());
    }

    @Test
    void aBlankOrNonStringRarityIsOmitted() {
        assertTrue(VaultPetSummary.of(pet(Map.of("hatching", Map.of("rarityId", "   "))))
                .rarity().isEmpty());
        assertTrue(VaultPetSummary.of(pet(Map.of("hatching", Map.of("rarityId", 5))))
                .rarity().isEmpty());
    }

    @Test
    void aProgressionNodeWithoutALevelStillAllowsRarityToRender() {
        // The node exists and carries experience but no level. Absent within a present node still means
        // never-levelled, so it reads as 1; the point of the test is that rarity is unaffected either way.
        VaultPetSummary summary = VaultPetSummary.of(pet(Map.of(
                "progression", Map.of("experience", 10.0),
                "hatching", Map.of("rarityId", "RARE"))));

        assertEquals("RARE", summary.rarity().orElseThrow());
    }

    @Test
    void showsTheStatsAPlayerWouldCompareTwoPetsBy() {
        // Comparing two pets was impossible without opening each one's management screen, which is most of
        // what a vault is for. The stats were persisted at hatch all along and simply never read here.
        VaultPetSummary summary = VaultPetSummary.of(pet(Map.of(
                "stats", List.of(
                        Map.of("id", "ATTACK_DAMAGE", "modifierType", "FLAT", "value", 12.0),
                        Map.of("id", "MAX_HEALTH", "modifierType", "FLAT", "value", 40.0)))));

        assertEquals(2, summary.stats().size());
        // Largest first: that is the number someone comparing pets is looking for.
        assertEquals("MAX_HEALTH", summary.stats().get(0).statId());
        assertEquals("ATTACK_DAMAGE", summary.stats().get(1).statId());
        assertEquals(0, summary.hiddenStatCount());
    }

    @Test
    void ranksByMagnitudeSoALargePenaltyIsNotHidden() {
        // Absolute value, not signed. A -40 is as interesting as a +40, and sorting signed would bury the
        // biggest drawback a pet has at the bottom of the list where the cap cuts it off.
        VaultPetSummary summary = VaultPetSummary.of(pet(Map.of(
                "stats", List.of(
                        Map.of("id", "SPEED", "modifierType", "FLAT", "value", 3.0),
                        Map.of("id", "COOLDOWN", "modifierType", "FLAT", "value", -40.0)))));

        assertEquals("COOLDOWN", summary.stats().get(0).statId());
    }

    @Test
    void capsTheListAndSaysHowManyWereLeftOut() {
        // A pet may carry up to 256 stats and lore has to stay readable. Truncating silently would make a
        // stat-heavy pet look weaker than a plain one.
        List<Map<String, Object>> many = new java.util.ArrayList<>();
        for (int index = 0; index < 9; index++) {
            many.add(Map.of("id", "STAT_" + index, "modifierType", "FLAT", "value", (double) index + 1));
        }

        VaultPetSummary summary = VaultPetSummary.of(pet(Map.of("stats", many)));

        assertEquals(VaultPetSummary.MAX_STAT_LINES, summary.stats().size());
        assertEquals(9 - VaultPetSummary.MAX_STAT_LINES, summary.hiddenStatCount());
    }

    @Test
    void tiesAreBrokenSoTwoIdenticalPetsListTheSameOrder() {
        // Otherwise the same pet could render its stats in a different order between openings, which reads
        // as the values changing.
        Map<String, Object> components = Map.of("stats", List.of(
                Map.of("id", "ZETA", "modifierType", "FLAT", "value", 5.0),
                Map.of("id", "ALPHA", "modifierType", "FLAT", "value", 5.0)));

        assertEquals("ALPHA", VaultPetSummary.of(pet(components)).stats().get(0).statId());
        assertEquals("ALPHA", VaultPetSummary.of(pet(components)).stats().get(0).statId());
    }

    @Test
    void anUnreadableStatIsSkippedRatherThanBlankingThePage() {
        VaultPetSummary summary = assertDoesNotThrow(() -> VaultPetSummary.of(pet(Map.of(
                "stats", List.of(
                        Map.of("id", "GOOD", "modifierType", "FLAT", "value", 5.0),
                        Map.of("id", "NO_VALUE", "modifierType", "FLAT"),
                        Map.of("modifierType", "FLAT", "value", 5.0),
                        "not-a-map")))));

        assertEquals(1, summary.stats().size());
        assertEquals("GOOD", summary.stats().get(0).statId());
    }

    @Test
    void aPetWithNoStatsComponentHasNoStatLines() {
        // Silence rather than an empty heading: a legacy pet and a pet whose definition grants no stats are
        // both better served by nothing than by a label with no rows under it.
        assertTrue(VaultPetSummary.of(pet(Map.of())).stats().isEmpty());
        assertTrue(VaultPetSummary.of(pet(Map.of("stats", "not-a-list"))).stats().isEmpty());
    }

    @Test
    void experienceIsShownOnlyOnceThereIsSomeToShow() {
        assertEquals(340.0, VaultPetSummary.of(pet(Map.of(
                "progression", Map.of("level", 3, "experience", 340.0)))).experience().orElseThrow());

        // Zero is not progress, and a row reading "0 exp" on every uncultivated pet is noise.
        assertTrue(VaultPetSummary.of(pet(Map.of(
                "progression", Map.of("level", 1, "experience", 0.0)))).experience().isEmpty());
        assertTrue(VaultPetSummary.of(pet(Map.of())).experience().isEmpty());
        assertTrue(VaultPetSummary.of(pet(Map.of(
                "progression", Map.of("experience", "lots")))).experience().isEmpty());
        // NaN is not asserted here: PetInstance rejects a non-finite number in a raw component at
        // construction, so it cannot reach this reader from stored data. The finiteness check stays in
        // the reader anyway, since it is cheap and the reader does not get to assume its caller.
    }

    private static PetInstance pet(Map<String, Object> components) {
        return new PetInstance(UUID.randomUUID(), "wolf", 1, new LinkedHashMap<>(components), Map.of());
    }
}
