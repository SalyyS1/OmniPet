package io.github.salyvn.omnipet.core.persistence;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.github.salyvn.omnipet.core.domain.PetInstance;
import io.github.salyvn.omnipet.core.domain.PlayerState;
import io.github.salyvn.omnipet.core.domain.PlayerStateEnvelope;
import io.github.salyvn.omnipet.core.domain.RawNodeValues;

public final class PlayerStateYamlCodec {
    private static final List<String> PLAYER_KEYS = List.of(
            "schemaVersion", "uuid", "revision", "pets", "currentPetIndex", "currentEgg", "capacity",
            "vaultCapacity", "activeSlotCount", "desiredActivePetIds", "slotEntitlements");
    private static final List<String> PET_KEYS = List.of(
            "id", "definitionId", "definitionRevision", "type", "components");

    public PlayerMigrationResult decodeWithReport(String yaml) {
        Map<String, Object> raw = YamlDocuments.readMap(yaml);
        int schema = integer(raw.getOrDefault("schemaVersion", 1), "schemaVersion");
        if (schema < 1 || schema > PlayerStateEnvelope.CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported player schema version: " + schema);
        }
        UUID playerId = uuid(raw.get("uuid"), "uuid");
        long revision = longValue(raw.getOrDefault("revision", 0L), "revision");
        Object rawPets = raw.getOrDefault("pets", List.of());
        if (!(rawPets instanceof List<?> list)) throw new IllegalArgumentException("pets must be a list");

        List<PetInstance> pets = new ArrayList<>(list.size());
        List<String> assigned = new ArrayList<>();
        for (int index = 0; index < list.size(); index++) {
            if (!(list.get(index) instanceof Map<?, ?> value)) {
                throw new IllegalArgumentException("pets[" + index + "] must be a map");
            }
            Map<String, Object> pet = stringMap(value, "pets[" + index + "]");
            UUID instanceId;
            if (pet.containsKey("id")) {
                instanceId = uuid(pet.get("id"), "pets[" + index + "].id");
            } else {
                instanceId = stableInstanceId(playerId, index, pet);
                assigned.add(instanceId.toString());
            }
            String definitionId = string(pet.getOrDefault("definitionId", pet.get("type")),
                    "pets[" + index + "].definitionId");
            long definitionRevision = longValue(pet.getOrDefault("definitionRevision", 0L),
                    "pets[" + index + "].definitionRevision");
            Map<String, Object> components = mapOrEmpty(pet.get("components"));
            Map<String, Object> extensions = without(pet, PET_KEYS);
            pets.add(new PetInstance(instanceId, definitionId, definitionRevision, components, extensions));
        }

        List<String> warnings = new ArrayList<>();
        List<String> appliedMigrations = new ArrayList<>();
        if (!assigned.isEmpty()) appliedMigrations.add("ASSIGN_STABLE_PET_IDS");

        Integer currentIndex = optionalInteger(raw.get("currentPetIndex"), "currentPetIndex");
        if (currentIndex != null && (currentIndex < -1 || currentIndex >= pets.size())) {
            currentIndex = -1;
            warnings.add("currentPetIndex was stale and normalized to -1");
        }
        PlayerStorageYamlCodec.DecodedStorage storage = PlayerStorageYamlCodec.decode(
                raw, schema, pets, currentIndex, appliedMigrations, warnings);
        Map<String, Object> currentEgg = mapOrEmpty(raw.get("currentEgg"));
        Map<String, Object> extensions = without(raw, PLAYER_KEYS);
        PlayerState state = new PlayerState(
                playerId,
                revision,
                pets,
                storage.vaultCapacity(),
                storage.activeSlotCount(),
                storage.desiredActivePetIds(),
                storage.slotEntitlements(),
                currentEgg,
                extensions);
        boolean migrated = schema < PlayerStateEnvelope.CURRENT_SCHEMA_VERSION
                || !assigned.isEmpty()
                || raw.containsKey("capacity")
                || raw.containsKey("currentPetIndex");
        return new PlayerMigrationResult(
                new PlayerStateEnvelope(PlayerStateEnvelope.CURRENT_SCHEMA_VERSION, state),
                new MigrationReport(schema, migrated, assigned, appliedMigrations, warnings));
    }

    public PlayerStateEnvelope decode(String yaml) {
        return decodeWithReport(yaml).envelope();
    }

    public PlayerStateEnvelope decodeCurrent(String yaml) {
        PlayerMigrationResult result = decodeWithReport(yaml);
        if (result.report().sourceSchemaVersion() != PlayerStateEnvelope.CURRENT_SCHEMA_VERSION
                || result.report().migrated()) {
            throw new IllegalArgumentException("runtime repository requires the current player schema");
        }
        return result.envelope();
    }

    public String encode(PlayerStateEnvelope envelope) {
        if (envelope == null) throw new IllegalArgumentException("envelope is required");
        PlayerState state = envelope.state();
        LinkedHashMap<String, Object> output = new LinkedHashMap<>();
        output.put("schemaVersion", PlayerStateEnvelope.CURRENT_SCHEMA_VERSION);
        output.put("uuid", state.playerId().toString());
        output.put("revision", state.revision());
        List<Map<String, Object>> pets = new ArrayList<>(state.pets().size());
        for (PetInstance instance : state.pets()) {
            LinkedHashMap<String, Object> pet = new LinkedHashMap<>(RawNodeValues.mutableMap(instance.extensions()));
            pet.put("id", instance.id().toString());
            pet.put("definitionId", instance.definitionId());
            pet.put("definitionRevision", instance.definitionRevision());
            pet.put("components", RawNodeValues.mutableCopy(instance.rawComponents()));
            pets.add(pet);
        }
        output.put("pets", pets);
        output.put("vaultCapacity", state.vaultCapacity());
        output.put("activeSlotCount", state.activeSlotCount());
        output.put("desiredActivePetIds", state.desiredActivePetIds().stream().map(UUID::toString).toList());
        output.put("slotEntitlements", PlayerStorageYamlCodec.encodeEntitlements(state.slotEntitlements()));
        if (!state.legacyCurrentEgg().isEmpty()) output.put("currentEgg", RawNodeValues.mutableCopy(state.legacyCurrentEgg()));
        state.extensions().forEach((key, value) -> output.putIfAbsent(key, RawNodeValues.mutableCopy(value)));
        return YamlDocuments.writeMap(output);
    }

    public byte[] encodeBytes(PlayerStateEnvelope envelope) {
        return encode(envelope).getBytes(StandardCharsets.UTF_8);
    }

    private static UUID stableInstanceId(UUID playerId, int index, Map<String, Object> rawPet) {
        byte[] prefix = (playerId + ":" + index + ":").getBytes(StandardCharsets.UTF_8);
        byte[] semantic = RawNodeValues.semanticBytes(rawPet);
        byte[] input = new byte[prefix.length + semantic.length];
        System.arraycopy(prefix, 0, input, 0, prefix.length);
        System.arraycopy(semantic, 0, input, prefix.length, semantic.length);
        return UUID.nameUUIDFromBytes(input);
    }

    public static Map<String, Object> stringMap(Map<?, ?> value, String path) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        value.forEach((key, nested) -> result.put(String.valueOf(key), RawNodeValues.mutableCopy(nested)));
        if (result.keySet().stream().anyMatch(String::isBlank)) throw new IllegalArgumentException(path + " has a blank key");
        return result;
    }

    private static Map<String, Object> mapOrEmpty(Object value) {
        return value == null ? Map.of() : value instanceof Map<?, ?> map ? stringMap(map, "map") : throwType("map");
    }

    private static Map<String, Object> without(Map<String, Object> value, List<String> keys) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>(value);
        keys.forEach(result::remove);
        return result;
    }

    private static String string(Object value, String path) {
        if (!(value instanceof String text) || text.isBlank()) throw new IllegalArgumentException(path + " must be a non-blank string");
        return text;
    }

    private static UUID uuid(Object value, String path) {
        try {
            return UUID.fromString(string(value, path));
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException(path + " must be a UUID", error);
        }
    }

    private static long longValue(Object value, String path) {
        if (!(value instanceof Number number)) throw new IllegalArgumentException(path + " must be numeric");
        long result = number.longValue();
        if (number.doubleValue() != result) throw new IllegalArgumentException(path + " must be an integer");
        return result;
    }

    private static int integer(Object value, String path) {
        long result = longValue(value, path);
        if (result < Integer.MIN_VALUE || result > Integer.MAX_VALUE) throw new IllegalArgumentException(path + " is out of range");
        return (int) result;
    }

    private static Integer optionalInteger(Object value, String path) {
        return value == null ? null : integer(value, path);
    }

    private static <T> T throwType(String type) {
        throw new IllegalArgumentException("expected " + type);
    }
}
