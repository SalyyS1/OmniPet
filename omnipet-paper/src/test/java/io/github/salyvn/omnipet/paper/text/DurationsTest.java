package io.github.salyvn.omnipet.paper.text;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;

import org.junit.jupiter.api.Test;

/**
 * Pins the exact output of the formatters this class replaced.
 *
 * <p>The point of a deduplication is that nothing visible changes. These strings were copied from the
 * two private implementations before they were deleted, so a regression shows up here rather than as a
 * player noticing the hub counting down differently from the hatch menu.
 */
class DurationsTest {
    @Test
    void subDayCountdownsOmitTheDayComponent() {
        assertEquals("00h 00m 00s", Durations.countdown(0));
        assertEquals("00h 00m 05s", Durations.countdown(5_000));
        assertEquals("00h 01m 00s", Durations.countdown(Duration.ofMinutes(1).toMillis()));
        assertEquals("00h 59m 59s", Durations.countdown(Duration.ofSeconds(3_599).toMillis()));
        assertEquals("01h 00m 00s", Durations.countdown(Duration.ofHours(1).toMillis()));
        assertEquals("23h 59m 59s", Durations.countdown(Duration.ofSeconds(86_399).toMillis()));
    }

    @Test
    void multiDayCountdownsIncludeTheDayComponent() {
        assertEquals("1d 00h 00m 00s", Durations.countdown(Duration.ofDays(1).toMillis()));
        assertEquals("2d 03h 04m 05s", Durations.countdown(
                Duration.ofDays(2).plusHours(3).plusMinutes(4).plusSeconds(5).toMillis()));
        assertEquals("400d 00h 00m 00s", Durations.countdown(Duration.ofDays(400).toMillis()));
    }

    @Test
    void subSecondRemaindersTruncateRatherThanRound() {
        // toSeconds() truncates, so 1900ms is one second remaining, not two. Preserved deliberately:
        // rounding up would show "01s" on a countdown that is about to hit zero.
        assertEquals("00h 00m 01s", Durations.countdown(1_900));
        assertEquals("00h 00m 00s", Durations.countdown(999));
    }

    @Test
    void negativeInputClampsToZeroInsteadOfCountingBackwards() {
        // A finish time already in the past must read as done, not as a negative countdown.
        assertEquals("00h 00m 00s", Durations.countdown(-1));
        assertEquals("00h 00m 00s", Durations.countdown(Duration.ofDays(-3).toMillis()));
    }

    @Test
    void decimalsDropTrailingZerosAndNeverUseExponentNotation() {
        assertEquals("7", Durations.decimal(7.0));
        assertEquals("2.5", Durations.decimal(2.5));
        assertEquals("0", Durations.decimal(0.0));
        assertEquals("-1.25", Durations.decimal(-1.25));
        assertEquals("0.001", Durations.decimal(0.001));
        // A large value must stay readable rather than becoming 1E+9 in a lore line.
        assertEquals("1000000000", Durations.decimal(1_000_000_000.0));
    }
}
