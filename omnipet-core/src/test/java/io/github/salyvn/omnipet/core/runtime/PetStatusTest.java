package io.github.salyvn.omnipet.core.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * The word a pet's nameplate shows for what it is doing.
 *
 * <p>Derived from {@link MovementGait} rather than from speed directly, so the plate and the animation can
 * never disagree — a pet playing its run clip cannot be labelled "idle".
 */
class PetStatusTest {
    private static final double RUN_SPEED = 1.2;

    @Test
    void aStationaryPetIsIdleAndASettledOneIsResting() {
        assertEquals(PetStatus.IDLE, PetStatus.of(0, false, RUN_SPEED, false));
        assertEquals(PetStatus.RESTING, PetStatus.of(0, false, RUN_SPEED, true));
    }

    @Test
    void aWalkingPetIsFollowingAndAFastOneIsDashing() {
        assertEquals(PetStatus.FOLLOWING, PetStatus.of(0.6, false, RUN_SPEED, false));
        assertEquals(PetStatus.DASHING, PetStatus.of(RUN_SPEED, false, RUN_SPEED, false));
    }

    /** The controller raises its dash flag before top speed, and that flag wins. */
    @Test
    void theControllersOwnDashFlagOutranksSpeed() {
        assertEquals(PetStatus.DASHING, PetStatus.of(0.1, true, RUN_SPEED, false));
        assertEquals(PetStatus.DASHING, PetStatus.of(0, true, RUN_SPEED, true),
                "a dashing pet is not resting, whatever the idle state says");
    }

    /** Real movement beats a stale resting flag: the pet is already being steered by then. */
    @Test
    void movementOutranksResting() {
        assertEquals(PetStatus.FOLLOWING, PetStatus.of(0.6, false, RUN_SPEED, true));
    }

    @Test
    void aNonFiniteSpeedReadsAsStandingStillRatherThanThrowing() {
        assertEquals(PetStatus.IDLE, PetStatus.of(Double.NaN, false, RUN_SPEED, false));
        assertEquals(PetStatus.RESTING, PetStatus.of(Double.NaN, false, RUN_SPEED, true));
    }

    @Test
    void aTransformResolvesToTheSameStatusAsItsParts() {
        RuntimeTransform walking = new RuntimeTransform(
                new RuntimeVector(0, 0, 0), 0, 0, 1, new RuntimeVector(0, 0, 0.6), false);

        assertEquals(PetStatus.FOLLOWING, PetStatus.of(walking, RUN_SPEED));
    }

    /** A renderer with no transform yet must get a word rather than a null. */
    @Test
    void anAbsentTransformReadsAsIdle() {
        assertEquals(PetStatus.IDLE, PetStatus.of(null, RUN_SPEED));
    }

    /**
     * Every gait produces its own distinct status, so a new gait cannot silently share a word.
     *
     * <p>Asserted by driving real inputs through the mapping and checking the four results are four
     * different words — not by restating the switch, which would only prove the test agrees with itself.
     */
    @Test
    void everyGaitProducesItsOwnDistinctStatus() {
        java.util.Set<PetStatus> reached = new java.util.LinkedHashSet<>();
        reached.add(PetStatus.of(0, false, RUN_SPEED, true));        // REST
        reached.add(PetStatus.of(0, false, RUN_SPEED, false));       // IDLE
        reached.add(PetStatus.of(0.6, false, RUN_SPEED, false));     // WALK
        reached.add(PetStatus.of(0.1, true, RUN_SPEED, false));      // RUN

        assertEquals(MovementGait.values().length, reached.size(),
                "each gait must reach a status of its own: " + reached);
        assertEquals(PetStatus.values().length, reached.size(),
                "a status word exists that no gait reaches, or a gait was added without one");
    }
}
