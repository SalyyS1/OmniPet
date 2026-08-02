package io.github.salyvn.omnipet.core.incubation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class IncubationItemActionItemContract {
    public static final String TYPE_KEY = "incubationActionType";
    public static final String EFFECT_MILLIS_KEY = "incubationActionEffectMillis";

    private IncubationItemActionItemContract() {}

    public static Map<String, Object> bind(
            Map<String, Object> extensions,
            IncubationItemActionType type,
            long effectMillis) {
        validateEffect(type, effectMillis);
        Map<String, Object> bound = new LinkedHashMap<>(extensions == null ? Map.of() : extensions);
        bound.put(TYPE_KEY, type.name());
        bound.put(EFFECT_MILLIS_KEY, Long.toString(effectMillis));
        return Map.copyOf(bound);
    }

    public static void requireMatches(
            EggItemIdentity item,
            IncubationItemActionType type,
            long effectMillis) {
        Objects.requireNonNull(item, "incubation action item identity");
        validateEffect(type, effectMillis);
        Object itemType = item.extensions().get(TYPE_KEY);
        Object itemEffect = item.extensions().get(EFFECT_MILLIS_KEY);
        if (!(itemType instanceof String name) || !type.name().equals(name)) {
            throw new IllegalArgumentException("incubation action item type does not match transaction type");
        }
        if (!(itemEffect instanceof String value) || !Long.toString(effectMillis).equals(value)) {
            throw new IllegalArgumentException("incubation action item effect does not match transaction effect");
        }
    }

    private static void validateEffect(IncubationItemActionType type, long effectMillis) {
        Objects.requireNonNull(type, "incubation action item type");
        if (type == IncubationItemActionType.REDUCE && effectMillis <= 0) {
            throw new IllegalArgumentException("REDUCE action item effect must be positive");
        }
        if (type == IncubationItemActionType.COMPLETE && effectMillis != 0) {
            throw new IllegalArgumentException("COMPLETE action item effect must be zero");
        }
    }
}
