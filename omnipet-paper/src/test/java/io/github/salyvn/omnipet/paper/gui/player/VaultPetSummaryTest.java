package io.github.salyvn.omnipet.paper.gui.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
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
    void aPetWithNoComponentsAtAllYieldsNothingAndDoesNotThrow() {
        VaultPetSummary summary = assertDoesNotThrow(() -> VaultPetSummary.of(pet(Map.of())));

        assertTrue(summary.level().isEmpty());
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
        VaultPetSummary summary = assertDoesNotThrow(() -> VaultPetSummary.of(pet(Map.of(
                "progression", "not-a-map",
                "hatching", 42))));

        assertTrue(summary.level().isEmpty());
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
    void aMissingLevelStillAllowsRarityToRender() {
        VaultPetSummary summary = VaultPetSummary.of(pet(Map.of(
                "progression", Map.of("experience", 10.0),
                "hatching", Map.of("rarityId", "RARE"))));

        assertTrue(summary.level().isEmpty());
        assertEquals("RARE", summary.rarity().orElseThrow());
    }

    private static PetInstance pet(Map<String, Object> components) {
        return new PetInstance(UUID.randomUUID(), "wolf", 1, new LinkedHashMap<>(components), Map.of());
    }
}
