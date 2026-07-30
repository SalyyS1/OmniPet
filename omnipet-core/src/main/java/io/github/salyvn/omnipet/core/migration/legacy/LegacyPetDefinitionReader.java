package io.github.salyvn.omnipet.core.migration.legacy;

import java.util.LinkedHashMap;
import java.util.Map;

import io.github.salyvn.omnipet.core.domain.PetDefinitionEnvelope;
import io.github.salyvn.omnipet.core.domain.RawNodeValues;
import io.github.salyvn.omnipet.core.domain.StableId;
import io.github.salyvn.omnipet.core.persistence.PetDefinitionYamlCodec;
import io.github.salyvn.omnipet.core.persistence.YamlDocuments;

public final class LegacyPetDefinitionReader {
    private final PetDefinitionYamlCodec codec = new PetDefinitionYamlCodec();

    public PetDefinitionEnvelope read(String id, String yaml) {
        StableId.requireValid(id);
        Map<String, Object> legacy = YamlDocuments.readMap(yaml);
        Map<String, Object> general = requiredMap(legacy.get("general"), "general");
        Object texture = general.get("texture");
        if (!(texture instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException("legacy general.texture is required for icon migration");
        }

        LinkedHashMap<String, Object> migrated = new LinkedHashMap<>();
        migrated.put("schemaVersion", PetDefinitionEnvelope.CURRENT_SCHEMA_VERSION);
        migrated.put("definitionId", id);
        migrated.put("revision", 0);
        migrated.put("classification", Map.of("tier", "D"));
        migrated.put("icon", Map.of("head", Map.of("source", "TEXTURE_URL", "value", text)));
        LinkedHashMap<String, Object> display = new LinkedHashMap<>();
        display.put("provider", "HEAD");
        display.put("model", null);
        migrated.put("display", display);
        migrated.put("extensions", Map.of("legacyComponents", RawNodeValues.mutableCopy(legacy)));
        return codec.decode(id, YamlDocuments.writeMap(migrated));
    }

    private static Map<String, Object> requiredMap(Object value, String path) {
        if (!(value instanceof Map<?, ?> map)) throw new IllegalArgumentException(path + " must be a map");
        return io.github.salyvn.omnipet.core.persistence.PlayerStateYamlCodec.stringMap(map, path);
    }
}
