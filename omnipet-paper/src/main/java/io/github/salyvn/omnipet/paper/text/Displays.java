package io.github.salyvn.omnipet.paper.text;

import java.util.Locale;
import java.util.Objects;

/**
 * Turns enum constants into human-readable text in exactly one place.
 *
 * <p>Before this, {@code name().toLowerCase().replace('_', ' ')} was repeated across controllers and
 * renderers, so {@code ENTITLEMENT_SYNC_PENDING} reached players as {@code entitlement sync pending}
 * with inconsistent capitalisation depending on the call site.
 */
public final class Displays {
    private Displays() {}

    /**
     * Sentence-case text for any enum constant: {@code ENTITLEMENT_SYNC_PENDING} becomes
     * {@code Entitlement sync pending}.
     */
    public static String of(Enum<?> value) {
        Objects.requireNonNull(value, "enum value");
        return sentence(value.name());
    }

    /** Lower-case words, for embedding mid-sentence: {@code entitlement sync pending}. */
    public static String words(Enum<?> value) {
        Objects.requireNonNull(value, "enum value");
        return value.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    /** Sentence-case text for a raw identifier such as a rarity or release-mode ID. */
    public static String identifier(String value) {
        if (value == null || value.isBlank()) return "";
        return sentence(value.trim());
    }

    /**
     * A short wait a player can read at a glance: {@code 0.8s}, {@code 7s}, {@code 1m 20s}.
     *
     * <p>Rounds a sub-second remainder up rather than down, because a cooldown displayed as {@code 0s} that
     * still refuses the cast is worse than one tenth of a second of overstatement.
     */
    public static String remaining(long millis) {
        if (millis <= 0) return "0s";
        if (millis < 1000) return "0." + Math.max(1, millis / 100) + "s";
        long seconds = (millis + 999) / 1000;
        if (seconds < 60) return seconds + "s";
        long minutes = seconds / 60;
        long rest = seconds % 60;
        if (minutes < 60) return rest == 0 ? minutes + "m" : minutes + "m " + rest + "s";
        long hours = minutes / 60;
        long restMinutes = minutes % 60;
        return restMinutes == 0 ? hours + "h" : hours + "h " + restMinutes + "m";
    }

    private static String sentence(String raw) {
        String words = raw.toLowerCase(Locale.ROOT).replace('_', ' ');
        if (words.isEmpty()) return words;
        return Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }
}
