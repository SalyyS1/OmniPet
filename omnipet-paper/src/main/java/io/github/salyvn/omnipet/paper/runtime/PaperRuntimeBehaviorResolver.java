package io.github.salyvn.omnipet.paper.runtime;

import java.util.Locale;
import java.util.Map;

import io.github.salyvn.omnipet.core.runtime.MovementPattern;
import io.github.salyvn.omnipet.core.runtime.MovementProfile;

/** Reads optional Studio behavior extensions without making them a required schema contract. */
final class PaperRuntimeBehaviorResolver {
    private PaperRuntimeBehaviorResolver() {}

    static ResolvedBehavior resolve(Map<String, Object> rawNode) {
        MovementProfile defaults = MovementProfile.defaults();
        Map<?, ?> behavior = map(rawNode == null ? null : rawNode.get("behavior"));
        MovementPattern pattern = enumValue(behavior, "pattern", defaults.pattern());
        MovementProfile movement = new MovementProfile(
                pattern,
                number(behavior, "followDistance", defaults.followDistance()),
                number(behavior, "sideOffset", defaults.sideOffset()),
                number(behavior, "heightOffset", defaults.heightOffset()),
                number(behavior, "orbitRadius", defaults.orbitRadius()),
                number(behavior, "orbitRadiansPerSecond", defaults.orbitRadiansPerSecond()),
                number(behavior, "bobAmplitude", defaults.bobAmplitude()),
                number(behavior, "bobRadiansPerSecond", defaults.bobRadiansPerSecond()),
                number(behavior, "springStrength", defaults.springStrength()),
                number(behavior, "damping", defaults.damping()),
                number(behavior, "maxAcceleration", defaults.maxAcceleration()),
                number(behavior, "maxSpeed", defaults.maxSpeed()),
                number(behavior, "dashDistance", defaults.dashDistance()),
                number(behavior, "dashSpeedMultiplier", defaults.dashSpeedMultiplier()),
                number(behavior, "safetySnapDistance", defaults.safetySnapDistance()),
                number(behavior, "maxDeltaSeconds", defaults.maxDeltaSeconds()));
        double scale = number(behavior, "scale", 1.0);
        if (!Double.isFinite(scale) || scale <= 0 || scale > 64) {
            throw new IllegalArgumentException("behavior.scale is outside the supported range");
        }
        return new ResolvedBehavior(movement, scale);
    }

    private static Map<?, ?> map(Object value) {
        if (value == null) return Map.of();
        if (value instanceof Map<?, ?> map) return map;
        throw new IllegalArgumentException("behavior must be a map");
    }

    private static double number(Map<?, ?> source, String key, double fallback) {
        Object value = source.get(key);
        if (value == null) return fallback;
        if (value instanceof Number number) return number.doubleValue();
        throw new IllegalArgumentException("behavior." + key + " must be numeric");
    }

    private static MovementPattern enumValue(Map<?, ?> source, String key, MovementPattern fallback) {
        Object value = source.get(key);
        if (value == null) return fallback;
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException("behavior." + key + " must be a movement pattern");
        }
        try {
            return MovementPattern.valueOf(text.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("behavior." + key + " is unsupported", failure);
        }
    }

    record ResolvedBehavior(MovementProfile movement, double scale) {}
}
