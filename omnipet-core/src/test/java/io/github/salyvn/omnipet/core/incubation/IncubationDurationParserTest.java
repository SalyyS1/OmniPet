package io.github.salyvn.omnipet.core.incubation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class IncubationDurationParserTest {
    @Test
    void parsesCompactCompoundAndIsoDurations() {
        assertEquals(5_400_000, IncubationDurationParser.parseMillis("1h30m"));
        assertEquals(5_400_000, IncubationDurationParser.parseMillis("PT1H30M"));
        assertEquals("90m", IncubationDurationParser.formatMillis(5_400_000));
        assertEquals("1500ms", IncubationDurationParser.formatMillis(1_500));
    }

    @Test
    void rejectsZeroNegativeMalformedAndOverflowDurations() {
        assertThrows(IllegalArgumentException.class, () -> IncubationDurationParser.parseMillis("0s"));
        assertThrows(IllegalArgumentException.class, () -> IncubationDurationParser.parseMillis("-1h"));
        assertThrows(IllegalArgumentException.class, () -> IncubationDurationParser.parseMillis("1 hour"));
        assertThrows(IllegalArgumentException.class, () ->
                IncubationDurationParser.parseMillis("999999999999999999999999d"));
    }
}
