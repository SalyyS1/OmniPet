package io.github.salyvn.omnipet.core.runtime;

/**
 * What a pet is visibly doing, derived from how it is moving.
 *
 * <p>Kept vendor-neutral and separate from any clip name so the mapping is testable without a server and
 * so a renderer that cannot animate simply ignores it. The renderer decides which clip a gait plays.
 */
public enum MovementGait {
    /** Standing still, or drifting slowly enough that walking would look like sliding. */
    IDLE,
    /**
     * Settled: the owner has been still long enough that the pet gave up waiting and curled up.
     *
     * <p>Distinct from {@link #IDLE} because they say different things. Idle is a pet standing there
     * ready to go; resting is a pet that has decided nothing is happening, which is the moment a
     * companion stops reading as a prop.
     */
    REST,
    WALK,
    /** Catching up. The steering controller raises its own dash flag well before top speed. */
    RUN;

    /** Below this horizontal speed, in metres per second, a pet reads as standing still. */
    public static final double IDLE_SPEED = 0.35;

    /**
     * The gait for one movement sample.
     *
     * <p>A dash is always {@link #RUN}: it is the controller's own catch-up state, so honouring it keeps
     * the animation and the steering telling the same story even when speed alone is ambiguous.
     */
    public static MovementGait of(double horizontalSpeed, boolean dashing, double runSpeed) {
        return of(horizontalSpeed, dashing, runSpeed, false);
    }

    /**
     * The gait for one movement sample, honouring a pet that has settled.
     *
     * <p>Resting loses to any real movement: a settled pet the owner walks away from is walking, not
     * resting, and the steering controller has already started moving it by the time this is asked.
     */
    public static MovementGait of(
            double horizontalSpeed, boolean dashing, double runSpeed, boolean resting) {
        if (dashing) return RUN;
        if (!Double.isFinite(horizontalSpeed) || horizontalSpeed < IDLE_SPEED) {
            return resting ? REST : IDLE;
        }
        return Double.isFinite(runSpeed) && runSpeed > 0 && horizontalSpeed >= runSpeed ? RUN : WALK;
    }
}
