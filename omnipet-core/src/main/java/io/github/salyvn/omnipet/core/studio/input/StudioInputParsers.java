package io.github.salyvn.omnipet.core.studio.input;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.salyvn.omnipet.core.domain.StableId;
import io.github.salyvn.omnipet.core.studio.SkillReference;
import io.github.salyvn.omnipet.core.studio.StatRange;

/** Pure, server-independent parsers used by Studio chat fields. */
public final class StudioInputParsers {
    private static final Pattern DURATION = Pattern.compile("^\\s*(?:(\\d+)[Ww])?\\s*(?:(\\d+)[Dd])?\\s*(?:(\\d+)[Hh])?\\s*(?:(\\d+)[Mm])?\\s*(?:(\\d+)[Ss])?\\s*$");

    private StudioInputParsers() {}

    public static String parseStableId(String input) {
        if (input == null || !input.equals(input.trim()) || input.contains(" ")) {
            throw new IllegalArgumentException("stable ID must be one ASCII-safe token");
        }
        return StableId.requireValid(input);
    }

    public static StatRange parseRange(String input) {
        if (input == null) throw new IllegalArgumentException("range is required");
        String trimmed = input.trim();
        if (trimmed.isEmpty()) throw new IllegalArgumentException("range requires exactly two values");
        String[] tokens = trimmed.split("\\s+");
        if (tokens.length != 2) throw new IllegalArgumentException("range requires exactly two values");
        double minimum = finite(tokens[0], "minimum");
        double maximum = finite(tokens[1], "maximum");
        return new StatRange(minimum, maximum);
    }

    public static StatRange parseMinMax(String input) {
        return parseRange(input);
    }

    public static double parseChance(String input) {
        double value = finite(input, "chance");
        if (value < 0 || value > 1) throw new IllegalArgumentException("chance must be in [0,1]");
        return value;
    }

    public static double parseWeight(String input) {
        double value = finite(input, "weight");
        if (value < 0) throw new IllegalArgumentException("weight must be non-negative");
        return value;
    }

    public static Duration parsePositiveDuration(String input) {
        if (input == null) throw new IllegalArgumentException("duration is required");
        Matcher matcher = DURATION.matcher(input);
        if (!matcher.matches()) throw new IllegalArgumentException("invalid duration: " + input);
        try {
            long weeks = unit(matcher, 1);
            long days = unit(matcher, 2);
            long hours = unit(matcher, 3);
            long minutes = unit(matcher, 4);
            long seconds = unit(matcher, 5);
            long total = Math.addExact(Math.multiplyExact(weeks, 7L), days);
            total = Math.addExact(Math.multiplyExact(total, 24L), hours);
            total = Math.addExact(Math.multiplyExact(total, 60L), minutes);
            total = Math.addExact(Math.multiplyExact(total, 60L), seconds);
            if (total <= 0) throw new IllegalArgumentException("duration must be positive");
            return Duration.ofSeconds(total);
        } catch (ArithmeticException | NumberFormatException error) {
            throw new IllegalArgumentException("duration is out of range", error);
        }
    }

    public static List<SkillReference> parseSkillReferences(String input) {
        if (input == null || input.isBlank()) throw new IllegalArgumentException("skill references are required");
        String[] tokens = input.split(",", -1);
        List<SkillReference> result = new ArrayList<>(tokens.length);
        Set<String> seen = new HashSet<>();
        for (String token : tokens) {
            String value = token.trim();
            int separator = value.indexOf(':');
            if (value.isEmpty() || separator <= 0 || separator == value.length() - 1 || value.chars().anyMatch(Character::isWhitespace)) {
                throw new IllegalArgumentException("skill reference must be provider:namespaced-id: " + token);
            }
            String provider = value.substring(0, separator);
            String id = value.substring(separator + 1);
            String folded = value.toLowerCase(Locale.ROOT);
            if (!seen.add(folded)) throw new IllegalArgumentException("duplicate skill reference: " + value);
            result.add(new SkillReference(provider, id));
        }
        return List.copyOf(result);
    }

    private static long unit(Matcher matcher, int group) {
        return matcher.group(group) == null ? 0 : Long.parseLong(matcher.group(group));
    }

    private static double finite(String input, String field) {
        if (input == null || input.isBlank()) throw new IllegalArgumentException(field + " is required");
        try {
            double value = Double.parseDouble(input.trim());
            if (!Double.isFinite(value)) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException(field + " must be finite", error);
        }
    }
}
