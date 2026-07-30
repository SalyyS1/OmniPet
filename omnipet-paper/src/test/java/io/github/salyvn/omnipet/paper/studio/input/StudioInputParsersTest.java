package io.github.salyvn.omnipet.paper.studio.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

class StudioInputParsersTest {
    @Test
    void parsesStableIdsRangesChanceAndNamespacedSkills() {
        assertEquals("my_pet-1", StudioInputParsers.stableId("my_pet-1"));
        assertEquals(new StudioInputParsers.NumericRange(10, 50), StudioInputParsers.numericRange("10 50"));
        assertEquals(0.25, StudioInputParsers.chance("0.25"));
        assertEquals(List.of("mythicmobs:fire_bolt", "custom:heal"),
                StudioInputParsers.skillIds("mythicmobs:fire_bolt, custom:heal"));
    }

    @Test
    void rejectsMalformedTrailingAndNonFiniteValues() {
        assertThrows(IllegalArgumentException.class, () -> StudioInputParsers.stableId("bad id"));
        assertThrows(IllegalArgumentException.class, () -> StudioInputParsers.numericRange("10"));
        assertThrows(IllegalArgumentException.class, () -> StudioInputParsers.numericRange("50 10"));
        assertThrows(IllegalArgumentException.class, () -> StudioInputParsers.numericRange("NaN 10"));
        assertThrows(IllegalArgumentException.class, () -> StudioInputParsers.chance("1.1"));
        assertThrows(IllegalArgumentException.class, () -> StudioInputParsers.skillIds("mythicmobs:fire_bolt,mythicmobs:fire_bolt"));
        assertThrows(IllegalArgumentException.class, () -> StudioInputParsers.skillIds("fire_bolt"));
    }
}
