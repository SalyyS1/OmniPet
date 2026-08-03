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

    private static String sentence(String raw) {
        String words = raw.toLowerCase(Locale.ROOT).replace('_', ' ');
        if (words.isEmpty()) return words;
        return Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }
}
