package io.github.salyvn.omnipet.core.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MovementControllerTest {
    private final MovementController movement = new MovementController();

    @Test
    void normalFollowUsesBoundedSpringMotionWithoutSnapping() {
        MovementProfile profile = MovementProfile.defaults();
        MovementStep step = movement.step(profile, input(new RuntimeVector(0, 1, 0), 0.05, 0));

        assertFalse(step.safetySnap());
        assertFalse(step.dashing());
        assertTrue(step.velocity().length() <= profile.maxSpeed());
        assertTrue(step.position().length() > 0);
    }

    @Test
    void lagSpikeUsesTheConfiguredDeltaClamp() {
        MovementProfile profile = MovementProfile.defaults();
        MovementInput clamped = input(new RuntimeVector(0, 1, 0), profile.maxDeltaSeconds(), 1);
        MovementInput spike = input(new RuntimeVector(0, 1, 0), 10, 1);

        assertEquals(movement.step(profile, clamped), movement.step(profile, spike));
    }

    @Test
    void distantPetUsesAZeroVelocitySafetySnap() {
        MovementProfile profile = MovementProfile.defaults();
        MovementStep step = movement.step(profile, input(new RuntimeVector(100, 1, 100), 0.05, 0));

        assertTrue(step.safetySnap());
        assertFalse(step.dashing());
        assertEquals(step.target(), step.position());
        assertEquals(RuntimeVector.ZERO, step.velocity());
    }

    @Test
    void visualPatternsAreDeterministicAndFinite() {
        for (MovementPattern pattern : MovementPattern.values()) {
            MovementProfile profile = withPattern(MovementProfile.defaults(), pattern);
            MovementStep first = movement.step(profile, input(new RuntimeVector(0, 1, 0), 0.05, 3.5));
            MovementStep second = movement.step(profile, input(new RuntimeVector(0, 1, 0), 0.05, 3.5));

            assertEquals(first, second);
            assertTrue(Double.isFinite(first.position().length()));
            assertTrue(Double.isFinite(first.target().length()));
        }
    }

    @Test
    void invalidVectorsAndProfilesFailBeforeRuntimeMutation() {
        assertThrows(IllegalArgumentException.class, () -> new RuntimeVector(Double.NaN, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new MovementInput(
                RuntimeVector.ZERO, RuntimeVector.ZERO, RuntimeVector.ZERO, RuntimeVector.ZERO,
                Double.POSITIVE_INFINITY, 0, 0));
        MovementProfile defaults = MovementProfile.defaults();
        assertThrows(IllegalArgumentException.class, () -> new MovementProfile(
                defaults.pattern(), defaults.followDistance(), defaults.sideOffset(), defaults.heightOffset(),
                defaults.orbitRadius(), defaults.orbitRadiansPerSecond(), defaults.bobAmplitude(),
                defaults.bobRadiansPerSecond(), defaults.springStrength(), defaults.damping(),
                defaults.maxAcceleration(), defaults.maxSpeed(), defaults.safetySnapDistance(),
                defaults.dashSpeedMultiplier(), defaults.safetySnapDistance(), defaults.maxDeltaSeconds()));
    }

    @Test
    void stepTowardAnArbitraryTargetUsesTheSameBoundedMotion() {
        MovementProfile profile = MovementProfile.defaults();
        MovementInput in = input(new RuntimeVector(0, 1, 0), 0.05, 0);
        RuntimeVector target = new RuntimeVector(1.5, 2, 0.5);

        MovementStep step = movement.stepToward(profile, in, target);

        assertFalse(step.safetySnap());
        assertEquals(target, step.target());
        assertTrue(step.velocity().length() <= profile.maxSpeed());
        assertTrue(Double.isFinite(step.position().length()));
    }

    @Test
    void stepIsStepTowardItsOwnPatternTarget() {
        // step() must stay exactly stepToward(baseTarget), or idle play would move on different physics
        // from following.
        MovementProfile profile = MovementProfile.defaults();
        MovementInput in = input(new RuntimeVector(0, 1, 0), 0.05, 1.3);

        MovementStep viaStep = movement.step(profile, in);
        MovementStep viaToward = movement.stepToward(profile, in, viaStep.target());

        assertEquals(viaStep, viaToward);
    }

    @Test
    void aDistantTargetSnapsWithoutVelocityWhicheverEntryPoint() {
        MovementProfile profile = MovementProfile.defaults();
        MovementInput in = input(RuntimeVector.ZERO, 0.05, 0);
        RuntimeVector distant = new RuntimeVector(100, 100, 100);

        MovementStep step = movement.stepToward(profile, in, distant);

        assertTrue(step.safetySnap());
        assertEquals(distant, step.position());
        assertEquals(RuntimeVector.ZERO, step.velocity());
    }

    private static MovementInput input(RuntimeVector current, double delta, double phase) {
        return new MovementInput(
                RuntimeVector.ZERO,
                new RuntimeVector(0, 0, 1),
                current,
                RuntimeVector.ZERO,
                delta,
                phase,
                0.37);
    }

    private static MovementProfile withPattern(MovementProfile profile, MovementPattern pattern) {
        return new MovementProfile(
                pattern,
                profile.followDistance(),
                profile.sideOffset(),
                profile.heightOffset(),
                profile.orbitRadius(),
                profile.orbitRadiansPerSecond(),
                profile.bobAmplitude(),
                profile.bobRadiansPerSecond(),
                profile.springStrength(),
                profile.damping(),
                profile.maxAcceleration(),
                profile.maxSpeed(),
                profile.dashDistance(),
                profile.dashSpeedMultiplier(),
                profile.safetySnapDistance(),
                profile.maxDeltaSeconds());
    }
}
