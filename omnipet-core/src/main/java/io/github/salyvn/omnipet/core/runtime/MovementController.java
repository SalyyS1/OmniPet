package io.github.salyvn.omnipet.core.runtime;

import java.util.Objects;

/** Pure spring-damper steering with deterministic visual offsets and finite guards. */
public final class MovementController {
    private static final RuntimeVector DEFAULT_FORWARD = new RuntimeVector(0, 0, 1);

    public MovementStep step(MovementProfile profile, MovementInput input) {
        Objects.requireNonNull(profile, "movement profile");
        Objects.requireNonNull(input, "movement input");
        RuntimeVector forward = horizontal(input.ownerForward()).normalizedOr(DEFAULT_FORWARD);
        RuntimeVector side = new RuntimeVector(-forward.z(), 0, forward.x());
        double phase = input.phaseSeconds() + input.phaseOffsetRadians();
        return stepToward(profile, input, baseTarget(profile, input.ownerPosition(), forward, side, phase));
    }

    /**
     * Steers the pet towards an arbitrary target with the same spring-damper the pattern targets use.
     *
     * <p>Split out so idle play can hand in a target of its own — an orbit, a dart, a hop — and get the
     * identical motion physics rather than a second copy that could drift from this one. {@link #step} is
     * now just this with the pattern's own target.
     */
    public MovementStep stepToward(MovementProfile profile, MovementInput input, RuntimeVector target) {
        Objects.requireNonNull(profile, "movement profile");
        Objects.requireNonNull(input, "movement input");
        Objects.requireNonNull(target, "movement target");
        RuntimeVector error = target.subtract(input.currentPosition());
        double distance = error.length();
        if (!Double.isFinite(distance) || distance >= profile.safetySnapDistance()) {
            return new MovementStep(target, RuntimeVector.ZERO, target, true, false);
        }

        double delta = Math.min(input.deltaSeconds(), profile.maxDeltaSeconds());
        boolean dashing = distance >= profile.dashDistance() && profile.dashDistance() > 0;
        double speedLimit = profile.maxSpeed() * (dashing ? profile.dashSpeedMultiplier() : 1);
        RuntimeVector acceleration = error.multiply(profile.springStrength())
                .subtract(input.currentVelocity().multiply(profile.damping()))
                .clampLength(profile.maxAcceleration());
        RuntimeVector velocity = input.currentVelocity().add(acceleration.multiply(delta)).clampLength(speedLimit);
        RuntimeVector position = input.currentPosition().add(velocity.multiply(delta));
        return new MovementStep(position, velocity, target, false, dashing);
    }

    private static RuntimeVector baseTarget(
            MovementProfile profile,
            RuntimeVector owner,
            RuntimeVector forward,
            RuntimeVector side,
            double phase) {
        RuntimeVector base = owner
                .subtract(forward.multiply(profile.followDistance()))
                .add(side.multiply(profile.sideOffset()))
                .add(new RuntimeVector(0, profile.heightOffset(), 0));
        return switch (profile.pattern()) {
            case FOLLOW -> base;
            case ORBIT -> owner
                    .add(side.multiply(Math.cos(phase * profile.orbitRadiansPerSecond()) * profile.orbitRadius()))
                    .add(forward.multiply(Math.sin(phase * profile.orbitRadiansPerSecond()) * profile.orbitRadius()))
                    .add(new RuntimeVector(0, profile.heightOffset(), 0));
            case HOP -> base.add(new RuntimeVector(0,
                    Math.abs(Math.sin(phase * profile.bobRadiansPerSecond())) * profile.bobAmplitude(), 0));
            case HOVER -> base.add(new RuntimeVector(0,
                    Math.sin(phase * profile.bobRadiansPerSecond()) * profile.bobAmplitude(), 0));
        };
    }

    private static RuntimeVector horizontal(RuntimeVector vector) {
        return new RuntimeVector(vector.x(), 0, vector.z());
    }
}
