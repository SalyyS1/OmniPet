package io.github.salyvn.omnipet.paper.runtime;

import java.util.UUID;

import io.github.salyvn.omnipet.core.runtime.MovementController;
import io.github.salyvn.omnipet.core.runtime.IdleBehaviour;
import io.github.salyvn.omnipet.core.runtime.MovementFacing;
import io.github.salyvn.omnipet.core.runtime.MovementInput;
import io.github.salyvn.omnipet.core.runtime.MovementStep;
import io.github.salyvn.omnipet.core.runtime.RendererSpawnRequest;
import io.github.salyvn.omnipet.core.runtime.RuntimeTransform;
import io.github.salyvn.omnipet.core.runtime.RuntimeVector;
import io.github.salyvn.omnipet.paper.runtime.PaperRuntimeOwnerSnapshot.DesiredPet;

final class PaperRuntimePetState {
    private final long rendererGeneration;
    private final String rendererProvider;
    private final long phaseStartedNanos;
    private final double phaseOffsetRadians;
    private RuntimeVector position;
    private RuntimeVector velocity;
    private float yaw;
    private boolean dashing;
    /** This pet's own character, rolled once from its instance ID rather than stored. */
    private final IdleBehaviour.Temperament temperament;
    /** Seeds this pet's flourish choices and their spacing; the same ID the temperament came from. */
    private final UUID petInstanceId;
    private RuntimeVector lastOwnerPosition;
    private double ownerStillSeconds;
    private IdleBehaviour.State idleState = IdleBehaviour.State.ACTIVE;
    private long flourishSequence;
    /** Seconds until the next flourish starts, or -1 once one is running. */
    private double untilFlourishSeconds;
    private double flourishRemainingSeconds;
    private IdleBehaviour.OneShot flourish;
    private long lastUpdateNanos;

    private PaperRuntimePetState(
            long rendererGeneration,
            String rendererProvider,
            long phaseStartedNanos,
            double phaseOffsetRadians,
            RuntimeVector position,
            RuntimeVector velocity,
            float yaw,
            IdleBehaviour.Temperament temperament,
            UUID petInstanceId) {
        this.rendererGeneration = rendererGeneration;
        this.rendererProvider = rendererProvider;
        this.phaseStartedNanos = phaseStartedNanos;
        this.phaseOffsetRadians = phaseOffsetRadians;
        this.position = position;
        this.velocity = velocity;
        this.yaw = yaw;
        this.temperament = temperament;
        this.petInstanceId = petInstanceId;
        this.untilFlourishSeconds =
                IdleBehaviour.oneShotDelaySeconds(petInstanceId, 0, temperament);
        this.lastUpdateNanos = phaseStartedNanos;
    }

    static PaperRuntimePetState create(
            DesiredPet pet,
            PaperRuntimeOwnerPose owner,
            long nowNanos,
            long rendererGeneration,
            MovementController movement) {
        RuntimeVector distant = owner.position().add(new RuntimeVector(
                0, pet.movement().safetySnapDistance() + 1, 0));
        MovementStep initial = movement.step(pet.movement(), new MovementInput(
                owner.position(), owner.forward(), distant, RuntimeVector.ZERO, 0, 0,
                phaseOffset(pet.instance().id())));
        return new PaperRuntimePetState(
                rendererGeneration,
                pet.appearance().provider(),
                nowNanos,
                phaseOffset(pet.instance().id()),
                initial.position(),
                initial.velocity(),
                // Spawns facing the same way as its owner; motion takes over from the first tick.
                MovementFacing.normalize(owner.yaw()),
                IdleBehaviour.temperament(pet.instance().id()),
                pet.instance().id());
    }

    void advance(DesiredPet pet, PaperRuntimeOwnerPose owner, long nowNanos, MovementController movement) {
        double deltaSeconds = elapsedSeconds(nowNanos, lastUpdateNanos);
        double phaseSeconds = elapsedSeconds(nowNanos, phaseStartedNanos);
        MovementStep step = movement.step(pet.movement(), new MovementInput(
                owner.position(), owner.forward(), position, velocity,
                deltaSeconds, phaseSeconds, phaseOffsetRadians));
        position = step.position();
        velocity = step.velocity();
        dashing = step.dashing();
        // A snap relocates the pet without it having travelled, so the old heading means nothing there.
        yaw = step.safetySnap()
                ? MovementFacing.normalize(owner.yaw())
                : MovementFacing.yaw(yaw, velocity, deltaSeconds);
        advanceIdle(owner, deltaSeconds);
        // An idle pet has no motion to derive facing from, so it would keep whatever heading it stopped
        // on and stare off past its owner. Only once settling has begun, and never during a snap.
        if (!step.safetySnap() && idleState != IdleBehaviour.State.ACTIVE) {
            yaw = IdleBehaviour.faceOwner(yaw, owner.position().subtract(position), deltaSeconds);
        }
        lastUpdateNanos = nowNanos;
    }

    /**
     * Tracks how long the owner has stood still, and what the pet is doing about it.
     *
     * <p>Owner speed is derived here rather than read from the pose, because the pose is a snapshot with
     * no history. Two consecutive positions are all this needs.
     */
    private void advanceIdle(PaperRuntimeOwnerPose owner, double deltaSeconds) {
        RuntimeVector previous = lastOwnerPosition;
        lastOwnerPosition = owner.position();
        if (previous == null || !(deltaSeconds > 0)) return;
        double ownerSpeed = owner.position().subtract(previous).length() / deltaSeconds;
        ownerStillSeconds = ownerSpeed < IdleBehaviour.OWNER_STILL_SPEED
                ? ownerStillSeconds + deltaSeconds
                : 0;
        idleState = IdleBehaviour.state(ownerSpeed, ownerStillSeconds, temperament.settleSeconds());
        advanceFlourish(deltaSeconds);
    }

    /**
     * Runs the flourish timer: counts down to the next one, then runs it for its short duration.
     *
     * <p>Cancelled outright the moment the pet is steered again, because a flourish that survived the
     * owner walking off would play while the pet ran after them.
     */
    private void advanceFlourish(double deltaSeconds) {
        if (idleState == IdleBehaviour.State.ACTIVE) {
            flourish = null;
            flourishRemainingSeconds = 0;
            // The countdown restarts rather than resuming, so a pet that stops briefly and often does not
            // bank progress and fire the instant it settles.
            untilFlourishSeconds =
                    IdleBehaviour.oneShotDelaySeconds(petInstanceId, flourishSequence, temperament);
            return;
        }
        if (flourish != null) {
            flourishRemainingSeconds -= deltaSeconds;
            if (flourishRemainingSeconds > 0) return;
            flourish = null;
            flourishSequence++;
            untilFlourishSeconds =
                    IdleBehaviour.oneShotDelaySeconds(petInstanceId, flourishSequence, temperament);
            return;
        }
        untilFlourishSeconds -= deltaSeconds;
        if (untilFlourishSeconds > 0) return;
        flourish = IdleBehaviour.oneShot(petInstanceId, flourishSequence, temperament);
        flourishRemainingSeconds = IdleBehaviour.ONE_SHOT_SECONDS;
    }

    /** What this pet is doing while nobody is steering it. */
    IdleBehaviour.Pose idlePose() {
        return new IdleBehaviour.Pose(idleState, flourish);
    }

    RendererSpawnRequest request(UUID ownerId, DesiredPet pet, PaperRuntimeOwnerPose owner) {
        return new RendererSpawnRequest(
                ownerId,
                pet.instance().id(),
                rendererGeneration,
                pet.definitionId(),
                pet.appearance(),
                // Pitch stays level: a pet that pitched with its owner's look would tip over when the
                // player glanced at the sky, and nothing about following needs it.
                new RuntimeTransform(position, yaw, 0, pet.scale(), velocity, dashing, idlePose()));
    }

    boolean rendererProviderMatches(DesiredPet pet) {
        return rendererProvider.equals(pet.appearance().provider());
    }

    private static double elapsedSeconds(long now, long previous) {
        if (now <= previous) return 0;
        long elapsed = now - previous;
        if (elapsed < 0) return 0;
        return elapsed / 1_000_000_000.0;
    }

    private static double phaseOffset(UUID petId) {
        long bits = petId.getMostSignificantBits() ^ Long.rotateLeft(petId.getLeastSignificantBits(), 17);
        double fraction = (bits >>> 11) * 0x1.0p-53;
        return fraction * Math.PI * 2;
    }
}
