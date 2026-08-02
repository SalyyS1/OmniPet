package io.github.salyvn.omnipet.core.skill;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.github.salyvn.omnipet.core.domain.PetDefinition;

/** Typed, bounded projection over Studio's provider-neutral skill nodes. */
public final class SkillBindingProjection {
    public static final int MAX_BINDINGS = 64;

    private SkillBindingProjection() {}

    public static List<SkillBinding> read(PetDefinition definition) {
        Object raw = definition.rawNode().get("skills");
        if (!(raw instanceof List<?> nodes)) return List.of();
        List<SkillBinding> result = new ArrayList<>();
        for (int index = 0; index < Math.min(nodes.size(), MAX_BINDINGS); index++) {
            if (!(nodes.get(index) instanceof Map<?, ?> node)) continue;
            try {
                result.add(read(node, index));
            } catch (RuntimeException ignored) {
                // Invalid bindings remain persisted for Studio repair but are disabled at runtime.
            }
        }
        return List.copyOf(result);
    }

    private static SkillBinding read(Map<?, ?> node, int index) {
        String provider = text(node.get("provider"), "provider");
        String skillId = text(node.get("id"), "skill ID");
        String bindingId = node.get("bindingId") instanceof String value && !value.isBlank()
                ? value
                : "skill_" + index;
        SkillTrigger trigger = enumValue(node.get("trigger"), SkillTrigger.class, SkillTrigger.ACTIVE);
        Duration cooldown = node.get("cooldown") == null
                ? Duration.ZERO
                : Duration.parse(text(node.get("cooldown"), "cooldown"));
        double chance = number(node.get("chance"), 1.0);
        double stamina = number(node.get("staminaCost"), 0.0);
        SkillTargetPolicy target = enumValue(node.get("targetPolicy"), SkillTargetPolicy.class, SkillTargetPolicy.OWNER);
        boolean persist = Boolean.TRUE.equals(node.get("persistCooldown"));
        return new SkillBinding(bindingId, provider, skillId, trigger, cooldown, chance, stamina, target, persist);
    }

    private static String text(Object value, String label) {
        if (!(value instanceof String result) || result.isBlank()) {
            throw new IllegalArgumentException(label + " is required");
        }
        return result.trim();
    }

    private static double number(Object value, double fallback) {
        if (value == null) return fallback;
        if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())) {
            throw new IllegalArgumentException("skill numeric field must be finite");
        }
        return number.doubleValue();
    }

    private static <T extends Enum<T>> T enumValue(Object value, Class<T> type, T fallback) {
        if (value == null) return fallback;
        return Enum.valueOf(type, text(value, type.getSimpleName()).toUpperCase(Locale.ROOT));
    }
}
