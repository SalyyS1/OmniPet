package io.github.salyvn.omnipet.core.studio;

import java.time.Duration;
import java.util.Map;

import io.github.salyvn.omnipet.core.domain.RawNodeValues;

/** Opaque provider and namespaced skill reference; no vendor API is required. */
public record SkillReference(String provider, String id, String trigger, Duration cooldown, double chance,
                             double staminaCost, String targetPolicy, Map<String, Object> extensions) {
    public SkillReference {
        provider = StudioStat.requireReference(provider, "skill provider");
        id = StudioStat.requireReference(id, "skill id");
        if (trigger != null && trigger.isBlank()) throw new IllegalArgumentException("skill trigger cannot be blank");
        if (cooldown != null && cooldown.isNegative()) throw new IllegalArgumentException("skill cooldown cannot be negative");
        if (!Double.isFinite(chance) || chance < 0 || chance > 1) throw new IllegalArgumentException("skill chance must be in [0,1]");
        if (!Double.isFinite(staminaCost) || staminaCost < 0) throw new IllegalArgumentException("skill stamina cost must be non-negative");
        if (targetPolicy != null && targetPolicy.isBlank()) throw new IllegalArgumentException("skill target policy cannot be blank");
        extensions = RawNodeValues.immutableMap(extensions == null ? Map.of() : extensions);
    }

    public SkillReference(String provider, String id) {
        this(provider, id, null, null, 1.0, 0.0, null, Map.of());
    }
}
