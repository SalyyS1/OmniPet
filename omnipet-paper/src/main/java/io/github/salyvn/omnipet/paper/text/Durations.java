package io.github.salyvn.omnipet.paper.text;

import java.math.BigDecimal;
import java.time.Duration;

/**
 * The one countdown and decimal formatter, beside {@link Displays}.
 *
 * <p>Both existed twice. The countdown was copied from the hatch menu into the hub tile, and the
 * decimal formatter from the management screen into the Studio's stat screens. Two copies of a format
 * string drift, and the drift shows up as one screen counting down differently from another.
 *
 * <p>Output is unchanged from the copies this replaces, including the clamp on negative input: a
 * finish time that has already passed reads as zero rather than as a negative countdown.
 */
public final class Durations {
    private static final long SECONDS_PER_DAY = 86_400L;
    private static final long SECONDS_PER_HOUR = 3_600L;

    private Durations() {}

    /**
     * A countdown as {@code 2d 03h 04m 05s}, dropping the day component when there is none.
     *
     * <p>Negative input clamps to zero, matching the previous behavior.
     */
    public static String countdown(long millis) {
        long seconds = Math.max(0, Duration.ofMillis(millis).toSeconds());
        long days = seconds / SECONDS_PER_DAY;
        seconds %= SECONDS_PER_DAY;
        long hours = seconds / SECONDS_PER_HOUR;
        seconds %= SECONDS_PER_HOUR;
        long minutes = seconds / 60;
        seconds %= 60;
        return days > 0
                ? "%dd %02dh %02dm %02ds".formatted(days, hours, minutes, seconds)
                : "%02dh %02dm %02ds".formatted(hours, minutes, seconds);
    }

    /** A stat value without trailing zeros or exponent notation: {@code 2.5}, {@code 7}, {@code 0}. */
    public static String decimal(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }
}
