package io.github.salyvn.omnipet.core.incubation;

/**
 * How hard a placed egg rocks as it approaches hatching.
 *
 * <p>Pure arithmetic in core so the curve is testable without a server, and so the renderer only has to
 * decide what to do with the number rather than when to produce it.
 *
 * <p>The shape matters more than the numbers. A linear ramp reads as a mechanism winding up; real
 * anticipation is almost nothing for most of the wait and then unmistakable at the end, which is why this
 * stays flat until {@link #QUIET_FRACTION} of the wait remains and then rises sharply.
 */
public final class EggShake {
    /** Above this fraction of time remaining the egg is still. Most of a wait should be uneventful. */
    public static final double QUIET_FRACTION = 0.25;

    /** Rotation at the moment of hatching, in degrees. Enough to read as rocking, not as spinning. */
    public static final double MAXIMUM_DEGREES = 14.0;

    /** Rocks back and forth this many times a second at full intensity. */
    public static final double RADIANS_PER_SECOND = 11.0;

    private EggShake() {}

    /**
     * How intense the rocking is, from 0 (still) to 1 (about to hatch).
     *
     * <p>Squared after the quiet period so the last moments accelerate rather than ramp evenly.
     *
     * @param remainingMillis time left; a non-positive value means ready
     * @param totalMillis     the full incubation, used to make the curve independent of egg duration
     */
    public static double intensity(long remainingMillis, long totalMillis) {
        if (totalMillis <= 0) return 0;
        if (remainingMillis <= 0) return 1;
        double remainingFraction = (double) remainingMillis / totalMillis;
        if (remainingFraction >= QUIET_FRACTION) return 0;
        double progressThroughFinal = (QUIET_FRACTION - remainingFraction) / QUIET_FRACTION;
        double clamped = Math.max(0, Math.min(1, progressThroughFinal));
        return clamped * clamped;
    }

    /**
     * The rocking angle in degrees for one moment, positive or negative.
     *
     * @param phaseSeconds a monotonic clock; the caller offsets it per egg so neighbours do not rock in
     *                     lockstep, the same way pets are desynchronised
     */
    public static double degrees(long remainingMillis, long totalMillis, double phaseSeconds) {
        double intensity = intensity(remainingMillis, totalMillis);
        if (intensity <= 0 || !Double.isFinite(phaseSeconds)) return 0;
        return Math.sin(phaseSeconds * RADIANS_PER_SECOND) * MAXIMUM_DEGREES * intensity;
    }

    /**
     * Whether this moment is close enough to hatching to be worth a particle burst.
     *
     * <p>Deliberately narrower than the shake: rocking is ambient, a burst is an event, and something that
     * happens through the whole final quarter is not an event.
     */
    public static boolean imminent(long remainingMillis, long totalMillis) {
        return intensity(remainingMillis, totalMillis) >= 0.5;
    }
}
