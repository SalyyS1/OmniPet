package io.github.salyvn.omnipet.paper.studio.bukkit;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.github.salyvn.omnipet.core.catalog.StatCatalogEntry;
import io.github.salyvn.omnipet.core.domain.DisplayDefinition;
import io.github.salyvn.omnipet.core.domain.HeadIcon;
import io.github.salyvn.omnipet.core.studio.HeadIconSources;
import io.github.salyvn.omnipet.core.studio.ProgressionFields;
import io.github.salyvn.omnipet.core.studio.RarityBand;
import io.github.salyvn.omnipet.core.studio.ReleasePolicy;
import io.github.salyvn.omnipet.core.studio.SkillReference;
import io.github.salyvn.omnipet.core.studio.StatLogicalIdentity;
import io.github.salyvn.omnipet.core.studio.StatModifierType;
import io.github.salyvn.omnipet.core.studio.StatRange;
import io.github.salyvn.omnipet.core.studio.StudioStat;
import io.github.salyvn.omnipet.core.studio.input.StudioFormulaValidator;
import io.github.salyvn.omnipet.core.studio.input.StudioInputParsers;

final class StudioDraftInputParsers {
    /**
     * How much nameplate text an operator may type.
     *
     * <p>Shorter than the renderer's own ceiling, which has to hold the level suffix the runtime appends as
     * well. Refusing here means an operator learns the limit while typing rather than discovering their name
     * silently truncated above a pet.
     */
    private static final int MAX_DISPLAY_NAME_INPUT = 48;

    private StudioDraftInputParsers() {}

    /**
     * Accepts a pasted base64 payload, a texture URL, a bare 64-hex texture hash, or the legacy
     * {@code <SOURCE> <value>} form. Detection and the authoritative rules both live in core, so a
     * value accepted here cannot be rejected at Save.
     */
    static HeadIcon icon(String input) {
        HeadIconSources.Detected detected = HeadIconSources.detect(input);
        return new HeadIcon(detected.source(), detected.value());
    }

    /**
     * The nameplate text for a pet, or null to clear it.
     *
     * <p>Kept as raw MiniMessage rather than parsed here, matching every other operator-authored string:
     * the renderer parses it at display time, and a broken tag shows literally rather than blocking a save.
     * Length is bounded to what a nameplate can carry, and the level suffix the runtime appends is left
     * room for.
     */
    static String displayName(String input) {
        if (isNone(input)) return null;
        String value = required(input);
        if (value.length() > MAX_DISPLAY_NAME_INPUT) {
            throw new IllegalArgumentException(
                    "nameplate text cannot exceed " + MAX_DISPLAY_NAME_INPUT + " characters");
        }
        return value;
    }

    static DisplayDefinition display(String input) {        String[] parts = required(input).split("\\s+", 2);
        DisplayDefinition.Provider provider;
        try {
            provider = DisplayDefinition.Provider.valueOf(parts[0].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("display provider must be HEAD or MODELENGINE", error);
        }
        return new DisplayDefinition(provider, parts.length == 2 ? parts[1] : null);
    }

    static List<StudioStat> stats(String input) {
        if (isNone(input)) return List.of();
        List<StudioStat> result = new ArrayList<>();
        for (String entry : required(input).split(";", -1)) {
            String[] parts = entry.trim().split("\\s+");
            if (parts.length != 4) throw new IllegalArgumentException("stat format: <id> <modifier> <min> <max>");
            StatModifierType modifier;
            try {
                modifier = StatModifierType.valueOf(parts[1].toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException error) {
                throw new IllegalArgumentException("unknown stat modifier: " + parts[1], error);
            }
            result.add(new StudioStat(parts[0], modifier,
                    StudioInputParsers.parseRange(parts[2] + " " + parts[3]), Map.of()));
        }
        return List.copyOf(result);
    }

    /** The legacy three-token form, kept so existing callers and tests are unaffected. */
    static StudioStat catalogStat(String input, StatCatalogEntry entry) {
        return catalogStat(input, entry, null);
    }

    /**
     * Parses a catalog stat range, optionally with the modifier already chosen by click.
     *
     * <p>Two tokens use {@code preselected}; three tokens read the modifier from the input and
     * ignore it. The full form stays valid so an operator who knows the syntax can keep typing it.
     */
    static StudioStat catalogStat(String input, StatCatalogEntry entry, StatModifierType preselected) {
        String[] parts = required(input).split("\\s+");
        StatModifierType modifier;
        String minimum;
        String maximum;
        if (parts.length == 2) {
            if (preselected == null) throw new IllegalArgumentException("expected: <modifier> <min> <max>");
            modifier = preselected;
            minimum = parts[0];
            maximum = parts[1];
        } else if (parts.length == 3) {
            try {
                modifier = StatModifierType.valueOf(parts[0].toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException error) {
                throw new IllegalArgumentException("unknown modifier: " + parts[0], error);
            }
            minimum = parts[1];
            maximum = parts[2];
        } else {
            throw new IllegalArgumentException(preselected == null
                    ? "expected: <modifier> <min> <max>"
                    : "expected: <min> <max>");
        }
        if (!entry.supportedModifierTypes().contains(modifier)) {
            throw new IllegalArgumentException("modifier is not supported by " + entry.displayName());
        }
        return new StudioStat(entry.id(), modifier,
                StudioInputParsers.parseRange(minimum + " " + maximum), entry.extensions());
    }

    static List<StudioStat> upsertStat(List<StudioStat> current, StudioStat replacement) {
        List<StudioStat> result = new ArrayList<>(current);
        String replacementKey = StatLogicalIdentity.key(replacement);
        result.removeIf(stat -> StatLogicalIdentity.key(stat).equals(replacementKey));
        result.add(replacement);
        return List.copyOf(result);
    }

    static List<RarityBand> rarity(String input) {
        if (isNone(input)) return List.of();
        List<RarityBand> result = new ArrayList<>();
        for (String entry : required(input).split(";", -1)) {
            String[] parts = entry.trim().split("\\s+");
            if (parts.length != 5) throw new IllegalArgumentException("rarity format: <id> <qualityMin> <qualityMax> <weight> <hatchMultiplier>");
            StatRange range = StudioInputParsers.parseRange(parts[1] + " " + parts[2]);
            double weight = StudioInputParsers.parseWeight(parts[3]);
            double hatchMultiplier = positive(parts[4], "hatch multiplier");
            result.add(new RarityBand(parts[0], range.minimum(), range.maximum(), weight,
                    Map.of("hatchMultiplier", hatchMultiplier)));
        }
        return List.copyOf(result);
    }

    static ProgressionFields progression(String input) {
        if (isNone(input)) return null;
        String[] parts = required(input).split(";", 2);
        if (parts.length != 2) throw new IllegalArgumentException("progression format: <maxLevel>;<formula>");
        int maxLevel;
        try {
            maxLevel = Integer.parseInt(parts[0].trim());
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("maxLevel must be an integer", error);
        }
        if (maxLevel <= 0 || maxLevel > 10_000) throw new IllegalArgumentException("maxLevel must be between 1 and 10000");
        String formula = required(parts[1]);
        for (int level = 1; level <= maxLevel; level++) {
            StudioFormulaValidator.validate(formula, Map.of("level", (double) level), 0, Double.MAX_VALUE);
        }
        return new ProgressionFields(maxLevel, formula, Map.of("level", 1.0), Map.of());
    }

    static List<SkillReference> skills(String input) {
        if (isNone(input)) return List.of();
        List<SkillReference> result = new ArrayList<>();
        for (String entry : required(input).split(";", -1)) {
            String[] fields = entry.trim().split("\\|", -1);
            if (fields.length != 6) throw new IllegalArgumentException("skill format: <provider:id>|<trigger>|<cooldown>|<chance>|<stamina>|<target>");
            int separator = fields[0].indexOf(':');
            if (separator <= 0 || separator == fields[0].length() - 1) throw new IllegalArgumentException("skill requires provider:id");
            Duration cooldown = fields[2].equalsIgnoreCase("none") ? null
                    : StudioInputParsers.parsePositiveDuration(fields[2]);
            result.add(new SkillReference(
                    fields[0].substring(0, separator),
                    fields[0].substring(separator + 1),
                    nullable(fields[1]),
                    cooldown,
                    StudioInputParsers.parseChance(fields[3]),
                    nonNegative(fields[4], "stamina"),
                    nullable(fields[5]),
                    Map.of()));
        }
        return List.copyOf(result);
    }

    static Map<String, Object> behavior(String input) {
        if (isNone(input)) return Map.of();
        Map<String, Object> result = new LinkedHashMap<>();
        for (String entry : required(input).split(";", -1)) {
            String[] parts = entry.trim().split("=", 2);
            if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
                throw new IllegalArgumentException("behavior format: key=value;key=value");
            }
            if (result.putIfAbsent(parts[0].trim(), parts[1].trim()) != null) {
                throw new IllegalArgumentException("duplicate behavior key: " + parts[0].trim());
            }
        }
        return Map.copyOf(result);
    }

    static ReleasePolicy release(String input) {
        if (isNone(input)) return null;
        String value = required(input);
        if (value.chars().anyMatch(Character::isWhitespace)) throw new IllegalArgumentException("release mode must be one token");
        return new ReleasePolicy(value.toUpperCase(Locale.ROOT), Map.of());
    }

    static String exactDefinitionId(String input, String expected) {
        if (input == null || !input.equals(expected)) {
            throw new IllegalArgumentException("type the exact definition ID: " + expected);
        }
        return input;
    }

    private static String required(String input) {
        if (input == null || input.trim().isEmpty()) throw new IllegalArgumentException("input is required");
        return input.trim();
    }

    private static boolean isNone(String input) { return input != null && input.trim().equalsIgnoreCase("none"); }

    private static String nullable(String input) { return input.isBlank() || input.equalsIgnoreCase("none") ? null : input.trim(); }

    private static double positive(String input, String field) {
        double value = nonNegative(input, field);
        if (value == 0) throw new IllegalArgumentException(field + " must be positive");
        return value;
    }

    private static double nonNegative(String input, String field) {
        double value;
        try { value = Double.parseDouble(input.trim()); }
        catch (NumberFormatException error) { throw new IllegalArgumentException(field + " must be a number", error); }
        if (!Double.isFinite(value) || value < 0) throw new IllegalArgumentException(field + " must be finite and non-negative");
        return value;
    }
}
