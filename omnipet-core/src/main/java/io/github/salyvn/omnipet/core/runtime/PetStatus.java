package io.github.salyvn.omnipet.core.runtime;

/**
 * What a pet is doing, as a word for its nameplate.
 *
 * <p>Distinct from {@link MovementGait}, which selects an animation clip. This is the shorter vocabulary a
 * player reads at a glance: a gait tells a renderer which loop to play, a status tells its owner whether the
 * pet is keeping up. Deriving one from the other keeps the two from disagreeing.
 *
 * <p>Pure and vendor-neutral, so both renderers reach the same word for the same pet, and so the mapping is
 * testable without a server.
 */
public enum PetStatus {
    /** Settled: gave up waiting and curled up. */
    RESTING,
    /** Standing by, on its feet. */
    IDLE,
    /** Moving at a normal pace. */
    FOLLOWING,
    /** Catching up, because its owner got ahead. */
    DASHING;

    /**
     * The status for one movement sample.
     *
     * @param runSpeed the velocity ceiling a pet is considered to be running at
     */
    public static PetStatus of(
            double horizontalSpeed, boolean dashing, double runSpeed, boolean resting) {
        return switch (MovementGait.of(horizontalSpeed, dashing, runSpeed, resting)) {
            case REST -> RESTING;
            case IDLE -> IDLE;
            case WALK -> FOLLOWING;
            case RUN -> DASHING;
        };
    }

    /** The status a transform describes, so a renderer does not have to unpack it. */
    public static PetStatus of(RuntimeTransform transform, double runSpeed) {
        if (transform == null) return IDLE;
        return of(transform.horizontalSpeed(), transform.dashing(), runSpeed, transform.resting());
    }
}
