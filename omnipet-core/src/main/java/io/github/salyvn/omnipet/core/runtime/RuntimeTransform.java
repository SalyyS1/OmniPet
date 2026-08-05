package io.github.salyvn.omnipet.core.runtime;

import java.util.Objects;

/**
 * Where a pet is, how it is moving, and what it is doing about standing still.
 *
 * <p>Motion rides along with the pose instead of getting its own port method. The steering controller
 * already computes velocity and the dash flag every tick, and this record already flows spawn-to-update,
 * so carrying them here costs nothing and lets a renderer animate without every implementation having to
 * grow a method it may not support. The idle pose travels the same way for the same reason.
 *
 * <p>{@code velocity} is metres per second in world space, not a per-tick delta.
 */
public record RuntimeTransform(
        RuntimeVector position,
        float yaw,
        float pitch,
        double scale,
        RuntimeVector velocity,
        boolean dashing,
        IdleBehaviour.Pose idle) {
    public RuntimeTransform {
        position = Objects.requireNonNull(position, "runtime transform position");
        velocity = Objects.requireNonNull(velocity, "runtime transform velocity");
        idle = idle == null ? IdleBehaviour.Pose.ACTIVE : idle;
        if (!Float.isFinite(yaw) || !Float.isFinite(pitch)) {
            throw new IllegalArgumentException("runtime transform rotation must be finite");
        }
        if (!Double.isFinite(scale) || scale <= 0 || scale > 64) {
            throw new IllegalArgumentException("runtime transform scale is outside the supported range");
        }
    }

    /** A pose with motion but no idle opinion, for a caller that only steers. */
    public RuntimeTransform(
            RuntimeVector position, float yaw, float pitch, double scale,
            RuntimeVector velocity, boolean dashing) {
        this(position, yaw, pitch, scale, velocity, dashing, IdleBehaviour.Pose.ACTIVE);
    }

    /** A pose with no motion, for a caller that only places a pet. */
    public RuntimeTransform(RuntimeVector position, float yaw, float pitch, double scale) {
        this(position, yaw, pitch, scale, RuntimeVector.ZERO, false, IdleBehaviour.Pose.ACTIVE);
    }

    /** Horizontal speed in metres per second, which is what facing and gait read. */
    public double horizontalSpeed() {
        return Math.sqrt(velocity.x() * velocity.x() + velocity.z() * velocity.z());
    }

    /** Whether the pet has settled, which is a different animation from merely being slow. */
    public boolean resting() {
        return idle.resting();
    }
}
