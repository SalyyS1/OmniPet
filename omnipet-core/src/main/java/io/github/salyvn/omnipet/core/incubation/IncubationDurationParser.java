package io.github.salyvn.omnipet.core.incubation;

import java.time.Duration;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.salyvn.omnipet.core.domain.incubation.EggDefinition;

public final class IncubationDurationParser {
    private static final Pattern PART = Pattern.compile("(?i)(\\d+)(ms|s|m|h|d)");

    private IncubationDurationParser() {}

    public static long parseMillis(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("duration is required");
        String text = value.trim();
        long millis = text.regionMatches(true, 0, "P", 0, 1)
                ? parseIso(text)
                : parseCompact(text);
        if (millis < 1 || millis > EggDefinition.MAX_ACTIVE_MILLIS) {
            throw new IllegalArgumentException("duration is outside the supported range");
        }
        return millis;
    }

    public static String formatMillis(long millis) {
        if (millis < 1 || millis > EggDefinition.MAX_ACTIVE_MILLIS) {
            throw new IllegalArgumentException("duration is outside the supported range");
        }
        long day = Duration.ofDays(1).toMillis();
        long hour = Duration.ofHours(1).toMillis();
        long minute = Duration.ofMinutes(1).toMillis();
        long second = Duration.ofSeconds(1).toMillis();
        if (millis % day == 0) return (millis / day) + "d";
        if (millis % hour == 0) return (millis / hour) + "h";
        if (millis % minute == 0) return (millis / minute) + "m";
        if (millis % second == 0) return (millis / second) + "s";
        return millis + "ms";
    }

    private static long parseIso(String value) {
        try { return Duration.parse(value.toUpperCase(Locale.ROOT)).toMillis(); }
        catch (RuntimeException error) { throw new IllegalArgumentException("duration is invalid", error); }
    }

    private static long parseCompact(String value) {
        Matcher matcher = PART.matcher(value);
        int cursor = 0;
        long total = 0;
        while (matcher.find()) {
            if (matcher.start() != cursor) throw new IllegalArgumentException("duration is invalid");
            long amount;
            try { amount = Long.parseLong(matcher.group(1)); }
            catch (NumberFormatException error) { throw new IllegalArgumentException("duration is invalid", error); }
            long multiplier = switch (matcher.group(2).toLowerCase(Locale.ROOT)) {
                case "ms" -> 1L;
                case "s" -> 1_000L;
                case "m" -> 60_000L;
                case "h" -> 3_600_000L;
                case "d" -> 86_400_000L;
                default -> throw new IllegalArgumentException("duration unit is invalid");
            };
            try { total = Math.addExact(total, Math.multiplyExact(amount, multiplier)); }
            catch (ArithmeticException error) { throw new IllegalArgumentException("duration is too large", error); }
            cursor = matcher.end();
        }
        if (cursor != value.length() || cursor == 0) throw new IllegalArgumentException("duration is invalid");
        return total;
    }
}
