package io.github.salyvn.omnipet.core.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Turning movement into a visible gait.
 *
 * <p>Speed alone is not enough. A pet is given a dash state by the steering controller when it falls
 * behind, and that begins well before it reaches top speed, so a purely speed-based mapping would show a
 * walk while the pet was visibly sprinting to catch up.
 */
class MovementGaitTest {
    private static final double RUN_SPEED = 1.2;

    @Test
    void aStillPetIsIdleRatherThanWalkingOnTheSpot() {
        // Hovering leaves a small residual velocity from the spring; treating that as a walk would make a
        // stationary pet permanently shuffle.
        assertEquals(MovementGait.IDLE, MovementGait.of(0, false, RUN_SPEED));
        assertEquals(MovementGait.IDLE, MovementGait.of(0.2, false, RUN_SPEED));
    }

    @Test
    void ordinaryFollowingWalks() {
        assertEquals(MovementGait.WALK, MovementGait.of(0.6, false, RUN_SPEED));
    }

    @Test
    void aDashRunsEvenBeforeTopSpeed() {
        // The case a speed-only mapping gets wrong: the controller has decided to catch up, so the pet
        // must look like it is catching up.
        assertEquals(MovementGait.RUN, MovementGait.of(0.6, true, RUN_SPEED));
    }

    @Test
    void reachingTheRunSpeedRunsWithoutADash() {
        assertEquals(MovementGait.RUN, MovementGait.of(RUN_SPEED, false, RUN_SPEED));
        assertEquals(MovementGait.RUN, MovementGait.of(RUN_SPEED + 2, false, RUN_SPEED));
    }

    @Test
    void anUnusableSpeedDoesNotProduceAnUnusableGait() {
        assertEquals(MovementGait.IDLE, MovementGait.of(Double.NaN, false, RUN_SPEED));
        // A renderer with no meaningful run threshold still distinguishes standing from moving.
        assertEquals(MovementGait.WALK, MovementGait.of(5, false, 0));
    }
}
