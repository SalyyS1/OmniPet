package io.github.salyvn.omnipet.core.studio;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

import io.github.salyvn.omnipet.core.domain.RawNodeValues;

final class StudioFieldExtensions {
    private StudioFieldExtensions() {
    }

    static List<StudioStat> stats(List<StudioStat> current, List<StudioStat> replacements) {
        Map<String, StudioStat> existing = index(current, stat -> key(stat.id()));
        List<StudioStat> result = new ArrayList<>(replacements.size());
        for (StudioStat replacement : replacements) {
            StudioStat previous = existing.get(key(replacement.id()));
            result.add(new StudioStat(replacement.id(), replacement.modifierType(), replacement.range(),
                    merge(previous == null ? Map.of() : previous.extensions(), replacement.extensions())));
        }
        return List.copyOf(result);
    }

    static List<RarityBand> rarity(List<RarityBand> current, List<RarityBand> replacements) {
        Map<String, RarityBand> existing = index(current, band -> key(band.id()));
        List<RarityBand> result = new ArrayList<>(replacements.size());
        for (RarityBand replacement : replacements) {
            RarityBand previous = existing.get(key(replacement.id()));
            result.add(new RarityBand(replacement.id(), replacement.qualityMinimum(), replacement.qualityMaximum(),
                    replacement.weight(), merge(previous == null ? Map.of() : previous.extensions(),
                            replacement.extensions())));
        }
        return List.copyOf(result);
    }

    static ProgressionFields progression(ProgressionFields current, ProgressionFields replacement) {
        if (replacement == null || current == null) return replacement;
        return new ProgressionFields(replacement.maxLevel(), replacement.experienceFormula(), replacement.samples(),
                merge(current.extensions(), replacement.extensions()));
    }

    static List<SkillReference> skills(List<SkillReference> current, List<SkillReference> replacements) {
        Map<String, SkillReference> existing = index(current,
                skill -> key(skill.provider()) + "\u0000" + key(skill.id()));
        List<SkillReference> result = new ArrayList<>(replacements.size());
        for (SkillReference replacement : replacements) {
            SkillReference previous = existing.get(key(replacement.provider()) + "\u0000" + key(replacement.id()));
            result.add(new SkillReference(replacement.provider(), replacement.id(), replacement.trigger(),
                    replacement.cooldown(), replacement.chance(), replacement.staminaCost(), replacement.targetPolicy(),
                    merge(previous == null ? Map.of() : previous.extensions(), replacement.extensions())));
        }
        return List.copyOf(result);
    }

    static ReleasePolicy release(ReleasePolicy current, ReleasePolicy replacement) {
        if (replacement == null || current == null) return replacement;
        return new ReleasePolicy(replacement.mode(), merge(current.extensions(), replacement.extensions()));
    }

    private static <T> Map<String, T> index(List<T> values, Function<T, String> key) {
        Map<String, T> result = new LinkedHashMap<>();
        for (T value : values) result.putIfAbsent(key.apply(value), value);
        return result;
    }

    private static Map<String, Object> merge(Map<String, Object> preserved, Map<String, Object> replacement) {
        Map<String, Object> result = new LinkedHashMap<>(preserved);
        result.putAll(replacement);
        return RawNodeValues.immutableMap(result);
    }

    private static String key(String value) {
        return value.toLowerCase(Locale.ROOT);
    }
}
