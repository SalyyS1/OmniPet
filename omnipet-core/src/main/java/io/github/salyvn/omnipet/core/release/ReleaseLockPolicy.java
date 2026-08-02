package io.github.salyvn.omnipet.core.release;

import java.util.Map;

import io.github.salyvn.omnipet.core.domain.PetInstance;

@FunctionalInterface
public interface ReleaseLockPolicy {
    boolean isLocked(PetInstance pet);

    static ReleaseLockPolicy standard() {
        return pet -> flag(pet.rawComponents()) || flag(pet.extensions());
    }

    private static boolean flag(Map<String, Object> values) {
        if (truthy(values.get("locked"))) return true;
        Object management = values.get("management");
        return management instanceof Map<?, ?> map && truthy(map.get("locked"));
    }

    private static boolean truthy(Object value) {
        return Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(String.valueOf(value));
    }
}
