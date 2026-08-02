package io.github.salyvn.omnipet.core.runtime;

import java.util.Objects;

public record MovementStep(
        RuntimeVector position,
        RuntimeVector velocity,
        RuntimeVector target,
        boolean safetySnap,
        boolean dashing) {
    public MovementStep {
        position = Objects.requireNonNull(position, "movement position");
        velocity = Objects.requireNonNull(velocity, "movement velocity");
        target = Objects.requireNonNull(target, "movement target");
        if (safetySnap && dashing) throw new IllegalArgumentException("a safety snap is not a dash");
    }
}
