package io.github.salyvn.omnipet.core.management;

import java.util.LinkedHashMap;
import java.util.Map;

import io.github.salyvn.omnipet.core.domain.PetInstance;
import io.github.salyvn.omnipet.core.domain.RawNodeValues;

/** Unknown-preserving projection over the per-instance management extension node. */
public record PetManagementMetadata(boolean favorite, boolean locked, String customName) {
    private static final String NODE = "management";

    public PetManagementMetadata {
        customName = customName == null ? "" : customName.trim();
        if (customName.length() > 48) throw new IllegalArgumentException("pet custom name is too long");
    }

    public static PetManagementMetadata read(PetInstance pet) {
        Map<String, Object> values = node(pet.extensions().get(NODE));
        return new PetManagementMetadata(
                truthy(values.get("favorite")),
                truthy(values.get("locked")),
                values.get("customName") instanceof String name ? name : "");
    }

    public static PetInstance write(PetInstance pet, PetManagementMetadata metadata) {
        LinkedHashMap<String, Object> extensions = new LinkedHashMap<>(pet.extensions());
        LinkedHashMap<String, Object> management = new LinkedHashMap<>(node(extensions.get(NODE)));
        management.put("favorite", metadata.favorite());
        management.put("locked", metadata.locked());
        if (metadata.customName().isEmpty()) management.remove("customName");
        else management.put("customName", metadata.customName());
        extensions.put(NODE, management);
        return new PetInstance(
                pet.id(), pet.definitionId(), pet.definitionRevision(), pet.rawComponents(), extensions);
    }

    public PetManagementMetadata withFavorite(boolean next) {
        return new PetManagementMetadata(next, locked, customName);
    }

    public PetManagementMetadata withLocked(boolean next) {
        return new PetManagementMetadata(favorite, next, customName);
    }

    public PetManagementMetadata withCustomName(String next) {
        return new PetManagementMetadata(favorite, locked, next);
    }

    private static Map<String, Object> node(Object raw) {
        if (!(raw instanceof Map<?, ?> source)) return Map.of();
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (key instanceof String text) result.put(text, value);
        });
        return RawNodeValues.immutableMap(result);
    }

    private static boolean truthy(Object value) {
        return Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(String.valueOf(value));
    }
}
