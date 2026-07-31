package io.github.salyvn.omnipet.core.persistence;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.github.salyvn.omnipet.core.domain.HeadIcon;
import io.github.salyvn.omnipet.core.domain.PetTier;
import io.github.salyvn.omnipet.core.domain.RawNodeValues;
import io.github.salyvn.omnipet.core.domain.incubation.IncubationOutcome;
import io.github.salyvn.omnipet.core.domain.incubation.IncubationState;
import io.github.salyvn.omnipet.core.domain.incubation.IncubationStatus;
import io.github.salyvn.omnipet.core.domain.incubation.RealizedStat;
import io.github.salyvn.omnipet.core.studio.StatModifierType;

final class IncubationYamlCodec {
    private static final String[] STATE_KEYS = {
        "id", "eggId", "outcome", "remainingActiveMillis", "status", "appliedActionTokens"
    };
    private static final String[] OUTCOME_KEYS = {
        "petInstanceId", "definitionId", "definitionRevision", "tier", "icon", "rarityId", "qualityScore",
        "seed", "algorithmId", "realizedStats", "totalActiveMillis"
    };
    private static final String[] STAT_KEYS = { "id", "modifierType", "value" };

    IncubationState decode(Object value) {
        if (value == null) return null;
        Map<String, Object> node = IncubationYamlValues.map(value, "incubation");
        Map<String, Object> outcomeNode = IncubationYamlValues.map(node.get("outcome"), "incubation.outcome");
        Map<String, Object> iconNode = IncubationYamlValues.map(outcomeNode.get("icon"), "incubation.outcome.icon");
        List<RealizedStat> stats = decodeStats(outcomeNode.getOrDefault("realizedStats", List.of()));
        IncubationOutcome outcome = new IncubationOutcome(
                IncubationYamlValues.uuid(outcomeNode.get("petInstanceId"), "incubation.outcome.petInstanceId"),
                IncubationYamlValues.text(outcomeNode.get("definitionId"), "incubation.outcome.definitionId"),
                IncubationYamlValues.integer(outcomeNode.get("definitionRevision"), "incubation.outcome.definitionRevision"),
                IncubationYamlValues.enumValue(outcomeNode.get("tier"), PetTier.class, "incubation.outcome.tier"),
                new HeadIcon(
                        IncubationYamlValues.text(iconNode.get("source"), "incubation.outcome.icon.source"),
                        IncubationYamlValues.text(iconNode.get("value"), "incubation.outcome.icon.value")),
                IncubationYamlValues.without(iconNode, "source", "value"),
                IncubationYamlValues.text(outcomeNode.get("rarityId"), "incubation.outcome.rarityId"),
                IncubationYamlValues.number(outcomeNode.get("qualityScore"), "incubation.outcome.qualityScore"),
                IncubationYamlValues.integer(outcomeNode.get("seed"), "incubation.outcome.seed"),
                IncubationYamlValues.text(outcomeNode.get("algorithmId"), "incubation.outcome.algorithmId"),
                stats,
                IncubationYamlValues.integer(outcomeNode.get("totalActiveMillis"), "incubation.outcome.totalActiveMillis"),
                IncubationYamlValues.without(outcomeNode, OUTCOME_KEYS));
        List<UUID> tokens = IncubationYamlValues.uuidList(
                node.getOrDefault("appliedActionTokens", List.of()), "incubation.appliedActionTokens");
        return new IncubationState(
                IncubationYamlValues.uuid(node.get("id"), "incubation.id"),
                IncubationYamlValues.text(node.get("eggId"), "incubation.eggId"),
                outcome,
                IncubationYamlValues.integer(node.get("remainingActiveMillis"), "incubation.remainingActiveMillis"),
                IncubationYamlValues.enumValue(node.get("status"), IncubationStatus.class, "incubation.status"),
                tokens,
                IncubationYamlValues.without(node, STATE_KEYS));
    }

    Map<String, Object> encode(IncubationState state) {
        if (state == null) return Map.of();
        LinkedHashMap<String, Object> node = new LinkedHashMap<>(RawNodeValues.mutableMap(state.extensions()));
        node.put("id", state.id().toString());
        node.put("eggId", state.eggId());
        node.put("outcome", encodeOutcome(state.outcome()));
        node.put("remainingActiveMillis", state.remainingActiveMillis());
        node.put("status", state.status().name());
        node.put("appliedActionTokens", state.appliedActionTokens().stream().map(UUID::toString).toList());
        return node;
    }

    private static List<RealizedStat> decodeStats(Object value) {
        List<Map<String, Object>> nodes = IncubationYamlValues.mapList(value, "incubation.outcome.realizedStats");
        List<RealizedStat> result = new ArrayList<>(nodes.size());
        for (int index = 0; index < nodes.size(); index++) {
            Map<String, Object> node = nodes.get(index);
            String path = "incubation.outcome.realizedStats[" + index + "]";
            result.add(new RealizedStat(
                    IncubationYamlValues.text(node.get("id"), path + ".id"),
                    IncubationYamlValues.enumValue(node.get("modifierType"), StatModifierType.class,
                            path + ".modifierType"),
                    IncubationYamlValues.number(node.get("value"), path + ".value"),
                    IncubationYamlValues.without(node, STAT_KEYS)));
        }
        return List.copyOf(result);
    }

    private static Map<String, Object> encodeOutcome(IncubationOutcome outcome) {
        LinkedHashMap<String, Object> node = new LinkedHashMap<>(RawNodeValues.mutableMap(outcome.extensions()));
        node.put("petInstanceId", outcome.petInstanceId().toString());
        node.put("definitionId", outcome.definitionId());
        node.put("definitionRevision", outcome.definitionRevision());
        node.put("tier", outcome.tier().name());
        LinkedHashMap<String, Object> icon = new LinkedHashMap<>(RawNodeValues.mutableMap(outcome.iconExtensions()));
        icon.put("source", outcome.icon().source());
        icon.put("value", outcome.icon().value());
        node.put("icon", icon);
        node.put("rarityId", outcome.rarityId());
        node.put("qualityScore", outcome.qualityScore());
        node.put("seed", outcome.seed());
        node.put("algorithmId", outcome.algorithmId());
        node.put("realizedStats", encodeStats(outcome.realizedStats()));
        node.put("totalActiveMillis", outcome.totalActiveMillis());
        return node;
    }

    private static List<Map<String, Object>> encodeStats(List<RealizedStat> stats) {
        List<Map<String, Object>> result = new ArrayList<>(stats.size());
        for (RealizedStat stat : stats) {
            LinkedHashMap<String, Object> node = new LinkedHashMap<>(RawNodeValues.mutableMap(stat.extensions()));
            node.put("id", stat.id());
            node.put("modifierType", stat.modifierType().name());
            node.put("value", stat.value());
            result.add(node);
        }
        return List.copyOf(result);
    }
}
