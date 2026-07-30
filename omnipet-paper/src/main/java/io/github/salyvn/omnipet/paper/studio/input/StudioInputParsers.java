package io.github.salyvn.omnipet.paper.studio.input;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import io.github.salyvn.omnipet.core.domain.StableId;

public final class StudioInputParsers {
    private static final Pattern SKILL_ID = Pattern.compile("[A-Za-z0-9_-]+:[A-Za-z0-9._/-]+");

    private StudioInputParsers() {}

    public static String stableId(String input) {
        String value = requireTrimmed(input);
        if (!value.equals(input)) throw new IllegalArgumentException("stable ID must not contain surrounding whitespace");
        return StableId.requireValid(value);
    }

    public static NumericRange numericRange(String input) {
        String[] values = requireTrimmed(input).split("\\s+");
        if (values.length != 2) throw new IllegalArgumentException("expected: <min> <max>");
        double min = finite(values[0]);
        double max = finite(values[1]);
        if (min > max) throw new IllegalArgumentException("minimum cannot exceed maximum");
        return new NumericRange(min, max);
    }

    public static double chance(String input) {
        double chance = finite(requireTrimmed(input));
        if (chance < 0 || chance > 1) throw new IllegalArgumentException("chance must be between 0 and 1");
        return chance;
    }

    public static List<String> skillIds(String input) {
        String[] values = requireTrimmed(input).split(",", -1);
        List<String> result = new ArrayList<>(values.length);
        Set<String> unique = new HashSet<>();
        for (String raw : values) {
            String value = raw.trim();
            if (!SKILL_ID.matcher(value).matches()) throw new IllegalArgumentException("invalid namespaced skill ID: " + value);
            if (!unique.add(value)) throw new IllegalArgumentException("duplicate skill ID: " + value);
            result.add(value);
        }
        return List.copyOf(result);
    }

    private static String requireTrimmed(String input) {
        if (input == null) throw new IllegalArgumentException("input is required");
        String value = input.trim();
        if (value.isEmpty()) throw new IllegalArgumentException("input is required");
        return value;
    }

    private static double finite(String input) {
        double value;
        try {
            value = Double.parseDouble(input);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("expected a number: " + input, exception);
        }
        if (!Double.isFinite(value)) throw new IllegalArgumentException("number must be finite");
        return value;
    }

    public record NumericRange(double min, double max) {}
}
