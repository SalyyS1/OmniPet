package io.github.salyvn.omnipet.core.studio.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.salyvn.omnipet.core.studio.SkillReference;
import io.github.salyvn.omnipet.core.studio.StatRange;

class StudioInputParsersTest {
    @Test
    void stableIdParserRejectsExtraWhitespaceAndReservedNames() {
        assertEquals("forest_fox", StudioInputParsers.parseStableId("forest_fox"));
        assertThrows(IllegalArgumentException.class, () -> StudioInputParsers.parseStableId("forest fox"));
        assertThrows(IllegalArgumentException.class, () -> StudioInputParsers.parseStableId(" forest_fox"));
        assertThrows(IllegalArgumentException.class, () -> StudioInputParsers.parseStableId("CON"));
    }

    @Test
    void rangeParserRequiresExactlyTwoFiniteOrderedValues() {
        assertEquals(new StatRange(10, 50), StudioInputParsers.parseRange("10 50"));
        assertThrows(IllegalArgumentException.class, () -> StudioInputParsers.parseRange("10"));
        assertThrows(IllegalArgumentException.class, () -> StudioInputParsers.parseRange("10 50 trailing"));
        assertThrows(IllegalArgumentException.class, () -> StudioInputParsers.parseRange("50 10"));
        assertThrows(IllegalArgumentException.class, () -> StudioInputParsers.parseRange("NaN 10"));
        assertThrows(IllegalArgumentException.class, () -> StudioInputParsers.parseRange("10 Infinity"));
    }

    @Test
    void chanceWeightAndDurationEnforceDomainBounds() {
        assertEquals(1, StudioInputParsers.parseChance("1"));
        assertEquals(0, StudioInputParsers.parseWeight("0"));
        assertEquals(Duration.ofSeconds(788645), StudioInputParsers.parsePositiveDuration("1w 2d 3h 4m 5s"));
        assertThrows(IllegalArgumentException.class, () -> StudioInputParsers.parseChance("1.01"));
        assertThrows(IllegalArgumentException.class, () -> StudioInputParsers.parseWeight("-0.1"));
        assertThrows(IllegalArgumentException.class, () -> StudioInputParsers.parsePositiveDuration("0s"));
        assertThrows(IllegalArgumentException.class, () -> StudioInputParsers.parsePositiveDuration("1 hour"));
    }

    @Test
    void skillParserTrimsAndRejectsBlankTrailingOrDuplicateReferences() {
        List<SkillReference> parsed = StudioInputParsers.parseSkillReferences("mythicmobs:dash, custom:pet.heal");

        assertEquals(List.of("mythicmobs", "custom"), parsed.stream().map(SkillReference::provider).toList());
        assertEquals(List.of("dash", "pet.heal"), parsed.stream().map(SkillReference::id).toList());
        assertThrows(UnsupportedOperationException.class, () -> parsed.add(new SkillReference("x", "y")));
        assertThrows(IllegalArgumentException.class, () -> StudioInputParsers.parseSkillReferences("mythicmobs:dash,"));
        assertThrows(IllegalArgumentException.class, () -> StudioInputParsers.parseSkillReferences("mythicmobs:dash, MYTHICMOBS:dash"));
        assertThrows(IllegalArgumentException.class, () -> StudioInputParsers.parseSkillReferences("mythicmobs:"));
    }

    @Test
    void formulaValidatorChecksSyntaxSamplesComplexityAndBounds() {
        assertEquals(25, StudioFormulaValidator.validate("clamp(level * 5, 0, 100)", Map.of("level", 5.0), 0, 100));
        assertThrows(IllegalArgumentException.class, () -> StudioFormulaValidator.validate("level +", Map.of("level", 1.0), 0, 100));
        assertThrows(IllegalArgumentException.class, () -> StudioFormulaValidator.validate("missing * 2", Map.of(), 0, 100));
        assertThrows(IllegalArgumentException.class, () -> StudioFormulaValidator.validate("1 / 0", Map.of(), 0, 100));
        assertThrows(IllegalArgumentException.class, () -> StudioFormulaValidator.validate("level * 5", Map.of("level", 30.0), 0, 100));
    }
}
