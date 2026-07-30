package io.github.salyvn.omnipet.core.studio;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.salyvn.omnipet.core.domain.DisplayDefinition;
import io.github.salyvn.omnipet.core.domain.HeadIcon;
import io.github.salyvn.omnipet.core.domain.RawNodeValues;

final class StudioRawNodes {
    private StudioRawNodes() {}

    static Map<String, Object> put(Map<String, Object> source, Object value, String... path) {
        if (path.length == 0) throw new IllegalArgumentException("raw node path is required");
        Map<String, Object> copy = RawNodeValues.mutableMap(source);
        Map<String, Object> current = copy;
        for (int index = 0; index < path.length - 1; index++) {
            String segment = requireSegment(path[index]);
            Object child = current.get(segment);
            Map<String, Object> nested;
            if (child instanceof Map<?, ?> map) {
                nested = RawNodeValues.mutableMap(stringMap(map));
            } else {
                nested = new LinkedHashMap<>();
            }
            current.put(segment, nested);
            current = nested;
        }
        current.put(requireSegment(path[path.length - 1]), RawNodeValues.mutableCopy(value));
        return RawNodeValues.immutableMap(copy);
    }

    static Map<String, Object> icon(Map<String, Object> raw, HeadIcon icon) {
        Map<String, Object> updated = put(raw, icon.source(), "icon", "head", "source");
        return put(updated, icon.value(), "icon", "head", "value");
    }

    static Map<String, Object> display(Map<String, Object> raw, DisplayDefinition display) {
        Map<String, Object> updated = put(raw, display.provider().name(), "display", "provider");
        return put(updated, display.model(), "display", "model");
    }

    static List<Map<String, Object>> stats(List<StudioStat> stats) {
        List<Map<String, Object>> nodes = new ArrayList<>(stats.size());
        for (StudioStat stat : stats) {
            Map<String, Object> node = RawNodeValues.mutableMap(stat.extensions());
            node.put("id", stat.id());
            node.put("modifierType", stat.modifierType().name());
            node.put("min", stat.range().minimum());
            node.put("max", stat.range().maximum());
            nodes.add(node);
        }
        return List.copyOf(nodes);
    }

    static List<Map<String, Object>> rarityBands(List<RarityBand> bands) {
        List<Map<String, Object>> nodes = new ArrayList<>(bands.size());
        for (RarityBand band : bands) {
            Map<String, Object> node = RawNodeValues.mutableMap(band.extensions());
            node.put("id", band.id());
            node.put("qualityMin", band.qualityMinimum());
            node.put("qualityMax", band.qualityMaximum());
            node.put("weight", band.weight());
            nodes.add(node);
        }
        return List.copyOf(nodes);
    }

    static Map<String, Object> progression(ProgressionFields progression) {
        Map<String, Object> node = RawNodeValues.mutableMap(progression.extensions());
        node.put("maxLevel", progression.maxLevel());
        node.put("experienceFormula", progression.experienceFormula());
        node.put("samples", progression.samples());
        return node;
    }

    static List<Map<String, Object>> skills(List<SkillReference> skills) {
        List<Map<String, Object>> nodes = new ArrayList<>(skills.size());
        for (SkillReference skill : skills) {
            Map<String, Object> node = RawNodeValues.mutableMap(skill.extensions());
            node.put("provider", skill.provider());
            node.put("id", skill.id());
            node.put("trigger", skill.trigger());
            node.put("cooldown", skill.cooldown() == null ? null : skill.cooldown().toString());
            node.put("chance", skill.chance());
            node.put("staminaCost", skill.staminaCost());
            node.put("targetPolicy", skill.targetPolicy());
            nodes.add(node);
        }
        return List.copyOf(nodes);
    }

    private static String requireSegment(String segment) {
        if (segment == null || segment.isBlank()) throw new IllegalArgumentException("raw node path contains a blank segment");
        return segment;
    }

    private static Map<String, Object> stringMap(Map<?, ?> source) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }
}
