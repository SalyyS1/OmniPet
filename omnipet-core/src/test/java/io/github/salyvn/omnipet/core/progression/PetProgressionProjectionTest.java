package io.github.salyvn.omnipet.core.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.github.salyvn.omnipet.core.domain.PetInstance;

class PetProgressionProjectionTest {
    @Test
    void missingComponentUsesDefaultsAndRoundTripPreservesOtherData() {
        PetInstance pet = new PetInstance(UUID.randomUUID(), "ember_fox", 4,
                Map.of("legacy", Map.of("value", 7)), Map.of("custom", true));
        ProgressionState initial = PetProgressionProjection.read(pet, 40, 1_000);
        PetInstance updated = PetProgressionProjection.write(pet,
                new ProgressionState(3, 12.5, 1, 25, 2_000, Map.of("branch", "fire")));
        ProgressionState decoded = PetProgressionProjection.read(updated, 0, 0);

        assertEquals(ProgressionState.initial(40, 1_000), initial);
        assertEquals(3, decoded.level());
        assertEquals("fire", decoded.extensions().get("branch"));
        assertEquals(Map.of("value", 7), updated.rawComponents().get("legacy"));
        assertEquals(true, updated.extensions().get("custom"));
    }

    @Test
    void malformedPersistedProgressionFailsClosed() {
        PetInstance pet = new PetInstance(UUID.randomUUID(), "ember_fox", 4,
                Map.of(PetProgressionProjection.COMPONENT_KEY, Map.of(
                        "level", 1.5,
                        "experience", 0,
                        "evolution", 0,
                        "stamina", 10,
                        "lastStaminaEpochMillis", 0)), Map.of());

        assertThrows(IllegalArgumentException.class, () -> PetProgressionProjection.read(pet, 10, 0));
    }
}
