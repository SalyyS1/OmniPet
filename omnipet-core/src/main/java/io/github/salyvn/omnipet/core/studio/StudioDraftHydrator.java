package io.github.salyvn.omnipet.core.studio;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.salyvn.omnipet.core.domain.RawNodeValues;

final class StudioDraftHydrator {
    private StudioDraftHydrator() {}

    static Fields read(Map<String, Object> raw) {
        return new Fields(
                stats(raw.get("stats")),
                rarity(nested(raw, "rarity", "bands")),
                progression(raw.get("progression")),
                skills(raw.get("skills")),
                objectMap(raw.get("behavior"), "behavior"),
                release(raw.get("release")));
    }

    private static List<StudioStat> stats(Object value) {
        if (value == null) return List.of();
        List<Map<String, Object>> nodes = listOfMaps(value, "stats");
        List<StudioStat> result = new ArrayList<>(nodes.size());
        for (int index = 0; index < nodes.size(); index++) {
            Map<String, Object> node = nodes.get(index);
            String path = "stats[" + index + "]";
            Object modifier = node.containsKey("modifierType") ? node.get("modifierType") : node.get("type");
            result.add(new StudioStat(
                    string(node.get("id"), path + ".id"),
                    enumValue(modifier, StatModifierType.class, path + ".modifierType"),
                    new StatRange(number(node.get("min"), path + ".min"), number(node.get("max"), path + ".max")),
                    extensions(node, "id", "modifierType", "type", "min", "max")));
        }
        return List.copyOf(result);
    }

    private static List<RarityBand> rarity(Object value) {
        if (value == null) return List.of();
        List<Map<String, Object>> nodes = listOfMaps(value, "rarity.bands");
        List<RarityBand> result = new ArrayList<>(nodes.size());
        for (int index = 0; index < nodes.size(); index++) {
            Map<String, Object> node = nodes.get(index);
            String path = "rarity.bands[" + index + "]";
            result.add(new RarityBand(
                    string(node.get("id"), path + ".id"),
                    number(node.get("qualityMin"), path + ".qualityMin"),
                    number(node.get("qualityMax"), path + ".qualityMax"),
                    number(node.get("weight"), path + ".weight"),
                    extensions(node, "id", "qualityMin", "qualityMax", "weight")));
        }
        return List.copyOf(result);
    }

    private static ProgressionFields progression(Object value) {
        if (value == null) return null;
        Map<String, Object> node = objectMap(value, "progression");
        int maxLevel = integer(node.get("maxLevel"), "progression.maxLevel");
        String formula = node.get("experienceFormula") == null ? null
                : string(node.get("experienceFormula"), "progression.experienceFormula");
        Map<String, Double> samples = new LinkedHashMap<>();
        if (node.get("samples") != null) {
            objectMap(node.get("samples"), "progression.samples")
                    .forEach((key, sample) -> samples.put(key, number(sample, "progression.samples." + key)));
        }
        return new ProgressionFields(maxLevel, formula, samples,
                extensions(node, "maxLevel", "experienceFormula", "samples"));
    }

    private static List<SkillReference> skills(Object value) {
        if (value == null) return List.of();
        List<Map<String, Object>> nodes = listOfMaps(value, "skills");
        List<SkillReference> result = new ArrayList<>(nodes.size());
        for (int index = 0; index < nodes.size(); index++) {
            Map<String, Object> node = nodes.get(index);
            String path = "skills[" + index + "]";
            Object cooldown = node.get("cooldown");
            result.add(new SkillReference(
                    string(node.get("provider"), path + ".provider"),
                    string(node.get("id"), path + ".id"),
                    nullableString(node.get("trigger"), path + ".trigger"),
                    cooldown == null ? null : duration(cooldown, path + ".cooldown"),
                    node.get("chance") == null ? 1.0 : number(node.get("chance"), path + ".chance"),
                    node.get("staminaCost") == null ? 0.0 : number(node.get("staminaCost"), path + ".staminaCost"),
                    nullableString(node.get("targetPolicy"), path + ".targetPolicy"),
                    extensions(node, "provider", "id", "trigger", "cooldown", "chance", "staminaCost", "targetPolicy")));
        }
        return List.copyOf(result);
    }

    private static ReleasePolicy release(Object value) {
        if (value == null) return null;
        Map<String, Object> node = objectMap(value, "release");
        return new ReleasePolicy(string(node.get("mode"), "release.mode"), extensions(node, "mode"));
    }

    private static Object nested(Map<String, Object> source, String parent, String child) {
        Object value = source.get(parent);
        if (value == null) return null;
        return objectMap(value, parent).get(child);
    }

    private static List<Map<String, Object>> listOfMaps(Object value, String path) {
        if (!(value instanceof List<?> list)) throw new IllegalArgumentException(path + " must be a list");
        List<Map<String, Object>> result = new ArrayList<>(list.size());
        for (int index = 0; index < list.size(); index++) result.add(objectMap(list.get(index), path + "[" + index + "]"));
        return result;
    }

    private static Map<String, Object> objectMap(Object value, String path) {
        if (value == null) return Map.of();
        if (!(value instanceof Map<?, ?> map)) throw new IllegalArgumentException(path + " must be a map");
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, nested) -> result.put(String.valueOf(key), RawNodeValues.mutableCopy(nested)));
        return result;
    }

    private static Map<String, Object> extensions(Map<String, Object> node, String... known) {
        Map<String, Object> result = RawNodeValues.mutableMap(node);
        for (String key : known) result.remove(key);
        return RawNodeValues.immutableMap(result);
    }

    private static String string(Object value, String path) {
        if (!(value instanceof String text) || text.isBlank()) throw new IllegalArgumentException(path + " must be text");
        return text;
    }

    private static String nullableString(Object value, String path) { return value == null ? null : string(value, path); }

    private static double number(Object value, String path) {
        if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())) {
            throw new IllegalArgumentException(path + " must be finite");
        }
        return number.doubleValue();
    }

    private static int integer(Object value, String path) {
        double number = number(value, path);
        if (number != (int) number) throw new IllegalArgumentException(path + " must be an integer");
        return (int) number;
    }

    private static Duration duration(Object value, String path) {
        try { return Duration.parse(string(value, path)); }
        catch (RuntimeException error) { throw new IllegalArgumentException(path + " is invalid", error); }
    }

    private static <T extends Enum<T>> T enumValue(Object value, Class<T> type, String path) {
        try { return Enum.valueOf(type, string(value, path).toUpperCase(java.util.Locale.ROOT)); }
        catch (RuntimeException error) { throw new IllegalArgumentException(path + " is invalid", error); }
    }

    record Fields(List<StudioStat> stats, List<RarityBand> rarity, ProgressionFields progression,
                  List<SkillReference> skills, Map<String, Object> behavior, ReleasePolicy release) {}
}
