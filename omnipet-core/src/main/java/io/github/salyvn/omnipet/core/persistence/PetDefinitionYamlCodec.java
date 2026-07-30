package io.github.salyvn.omnipet.core.persistence;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.salyvn.omnipet.core.domain.DisplayDefinition;
import io.github.salyvn.omnipet.core.domain.HeadIcon;
import io.github.salyvn.omnipet.core.domain.PetDefinition;
import io.github.salyvn.omnipet.core.domain.PetDefinitionEnvelope;
import io.github.salyvn.omnipet.core.domain.PetTier;
import io.github.salyvn.omnipet.core.domain.RawNodeValues;
import io.github.salyvn.omnipet.core.domain.StableId;

public final class PetDefinitionYamlCodec {
    public PetDefinitionEnvelope decode(String expectedId, String yaml) {
        String id = StableId.requireValid(expectedId);
        Map<String, Object> raw = YamlDocuments.readMap(yaml);
        int schema = integer(raw.getOrDefault("schemaVersion", 1), "schemaVersion");
        if (schema < 1 || schema > PetDefinitionEnvelope.CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported pet definition schema version: " + schema);
        }
        if (raw.containsKey("definitionId") && !id.equals(raw.get("definitionId"))) {
            throw new IllegalArgumentException("definitionId does not match filename");
        }
        long revision = longValue(raw.getOrDefault("revision", 0L), "revision");
        Map<String, Object> classification = map(raw.get("classification"), "classification");
        PetTier tier = enumValue(classification.get("tier"), PetTier.class, "classification.tier");
        Map<String, Object> icon = map(raw.get("icon"), "icon");
        Map<String, Object> head = map(icon.get("head"), "icon.head");
        HeadIcon headIcon = new HeadIcon(string(head.get("source"), "icon.head.source"), string(head.get("value"), "icon.head.value"));
        Map<String, Object> display = map(raw.get("display"), "display");
        DisplayDefinition.Provider provider = enumValue(display.getOrDefault("provider", "HEAD"), DisplayDefinition.Provider.class, "display.provider");
        String model = display.get("model") == null ? null : string(display.get("model"), "display.model");
        validateStats(raw.get("stats"));
        PetDefinition definition = new PetDefinition(id, revision, tier, headIcon, new DisplayDefinition(provider, model), raw);
        return new PetDefinitionEnvelope(PetDefinitionEnvelope.CURRENT_SCHEMA_VERSION, definition);
    }

    public String encode(PetDefinitionEnvelope envelope) {
        PetDefinition definition = envelope.definition();
        LinkedHashMap<String, Object> raw = new LinkedHashMap<>(RawNodeValues.mutableMap(definition.rawNode()));
        validateStats(raw.get("stats"));
        raw.put("schemaVersion", PetDefinitionEnvelope.CURRENT_SCHEMA_VERSION);
        raw.put("definitionId", definition.id());
        raw.put("revision", definition.revision());
        raw.put("classification", new LinkedHashMap<>(Map.of("tier", definition.tier().name())));
        raw.put("icon", new LinkedHashMap<>(Map.of("head", new LinkedHashMap<>(Map.of(
                "source", definition.icon().source(), "value", definition.icon().value())))));
        LinkedHashMap<String, Object> display = new LinkedHashMap<>();
        display.put("provider", definition.display().provider().name());
        display.put("model", definition.display().model());
        raw.put("display", display);
        return YamlDocuments.writeMap(raw);
    }

    private static void validateStats(Object value) {
        if (value == null) return;
        if (!(value instanceof List<?> list)) throw new IllegalArgumentException("stats must be a list");
        for (int index = 0; index < list.size(); index++) {
            if (!(list.get(index) instanceof Map<?, ?> map)) throw new IllegalArgumentException("stats[" + index + "] must be a map");
            String path = "stats[" + index + "]";
            double minimum = finiteNumber(map.get("min"), path + ".min");
            double maximum = finiteNumber(map.get("max"), path + ".max");
            if (minimum > maximum) throw new IllegalArgumentException(path + ".min must be <= " + path + ".max");
        }
    }

    private static double finiteNumber(Object value, String path) {
        if (!(value instanceof Number number)) throw new IllegalArgumentException(path + " must be a finite number");
        double converted = number.doubleValue();
        if (!Double.isFinite(converted)) throw new IllegalArgumentException(path + " must be a finite number");
        return converted;
    }

    private static Map<String, Object> map(Object value, String path) {
        if (!(value instanceof Map<?, ?> map)) throw new IllegalArgumentException(path + " must be a map");
        return PlayerStateYamlCodec.stringMap(map, path);
    }

    private static String string(Object value, String path) {
        if (!(value instanceof String text) || text.isBlank()) throw new IllegalArgumentException(path + " must be a non-blank string");
        return text;
    }

    private static int integer(Object value, String path) {
        long number = longValue(value, path);
        if (number < Integer.MIN_VALUE || number > Integer.MAX_VALUE) throw new IllegalArgumentException(path + " is out of range");
        return (int) number;
    }

    private static long longValue(Object value, String path) {
        if (!(value instanceof Number number) || number.doubleValue() != number.longValue()) throw new IllegalArgumentException(path + " must be an integer");
        return number.longValue();
    }

    private static <T extends Enum<T>> T enumValue(Object value, Class<T> type, String path) {
        try {
            return Enum.valueOf(type, string(value, path).toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException(path + " is invalid", error);
        }
    }
}
