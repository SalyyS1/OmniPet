package io.github.salyvn.omnipet.core.domain;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class StableId {
    private static final Pattern GRAMMAR = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_-]{0,63}");
    private static final Set<String> WINDOWS_RESERVED = Set.of(
            "CON", "PRN", "AUX", "NUL",
            "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
            "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9");

    private StableId() {}

    public static String requireValid(String id) {
        if (id == null || !GRAMMAR.matcher(id).matches()) {
            throw new IllegalArgumentException("Invalid stable ID: " + id);
        }
        String folded = id.toUpperCase(Locale.ROOT);
        if (WINDOWS_RESERVED.contains(folded)) {
            throw new IllegalArgumentException("Windows-reserved stable ID: " + id);
        }
        return id;
    }

    public static String folded(String id) {
        return requireValid(id).toLowerCase(Locale.ROOT);
    }
}
