package io.github.salyvn.omnipet.core.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class RuntimeFleetBudgetEvidenceTest {
    private static final int PET_COUNT = 100;
    private static final int GLOBAL_EFFECT_BUDGET = 24;
    private static final int EFFECT_ATTEMPTS_PER_PET = 2;

    @Test
    void oneCentralPassProcessesOneHundredPetsWithDeterministicBoundedWork() {
        List<PetFrame> pets = fleet();

        TickEvidence first = new CentralRuntimePass().tick(pets, 42, 1_000_000);
        TickEvidence repeated = new CentralRuntimePass().tick(pets, 42, 1_000_000);

        assertEquals(first, repeated);
        assertEquals(1, first.coordinatorPasses());
        assertEquals(PET_COUNT, first.petsVisited());
        assertEquals(PET_COUNT, first.movementSteps());
        assertEquals(PET_COUNT * EFFECT_ATTEMPTS_PER_PET, first.effectAttempts());
        assertEquals(GLOBAL_EFFECT_BUDGET, first.effectsAccepted());
        assertEquals(first.effectAttempts() - GLOBAL_EFFECT_BUDGET, first.effectsRejected());
        assertEquals(0, first.nonFiniteResults());
        assertEquals(0, first.safetySnaps());
        assertTrue(first.maximumVelocity() <= MovementProfile.defaults().maxSpeed());
        assertFalse(pets.isEmpty());
    }

    private static List<PetFrame> fleet() {
        List<PetFrame> pets = new ArrayList<>(PET_COUNT);
        MovementPattern[] patterns = MovementPattern.values();
        for (int index = 0; index < PET_COUNT; index++) {
            double ownerX = (index % 10) * 2.5;
            double ownerZ = (index / 10) * 2.5;
            RuntimeVector owner = new RuntimeVector(ownerX, 64, ownerZ);
            RuntimeVector current = owner.add(new RuntimeVector(0.25, 1.1, -1.4));
            pets.add(new PetFrame(
                    new UUID(0, index + 1L),
                    withPattern(MovementProfile.defaults(), patterns[index % patterns.length]),
                    new MovementInput(
                            owner,
                            new RuntimeVector(0, 0, 1),
                            current,
                            RuntimeVector.ZERO,
                            0.05,
                            12.5,
                            index * (Math.PI * 2 / PET_COUNT))));
        }
        return List.copyOf(pets);
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

    private record PetFrame(UUID petId, MovementProfile profile, MovementInput input) {}

    private record TickEvidence(
            int coordinatorPasses,
            int petsVisited,
            int movementSteps,
            int effectAttempts,
            int effectsAccepted,
            int effectsRejected,
            int nonFiniteResults,
            int safetySnaps,
            double maximumVelocity) {}

    /** Test-only contract probe: tick/time enter once and no pet owns a task or clock. */
    private static final class CentralRuntimePass {
        private final MovementController movement = new MovementController();
        private final EffectBudget effects = new EffectBudget(GLOBAL_EFFECT_BUDGET, 1);

        private TickEvidence tick(List<PetFrame> pets, long currentTick, long nowNanos) {
            int movementSteps = 0;
            int effectAttempts = 0;
            int effectsAccepted = 0;
            int nonFiniteResults = 0;
            int safetySnaps = 0;
            double maximumVelocity = 0;

            for (PetFrame pet : pets) {
                MovementStep step = movement.step(pet.profile(), pet.input());
                movementSteps++;
                maximumVelocity = Math.max(maximumVelocity, step.velocity().length());
                if (!Double.isFinite(step.position().length()) || !Double.isFinite(step.velocity().length())) {
                    nonFiniteResults++;
                }
                if (step.safetySnap()) safetySnaps++;

                for (int effect = 0; effect < EFFECT_ATTEMPTS_PER_PET; effect++) {
                    effectAttempts++;
                    if (effects.tryAcquire(currentTick, nowNanos, pet.petId(), "ambient-" + effect, 0)) {
                        effectsAccepted++;
                    }
                }
            }

            return new TickEvidence(
                    1,
                    pets.size(),
                    movementSteps,
                    effectAttempts,
                    effectsAccepted,
                    effectAttempts - effectsAccepted,
                    nonFiniteResults,
                    safetySnaps,
                    maximumVelocity);
        }
    }
}
