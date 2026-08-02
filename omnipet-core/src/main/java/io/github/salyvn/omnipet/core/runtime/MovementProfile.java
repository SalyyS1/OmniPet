package io.github.salyvn.omnipet.core.runtime;

import java.util.Objects;

/** Validated steering profile. Dash is a catch-up overlay for every visual pattern. */
public record MovementProfile(
        MovementPattern pattern,
        double followDistance,
        double sideOffset,
        double heightOffset,
        double orbitRadius,
        double orbitRadiansPerSecond,
        double bobAmplitude,
        double bobRadiansPerSecond,
        double springStrength,
        double damping,
        double maxAcceleration,
        double maxSpeed,
        double dashDistance,
        double dashSpeedMultiplier,
        double safetySnapDistance,
        double maxDeltaSeconds) {
    public MovementProfile {
        pattern = Objects.requireNonNull(pattern, "movement pattern");
        requireNonNegative(followDistance, "follow distance");
        requireFinite(sideOffset, "side offset");
        requireFinite(heightOffset, "height offset");
        requireNonNegative(orbitRadius, "orbit radius");
        requireNonNegative(orbitRadiansPerSecond, "orbit speed");
        requireNonNegative(bobAmplitude, "bob amplitude");
        requireNonNegative(bobRadiansPerSecond, "bob speed");
        requirePositive(springStrength, "spring strength");
        requireNonNegative(damping, "damping");
        requirePositive(maxAcceleration, "maximum acceleration");
        requirePositive(maxSpeed, "maximum speed");
        requireNonNegative(dashDistance, "dash distance");
        if (!Double.isFinite(dashSpeedMultiplier) || dashSpeedMultiplier < 1) {
            throw new IllegalArgumentException("dash speed multiplier must be finite and at least one");
        }
        requirePositive(safetySnapDistance, "safety snap distance");
        requirePositive(maxDeltaSeconds, "maximum delta seconds");
        if (dashDistance >= safetySnapDistance) {
            throw new IllegalArgumentException("dash distance must be smaller than safety snap distance");
        }
    }

    public static MovementProfile defaults() {
        return new MovementProfile(
                MovementPattern.FOLLOW,
                1.8,
                0.7,
                1.25,
                1.5,
                Math.PI / 2,
                0.18,
                Math.PI * 2,
                18,
                7,
                24,
                6,
                5,
                1.75,
                24,
                0.25);
    }

    private static void requireFinite(double value, String label) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException(label + " must be finite");
    }

    private static void requireNonNegative(double value, String label) {
        if (!Double.isFinite(value) || value < 0) {
            throw new IllegalArgumentException(label + " must be finite and non-negative");
        }
    }

    private static void requirePositive(double value, String label) {
        if (!Double.isFinite(value) || value <= 0) {
            throw new IllegalArgumentException(label + " must be finite and positive");
        }
    }
}
