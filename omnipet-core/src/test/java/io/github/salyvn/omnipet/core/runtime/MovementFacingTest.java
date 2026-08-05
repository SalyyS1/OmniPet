package io.github.salyvn.omnipet.core.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Which way a pet faces while it moves.
 *
 * <p>A pet used to copy its owner's yaw, so it never looked where it was going. Facing now comes from
 * velocity, which introduces two failure modes worth pinning: a stationary pet whose velocity direction
 * is noise, and a target that crosses behind the pet, where an unclamped turn reads as a teleport.
 */
class MovementFacingTest {
    private static final double TICK = 1.0 / 20;

    @Test
    void aStationaryPetKeepsItsHeadingRatherThanChasingNoise() {
        // Hovering in place still leaves a tiny residual velocity from the spring. Following it would
        // make the pet spin on the spot.
        RuntimeVector jitter = new RuntimeVector(0.001, 0, -0.002);

        assertEquals(90f, MovementFacing.yaw(90f, jitter, TICK));
        assertEquals(90f, MovementFacing.yaw(90f, RuntimeVector.ZERO, TICK));
    }

    @Test
    void aPetFacesTheWayItIsTravelling() {
        // Given enough time to complete the turn, the heading settles on the direction of travel.
        float facing = 0;
        for (int tick = 0; tick < 40; tick++) {
            facing = MovementFacing.yaw(facing, new RuntimeVector(0, 0, 4), TICK);
        }

        // Minecraft yaw: 0 is +Z, and -X is 90.
        assertEquals(0f, facing, 0.5f);

        float west = 0;
        for (int tick = 0; tick < 40; tick++) {
            west = MovementFacing.yaw(west, new RuntimeVector(-4, 0, 0), TICK);
        }
        assertEquals(90f, west, 0.5f);
    }

    @Test
    void aReversalTurnsThroughTheShortestArcInsteadOfSnapping() {
        // The case that reads as a teleport if the turn is unclamped: the target crosses behind the pet,
        // so the desired heading jumps by 180 degrees in one tick.
        float facing = 0;
        float afterOneTick = MovementFacing.yaw(facing, new RuntimeVector(0, 0, -4), TICK);

        double turned = Math.abs(MovementFacing.normalize(afterOneTick - facing));
        assertTrue(turned <= MovementFacing.TURN_DEGREES_PER_SECOND * TICK + 1.0e-6,
                "one tick turned " + turned + " degrees");
        assertTrue(turned > 0, "a reversal must still begin turning");
    }

    @Test
    void verticalMotionAloneDoesNotSteer() {
        // A hovering or hopping pet moves on Y only. There is no heading in that, and reading one would
        // make a bobbing pet rotate.
        assertEquals(45f, MovementFacing.yaw(45f, new RuntimeVector(0, 3, 0), TICK));
    }

    @Test
    void headingsStayWithinASingleTurnSoDifferencesRemainShortest() {
        assertEquals(0f, MovementFacing.normalize(360f));
        assertEquals(-90f, MovementFacing.normalize(270f));
        assertEquals(-179f, MovementFacing.normalize(181f));
        assertEquals(0f, MovementFacing.normalize(Float.NaN), "a non-finite heading cannot be steered from");

        float facing = MovementFacing.yaw(179f, new RuntimeVector(-4, 0, -4), TICK);
        assertTrue(facing >= -180f && facing < 180f, "facing left the single-turn range: " + facing);
    }

    @Test
    void aStalledTickHoldsTheHeadingRatherThanDividingByZero() {
        assertEquals(30f, MovementFacing.yaw(30f, new RuntimeVector(0, 0, 5), 0));
        assertEquals(30f, MovementFacing.yaw(30f, new RuntimeVector(0, 0, 5), -1));
    }
}
