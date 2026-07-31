package io.github.salyvn.omnipet.core.persistence;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.github.salyvn.omnipet.core.domain.PetInstance;
import io.github.salyvn.omnipet.core.domain.PlayerState;
import io.github.salyvn.omnipet.core.domain.RawNodeValues;
import io.github.salyvn.omnipet.core.domain.SlotEntitlement;

final class PlayerStorageYamlCodec {
    private static final List<String> ENTITLEMENT_KEYS = List.of("slot", "source", "referenceId");

    private PlayerStorageYamlCodec() {}

    static DecodedStorage decode(
            Map<String, Object> raw,
            int schema,
            List<PetInstance> pets,
            Integer currentIndex,
            List<String> appliedMigrations,
            List<String> warnings) {
        int vaultCapacity = schema < 3
                ? legacyVaultCapacity(raw.get("capacity"), warnings)
                : boundedInteger(raw.getOrDefault("vaultCapacity", 0), "vaultCapacity", 0,
                        PlayerState.MAX_VAULT_CAPACITY);
        if (schema < 3 && raw.containsKey("capacity")) {
            appliedMigrations.add("CAPACITY_TO_VAULT_CAPACITY");
        }
        int activeSlotCount = schema < 3
                ? 1
                : boundedInteger(raw.getOrDefault("activeSlotCount", 1), "activeSlotCount", 1,
                        PlayerState.MAX_ACTIVE_SLOT_COUNT);
        List<UUID> desiredActivePetIds = schema < 3
                ? desiredFromLegacyIndex(currentIndex, pets, appliedMigrations)
                : uuidList(raw.getOrDefault("desiredActivePetIds", List.of()), "desiredActivePetIds");
        List<SlotEntitlement> slotEntitlements = schema < 3
                ? List.of()
                : entitlements(raw.getOrDefault("slotEntitlements", List.of()));
        return new DecodedStorage(vaultCapacity, activeSlotCount, desiredActivePetIds, slotEntitlements);
    }

    static List<Map<String, Object>> encodeEntitlements(List<SlotEntitlement> entitlements) {
        List<Map<String, Object>> output = new ArrayList<>(entitlements.size());
        for (SlotEntitlement entitlement : entitlements) {
            LinkedHashMap<String, Object> value = new LinkedHashMap<>(RawNodeValues.mutableMap(entitlement.extensions()));
            value.put("slot", entitlement.slot());
            value.put("source", entitlement.source());
            value.put("referenceId", entitlement.referenceId());
            output.add(value);
        }
        return output;
    }

    private static int legacyVaultCapacity(Object value, List<String> warnings) {
        if (value == null) return 0;
        int capacity = integer(value, "capacity");
        if (capacity == -1) {
            warnings.add("legacy capacity -1 cache sentinel was normalized to vaultCapacity 0");
            return 0;
        }
        if (capacity < 0 || capacity > PlayerState.MAX_VAULT_CAPACITY) {
            throw new IllegalArgumentException("capacity is outside the supported range");
        }
        return capacity;
    }

    private static int boundedInteger(Object value, String path, int minimum, int maximum) {
        int result = integer(value, path);
        if (result < minimum || result > maximum) {
            throw new IllegalArgumentException(path + " is outside the supported range");
        }
        return result;
    }

    private static List<UUID> desiredFromLegacyIndex(
            Integer currentIndex,
            List<PetInstance> pets,
            List<String> appliedMigrations) {
        if (currentIndex == null || currentIndex < 0) return List.of();
        appliedMigrations.add("CURRENT_INDEX_TO_DESIRED_ACTIVE_ID");
        return List.of(pets.get(currentIndex).id());
    }

    private static List<UUID> uuidList(Object value, String path) {
        if (!(value instanceof List<?> list)) throw new IllegalArgumentException(path + " must be a list");
        List<UUID> result = new ArrayList<>(list.size());
        for (int index = 0; index < list.size(); index++) {
            result.add(uuid(list.get(index), path + "[" + index + "]"));
        }
        return List.copyOf(result);
    }

    private static List<SlotEntitlement> entitlements(Object value) {
        if (!(value instanceof List<?> list)) throw new IllegalArgumentException("slotEntitlements must be a list");
        List<SlotEntitlement> result = new ArrayList<>(list.size());
        for (int index = 0; index < list.size(); index++) {
            if (!(list.get(index) instanceof Map<?, ?> raw)) {
                throw new IllegalArgumentException("slotEntitlements[" + index + "] must be a map");
            }
            Map<String, Object> entitlement = PlayerStateYamlCodec.stringMap(
                    raw, "slotEntitlements[" + index + "]");
            result.add(new SlotEntitlement(
                    integer(entitlement.get("slot"), "slotEntitlements[" + index + "].slot"),
                    string(entitlement.get("source"), "slotEntitlements[" + index + "].source"),
                    string(entitlement.get("referenceId"), "slotEntitlements[" + index + "].referenceId"),
                    without(entitlement, ENTITLEMENT_KEYS)));
        }
        return List.copyOf(result);
    }

    private static Map<String, Object> without(Map<String, Object> value, List<String> keys) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>(value);
        keys.forEach(result::remove);
        return result;
    }

    private static String string(Object value, String path) {
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException(path + " must be a non-blank string");
        }
        return text;
    }

    private static UUID uuid(Object value, String path) {
        try {
            return UUID.fromString(string(value, path));
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException(path + " must be a UUID", error);
        }
    }

    private static int integer(Object value, String path) {
        if (!(value instanceof Number number)) throw new IllegalArgumentException(path + " must be numeric");
        long result = number.longValue();
        if (number.doubleValue() != result || result < Integer.MIN_VALUE || result > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(path + " must be an in-range integer");
        }
        return (int) result;
    }

    record DecodedStorage(
            int vaultCapacity,
            int activeSlotCount,
            List<UUID> desiredActivePetIds,
            List<SlotEntitlement> slotEntitlements) {}
}
