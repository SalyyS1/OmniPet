package io.github.salyvn.omnipet.paper.render;

import io.github.salyvn.omnipet.core.runtime.RuntimeTransform;

/**
 * Whether a carrier needs to be moved this tick, and how close counts as arrived.
 *
 * <p>Its own class because both renderers need exactly this decision and got it wrong in exactly the same
 * way. Each moved its carrier with {@code setVelocity}, which does nothing useful: the carrier is a marker
 * armor stand with gravity disabled, so it has no movement physics to integrate a velocity into. The pet
 * stood still and the only motion a player ever saw was the safety teleport firing once the gap grew past
 * {@code safetyDistance} — "the pet doesn't follow me, it just jumps to me when I get far away".
 *
 * <p>The other half of that bug was the skip condition. It asked whether the carrier's <em>velocity</em>
 * had settled, and for an entity that never moves under velocity that was permanently true, so the write
 * was suppressed for a stuck pet exactly as readily as for a stationary one. Arrival is a question about
 * position, and this class answers it that way.
 *
 * <p>Pure and Bukkit-free so it can be tested without a server, which is the point: the previous contract
 * test asserted the presence of {@code setVelocity} and so pinned the defect in place rather than the
 * behaviour.
 */
final class CarrierMotion {
    /**
     * How close the carrier counts as arrived, squared, in blocks.
     *
     * <p>A twentieth of a block. Below this the move is invisible and the write is pure network cost,
     * while a pet genuinely following its owner is always further out than this.
     */
    static final double ARRIVED_DISTANCE_SQUARED = 0.0025;

    /** Degrees of yaw or pitch difference below which the carrier already faces the right way. */
    static final float SAME_FACING_DEGREES = 0.1f;

    private CarrierMotion() {}

    /**
     * Whether the carrier has to be written to this tick.
     *
     * <p>False only when it is already where it should be <em>and</em> facing where it should face. Both
     * halves matter: a pet turning in place still needs the rotation, and a pet sliding without turning
     * still needs the position.
     *
     * @param currentX current carrier position
     * @param currentYaw   current carrier yaw in degrees
     * @param currentPitch current carrier pitch in degrees
     * @param transform    where the steering controller says the pet should be
     */
    static boolean needsMove(
            double currentX,
            double currentY,
            double currentZ,
            float currentYaw,
            float currentPitch,
            RuntimeTransform transform) {
        return !arrived(currentX, currentY, currentZ, transform)
                || !sameFacing(currentYaw, currentPitch, transform);
    }

    /** Whether the carrier is close enough to its target that moving it would not be visible. */
    static boolean arrived(double currentX, double currentY, double currentZ, RuntimeTransform transform) {
        double x = currentX - transform.position().x();
        double y = currentY - transform.position().y();
        double z = currentZ - transform.position().z();
        double squared = x * x + y * y + z * z;
        // A non-finite position cannot be reasoned about, so treat it as needing the move: writing a
        // position we do have is safer than skipping and leaving the carrier wherever it drifted to.
        return Double.isFinite(squared) && squared <= ARRIVED_DISTANCE_SQUARED;
    }

    /** Whether the carrier already faces where the transform wants it, within rounding. */
    static boolean sameFacing(float currentYaw, float currentPitch, RuntimeTransform transform) {
        if (!Float.isFinite(currentYaw) || !Float.isFinite(currentPitch)) return false;
        return Math.abs(currentYaw - transform.yaw()) < SAME_FACING_DEGREES
                && Math.abs(currentPitch - transform.pitch()) < SAME_FACING_DEGREES;
    }
}
