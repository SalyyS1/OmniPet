package io.github.salyvn.omnipet.paper.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.github.salyvn.omnipet.core.runtime.MovementFacing;

/**
 * Which way a head pet's face points.
 *
 * <p>Two defects met in the "mặt head bị ngược" report. The display had no heading of its own after spawn —
 * it is a passenger, and Minecraft carries a passenger with its vehicle without ever turning it, so
 * rotating the carrier left the head frozen at whatever direction its owner happened to face when the pet
 * appeared. On top of that, the {@code HEAD} item transform renders the skull turned away from the
 * display's heading, so even a correctly-rotated display shows the back of the skull.
 *
 * <p>Pure arithmetic, so both halves are testable without a server. The wiring — that the renderer actually
 * calls this every tick — is asserted in {@link PaperHeadRendererTest}.
 */
class HeadFacingTest {
    @Test
    void theHeadIsTurnedHalfATurnFromThePetsHeading() {
        // -180 rather than 180: the range is [-180, 180), so the half-turn from 0 lands on the closed end.
        // Same heading either way, and normalising keeps it comparable to what the entity reports.
        assertEquals(-180f, HeadFacing.displayYaw(0));
        assertEquals(-90f, HeadFacing.displayYaw(90));
        assertEquals(0f, HeadFacing.displayYaw(180));
    }

    /** Yaw stays in [-180, 180) so it is directly comparable to what the entity reports. */
    @Test
    void theResultIsAlwaysNormalised() {
        float[] headings = {-540, -180, -1, 0, 1, 179, 180, 359, 540, 1080};
        for (float heading : headings) {
            float result = HeadFacing.displayYaw(heading);
            assertTrue(result >= -180f && result < 180f, heading + " produced " + result);
            assertEquals(MovementFacing.normalize(result), result, "already-normalised input changed");
        }
    }

    /** Applying it twice returns the original heading, which is what "half a turn" has to mean. */
    @Test
    void turningTwiceComesBackToWhereItStarted() {
        float[] headings = {-179, -90, 0, 45, 90, 179};
        for (float heading : headings) {
            assertEquals(MovementFacing.normalize(heading),
                    HeadFacing.displayYaw(HeadFacing.displayYaw(heading)),
                    "half a turn twice should be a whole turn at " + heading);
        }
    }

    @Test
    void aDisplayAlreadyFacingTheRightWayNeedsNoPacket() {
        assertFalse(HeadFacing.needsTurn(HeadFacing.displayYaw(37), 37));
        assertFalse(HeadFacing.needsTurn(HeadFacing.displayYaw(-120), -120));
    }

    @Test
    void aDisplayFacingTheWrongWayNeedsATurn() {
        assertTrue(HeadFacing.needsTurn(0, 0), "0 is half a turn from where a heading of 0 wants it");
        assertTrue(HeadFacing.needsTurn(HeadFacing.displayYaw(37), 90));
    }

    /** A turn across the wrap point is a small turn, not a 359-degree one. */
    @Test
    void theWrapPointIsNotMistakenForALargeTurn() {
        assertFalse(HeadFacing.needsTurn(HeadFacing.displayYaw(-179.99f), 180f),
                "-179.99 and 180 are the same heading");
    }

    /** A display whose rotation cannot be read is turned rather than left wherever it drifted to. */
    @Test
    void aNonFiniteCurrentYawFailsTowardsWritingTheRotation() {
        assertTrue(HeadFacing.needsTurn(Float.NaN, 0));
        assertTrue(HeadFacing.needsTurn(Float.POSITIVE_INFINITY, 90));
    }
}
