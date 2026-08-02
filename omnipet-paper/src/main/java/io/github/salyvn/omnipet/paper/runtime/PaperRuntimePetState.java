package io.github.salyvn.omnipet.paper.runtime;

import java.util.UUID;

import io.github.salyvn.omnipet.core.runtime.MovementController;
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
    private long lastUpdateNanos;

    private PaperRuntimePetState(
            long rendererGeneration,
            String rendererProvider,
            long phaseStartedNanos,
            double phaseOffsetRadians,
            RuntimeVector position,
            RuntimeVector velocity) {
        this.rendererGeneration = rendererGeneration;
        this.rendererProvider = rendererProvider;
        this.phaseStartedNanos = phaseStartedNanos;
        this.phaseOffsetRadians = phaseOffsetRadians;
        this.position = position;
        this.velocity = velocity;
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
                initial.velocity());
    }

    void advance(DesiredPet pet, PaperRuntimeOwnerPose owner, long nowNanos, MovementController movement) {
        double deltaSeconds = elapsedSeconds(nowNanos, lastUpdateNanos);
        double phaseSeconds = elapsedSeconds(nowNanos, phaseStartedNanos);
        MovementStep step = movement.step(pet.movement(), new MovementInput(
                owner.position(), owner.forward(), position, velocity,
                deltaSeconds, phaseSeconds, phaseOffsetRadians));
        position = step.position();
        velocity = step.velocity();
        lastUpdateNanos = nowNanos;
    }

    RendererSpawnRequest request(UUID ownerId, DesiredPet pet, PaperRuntimeOwnerPose owner) {
        return new RendererSpawnRequest(
                ownerId,
                pet.instance().id(),
                rendererGeneration,
                pet.definitionId(),
                pet.appearance(),
                new RuntimeTransform(position, owner.yaw(), owner.pitch(), pet.scale()));
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
