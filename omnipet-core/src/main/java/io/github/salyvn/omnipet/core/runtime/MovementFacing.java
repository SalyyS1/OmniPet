package io.github.salyvn.omnipet.core.runtime;

/**
 * Which way a pet faces while it moves.
 *
 * <p>A pet used to copy its owner's yaw verbatim, so it always looked where the player looked and never
 * where it was actually going: orbiting pets slid sideways, and a pet catching up drifted backwards. The
 * steering controller already knows the velocity, so facing is derived from motion instead.
 *
 * <p>Below {@link #MINIMUM_SPEED} the heading is held rather than recomputed. A near-stationary pet has a
 * velocity whose direction is mostly noise, and following it would make the pet jitter in place. Turning
 * is rate-limited for the same reason a real creature cannot spin instantly, and because an unclamped
 * turn reads as a teleport when the target crosses behind the pet.
 */
public final class MovementFacing {
    /** Below this, in metres per second, direction is noise and the previous heading is kept. */
    public static final double MINIMUM_SPEED = 0.08;

    /** Degrees per second a pet may turn. Fast enough to feel responsive, slow enough to read as turning. */
    public static final double TURN_DEGREES_PER_SECOND = 540;

    private MovementFacing() {}

    /**
     * The yaw a pet should show this tick.
     *
     * @param currentYaw the heading it is showing now, in degrees
     * @param velocity   world-space metres per second
     * @param deltaSeconds time since the previous update; a non-positive value holds the heading
     * @return the new yaw in degrees, normalised to [-180, 180)
     */
    public static float yaw(float currentYaw, RuntimeVector velocity, double deltaSeconds) {
        double speed = Math.sqrt(velocity.x() * velocity.x() + velocity.z() * velocity.z());
        if (speed < MINIMUM_SPEED || !(deltaSeconds > 0) || !Double.isFinite(deltaSeconds)) {
            return normalize(currentYaw);
        }
        double desired = Math.toDegrees(Math.atan2(-velocity.x(), velocity.z()));
        double difference = normalize((float) (desired - currentYaw));
        double allowance = TURN_DEGREES_PER_SECOND * deltaSeconds;
        double step = Math.max(-allowance, Math.min(allowance, difference));
        return normalize((float) (currentYaw + step));
    }

    /** Wraps degrees into [-180, 180) so the shortest turn is always the signed difference. */
    public static float normalize(float degrees) {
        if (!Float.isFinite(degrees)) return 0;
        float wrapped = degrees % 360;
        if (wrapped >= 180) wrapped -= 360;
        if (wrapped < -180) wrapped += 360;
        return wrapped;
    }
}
