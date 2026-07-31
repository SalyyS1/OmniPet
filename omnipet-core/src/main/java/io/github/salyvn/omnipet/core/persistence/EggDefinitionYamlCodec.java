package io.github.salyvn.omnipet.core.persistence;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.salyvn.omnipet.core.domain.PetTier;
import io.github.salyvn.omnipet.core.domain.RawNodeValues;
import io.github.salyvn.omnipet.core.domain.StableId;
import io.github.salyvn.omnipet.core.domain.incubation.EggDefinition;
import io.github.salyvn.omnipet.core.domain.incubation.EggDefinitionEnvelope;
import io.github.salyvn.omnipet.core.domain.incubation.HatchCandidate;
import io.github.salyvn.omnipet.core.incubation.IncubationDurationParser;

public final class EggDefinitionYamlCodec {
    private static final List<String> EGG_KEYS = List.of(
            "schemaVersion", "eggId", "tier", "baseDuration", "candidates");
    private static final List<String> CANDIDATE_KEYS = List.of("definitionId", "weight");

    public EggDefinitionEnvelope decode(String expectedId, String yaml) {
        String id = StableId.requireValid(expectedId);
        Map<String, Object> raw = YamlDocuments.readMap(yaml);
        int schema = integer(raw.getOrDefault("schemaVersion", 1), "schemaVersion");
        if (schema != EggDefinitionEnvelope.CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported egg definition schema version: " + schema);
        }
        if (raw.containsKey("eggId") && !id.equals(raw.get("eggId"))) {
            throw new IllegalArgumentException("eggId does not match filename");
        }
        PetTier tier = enumValue(raw.get("tier"), PetTier.class, "tier");
        long duration = IncubationDurationParser.parseMillis(text(raw.get("baseDuration"), "baseDuration"));
        List<Map<String, Object>> candidateNodes = mapList(raw.get("candidates"), "candidates");
        List<HatchCandidate> candidates = new ArrayList<>(candidateNodes.size());
        for (int index = 0; index < candidateNodes.size(); index++) {
            Map<String, Object> node = candidateNodes.get(index);
            String path = "candidates[" + index + "]";
            candidates.add(new HatchCandidate(
                    text(node.get("definitionId"), path + ".definitionId"),
                    number(node.get("weight"), path + ".weight"),
                    without(node, CANDIDATE_KEYS)));
        }
        return new EggDefinitionEnvelope(schema, new EggDefinition(
                id, tier, duration, candidates, without(raw, EGG_KEYS)));
    }

    public String encode(EggDefinitionEnvelope envelope) {
        if (envelope == null) throw new IllegalArgumentException("egg definition envelope is required");
        EggDefinition definition = envelope.definition();
        LinkedHashMap<String, Object> output = new LinkedHashMap<>(RawNodeValues.mutableMap(definition.extensions()));
        output.put("schemaVersion", EggDefinitionEnvelope.CURRENT_SCHEMA_VERSION);
        output.put("eggId", definition.id());
        output.put("tier", definition.tier().name());
        output.put("baseDuration", IncubationDurationParser.formatMillis(definition.baseActiveMillis()));
        List<Map<String, Object>> candidates = new ArrayList<>(definition.candidates().size());
        for (HatchCandidate candidate : definition.candidates()) {
            LinkedHashMap<String, Object> node = new LinkedHashMap<>(RawNodeValues.mutableMap(candidate.extensions()));
            node.put("definitionId", candidate.definitionId());
            node.put("weight", candidate.weight());
            candidates.add(node);
        }
        output.put("candidates", candidates);
        return YamlDocuments.writeMap(output);
    }

    private static List<Map<String, Object>> mapList(Object value, String path) {
        if (!(value instanceof List<?> list)) throw new IllegalArgumentException(path + " must be a list");
        List<Map<String, Object>> result = new ArrayList<>(list.size());
        for (int index = 0; index < list.size(); index++) {
            if (!(list.get(index) instanceof Map<?, ?> map)) {
                throw new IllegalArgumentException(path + "[" + index + "] must be a map");
            }
            result.add(PlayerStateYamlCodec.stringMap(map, path + "[" + index + "]"));
        }
        return result;
    }

    private static Map<String, Object> without(Map<String, Object> value, List<String> keys) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>(value);
        keys.forEach(result::remove);
        return RawNodeValues.immutableMap(result);
    }

    private static String text(Object value, String path) {
        if (!(value instanceof String text) || text.isBlank()) throw new IllegalArgumentException(path + " must be text");
        return text;
    }

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

    private static <T extends Enum<T>> T enumValue(Object value, Class<T> type, String path) {
        try { return Enum.valueOf(type, text(value, path).toUpperCase(java.util.Locale.ROOT)); }
        catch (RuntimeException error) { throw new IllegalArgumentException(path + " is invalid", error); }
    }
}
