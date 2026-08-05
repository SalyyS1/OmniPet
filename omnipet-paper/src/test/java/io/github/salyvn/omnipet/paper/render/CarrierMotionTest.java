package io.github.salyvn.omnipet.paper.render;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.github.salyvn.omnipet.core.runtime.RuntimeTransform;
import io.github.salyvn.omnipet.core.runtime.RuntimeVector;

/**
 * A pet that has moved must be written, and a pet that has not must not.
 *
 * <p>The bug this guards against shipped: both renderers pushed the carrier with {@code setVelocity},
 * which a gravity-disabled marker armor stand has no physics to act on, and then decided whether to write
 * at all by asking whether that same velocity had settled. For an entity that never moves under velocity
 * the answer was always yes, so the write was skipped for a stuck pet exactly as readily as for a
 * stationary one, and the only movement anyone saw was the safety teleport firing once the owner walked far
 * enough away.
 *
 * <p>What made it survive was the shape of the tests. The contract test asserted that {@code setVelocity}
 * was present, so it pinned the mechanism rather than the outcome; and the renderer's fake backend only
 * counted how many times {@code smoothMove} was called, which was the wrong question — the call was always
 * made, it simply did nothing. These tests ask about the decision instead.
 */
class CarrierMotionTest {
    private static RuntimeTransform at(double x, double y, double z, float yaw) {
        return new RuntimeTransform(new RuntimeVector(x, y, z), yaw, 0, 1);
    }

    @Test
    void aPetFollowingItsOwnerIsAlwaysWritten() {
        // The case the shipped bug broke. A pet trailing its owner sits whole blocks from where the
        // steering controller wants it, every tick, and every one of those ticks has to reach the world.
        RuntimeTransform target = at(10, 64, 10, 0);

        assertTrue(CarrierMotion.needsMove(7, 64, 10, 0, 0, target), "a pet three blocks behind must move");
        assertTrue(CarrierMotion.needsMove(9.5, 64, 10, 0, 0, target), "half a block behind still moves");
        assertTrue(CarrierMotion.needsMove(10, 64, 10.2, 0, 0, target), "a fifth of a block still moves");
    }

    @Test
    void aStationaryPetIsNotWrittenAtAll() {
        // The saving the skip existed for: standing still is the common case, and a teleport per tick per
        // pet to every nearby player is real network cost for no visible change.
        RuntimeTransform target = at(10, 64, 10, 90);

        assertFalse(CarrierMotion.needsMove(10, 64, 10, 90, 0, target), "an arrived, aligned pet is skipped");
        // Rounding, not movement: a hair inside the threshold is still arrived.
        assertFalse(CarrierMotion.needsMove(10.01, 64, 10.01, 90.05f, 0.05f, target),
                "sub-threshold drift is not movement");
    }

    @Test
    void turningInPlaceCountsAsMovement() {
        // Both halves of the condition matter independently. An idle pet turning to face its owner does
        // not change position, and a pet sliding sideways does not change facing.
        RuntimeTransform target = at(10, 64, 10, 180);

        assertTrue(CarrierMotion.needsMove(10, 64, 10, 0, 0, target),
                "a pet at the right place facing the wrong way must still be written");
        assertTrue(CarrierMotion.needsMove(10, 64, 10, 180, 30, target),
                "pitch is part of facing too");
    }

    @Test
    void theArrivalThresholdIsSmallerThanAnyVisibleMove() {
        // If this grew, a genuinely following pet would start being skipped and the original bug would be
        // back in a subtler form. A twentieth of a block is well inside one pixel at any normal distance.
        assertTrue(CarrierMotion.ARRIVED_DISTANCE_SQUARED <= 0.01,
                "the arrival threshold must stay far below a visible distance");
        assertTrue(CarrierMotion.ARRIVED_DISTANCE_SQUARED > 0,
                "a zero threshold would write every tick forever on floating-point noise");
    }

    @Test
    void anUnusablePositionIsWrittenRatherThanSkipped() {
        // Failing towards writing: a position we cannot reason about is worse than a position we can, and
        // skipping would leave the carrier wherever it drifted to with nothing to correct it.
        RuntimeTransform target = at(10, 64, 10, 0);

        assertTrue(CarrierMotion.needsMove(Double.NaN, 64, 10, 0, 0, target));
        assertTrue(CarrierMotion.needsMove(Double.POSITIVE_INFINITY, 64, 10, 0, 0, target));
        assertTrue(CarrierMotion.needsMove(10, 64, 10, Float.NaN, 0, target));
    }
}
