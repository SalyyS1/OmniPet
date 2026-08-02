package io.github.salyvn.omnipet.core.runtime;

import java.util.Objects;

public record MovementInput(
        RuntimeVector ownerPosition,
        RuntimeVector ownerForward,
        RuntimeVector currentPosition,
        RuntimeVector currentVelocity,
        double deltaSeconds,
        double phaseSeconds,
        double phaseOffsetRadians) {
    public MovementInput {
        ownerPosition = Objects.requireNonNull(ownerPosition, "owner position");
        ownerForward = Objects.requireNonNull(ownerForward, "owner forward");
        currentPosition = Objects.requireNonNull(currentPosition, "current position");
        currentVelocity = Objects.requireNonNull(currentVelocity, "current velocity");
        if (!Double.isFinite(deltaSeconds) || deltaSeconds < 0) {
            throw new IllegalArgumentException("movement delta must be finite and non-negative");
        }
        if (!Double.isFinite(phaseSeconds) || !Double.isFinite(phaseOffsetRadians)) {
            throw new IllegalArgumentException("movement phase must be finite");
        }
    }
}
