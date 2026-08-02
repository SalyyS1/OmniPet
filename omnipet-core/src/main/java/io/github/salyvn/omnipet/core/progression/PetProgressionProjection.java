package io.github.salyvn.omnipet.core.progression;

import java.util.LinkedHashMap;
import java.util.Map;

import io.github.salyvn.omnipet.core.domain.PetInstance;

/** Projects cultivation state into the preserved pet component map. */
public final class PetProgressionProjection {
    public static final String COMPONENT_KEY = "progression";

    private PetProgressionProjection() {}

    public static ProgressionState read(PetInstance pet, double initialStamina, long nowEpochMillis) {
        if (pet == null) throw new IllegalArgumentException("pet instance is required");
        Object raw = pet.rawComponents().get(COMPONENT_KEY);
        if (raw == null) return ProgressionState.initial(initialStamina, nowEpochMillis);
        if (!(raw instanceof Map<?, ?> values)) {
            throw new IllegalArgumentException("pet progression component must be a map");
        }
        return new ProgressionState(
                integer(values, "level"),
                decimal(values, "experience"),
                integer(values, "evolution"),
                decimal(values, "stamina"),
                longValue(values, "lastStaminaEpochMillis"),
                extensions(values));
    }

    public static PetInstance write(PetInstance pet, ProgressionState state) {
        if (pet == null) throw new IllegalArgumentException("pet instance is required");
        if (state == null) throw new IllegalArgumentException("progression state is required");
        Map<String, Object> progression = new LinkedHashMap<>();
        progression.put("level", state.level());
        progression.put("experience", state.experience());
        progression.put("evolution", state.evolution());
        progression.put("stamina", state.stamina());
        progression.put("lastStaminaEpochMillis", state.lastStaminaEpochMillis());
        if (!state.extensions().isEmpty()) progression.put("extensions", state.extensions());

        Map<String, Object> components = new LinkedHashMap<>(pet.rawComponents());
        components.put(COMPONENT_KEY, progression);
        return new PetInstance(pet.id(), pet.definitionId(), pet.definitionRevision(), components, pet.extensions());
    }

    private static int integer(Map<?, ?> values, String key) {
        long value = longValue(values, key);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("pet progression " + key + " is outside integer range");
        }
        return (int) value;
    }

    private static long longValue(Map<?, ?> values, String key) {
        Object raw = values.get(key);
        if (!(raw instanceof Number number)) {
            throw new IllegalArgumentException("pet progression " + key + " must be numeric");
        }
        double decimal = number.doubleValue();
        long value = number.longValue();
        if (!Double.isFinite(decimal) || decimal != value) {
            throw new IllegalArgumentException("pet progression " + key + " must be an exact integer");
        }
        return value;
    }

    private static double decimal(Map<?, ?> values, String key) {
        Object raw = values.get(key);
        if (!(raw instanceof Number number) || !Double.isFinite(number.doubleValue())) {
            throw new IllegalArgumentException("pet progression " + key + " must be finite numeric");
        }
        return number.doubleValue();
    }

    private static Map<String, Object> extensions(Map<?, ?> values) {
        Object raw = values.get("extensions");
        if (raw == null) return Map.of();
        if (!(raw instanceof Map<?, ?> entries)) {
            throw new IllegalArgumentException("pet progression extensions must be a map");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        entries.forEach((key, value) -> {
            if (!(key instanceof String text) || text.isBlank()) {
                throw new IllegalArgumentException("pet progression extension key is invalid");
            }
            result.put(text, value);
        });
        return result;
    }
}
