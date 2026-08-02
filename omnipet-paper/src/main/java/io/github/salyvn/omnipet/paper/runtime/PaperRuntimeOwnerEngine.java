package io.github.salyvn.omnipet.paper.runtime;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;

import io.github.salyvn.omnipet.core.runtime.ActivationRendererResolver;
import io.github.salyvn.omnipet.core.runtime.MovementController;
import io.github.salyvn.omnipet.core.runtime.PetActivationResult;
import io.github.salyvn.omnipet.core.runtime.PetActivationService;
import io.github.salyvn.omnipet.core.runtime.RendererSpawnRequest;
import io.github.salyvn.omnipet.paper.runtime.PaperRuntimeOwnerSnapshot.DesiredPet;
import io.github.salyvn.omnipet.paper.runtime.PaperRuntimeOwnerSnapshot.InvalidPet;

final class PaperRuntimeOwnerEngine {
    private final PetActivationService activation;
    private final MovementController movement;
    private final ActivationRendererResolver renderers;
    private final PaperRuntimeOwnerPoseSource poses;
    private final LongSupplier nanoTime;
    private final PaperRuntimeFailureSink failures;
    private final int maximumPetsPerOwner;
    private final Map<UUID, LinkedHashMap<UUID, PaperRuntimePetState>> states = new LinkedHashMap<>();
    private final Map<UUID, SnapshotKey> reportedInvalidSnapshots = new LinkedHashMap<>();
    private long nextRendererGeneration = 1;

    PaperRuntimeOwnerEngine(
            PetActivationService activation,
            MovementController movement,
            ActivationRendererResolver renderers,
            PaperRuntimeOwnerPoseSource poses,
            LongSupplier nanoTime,
            PaperRuntimeFailureSink failures,
            int maximumPetsPerOwner) {
        this.activation = activation;
        this.movement = movement;
        this.renderers = renderers;
        this.poses = poses;
        this.nanoTime = nanoTime;
        this.failures = failures;
        this.maximumPetsPerOwner = maximumPetsPerOwner;
    }

    boolean process(PaperRuntimeOwnerSnapshot snapshot) {
        UUID ownerId = snapshot.ownerId();
        Optional<PaperRuntimeOwnerPose> resolved;
        try {
            resolved = poses.find(ownerId);
        } catch (RuntimeException | LinkageError failure) {
            report(ownerId, null, PaperRuntimeFailure.Stage.OWNER_POSE, failure);
            return cleanup(ownerId, PaperRuntimeFailure.Stage.CLEANUP);
        }
        if (resolved.isEmpty() || !resolved.orElseThrow().visible()) {
            return cleanup(ownerId, PaperRuntimeFailure.Stage.CLEANUP);
        }
        if (snapshot.desiredPets().size() + snapshot.invalidPets().size() > maximumPetsPerOwner) {
            report(ownerId, null, PaperRuntimeFailure.Stage.SNAPSHOT,
                    "persisted desired pet count exceeds the runtime safety budget");
            return cleanup(ownerId, PaperRuntimeFailure.Stage.CLEANUP);
        }

        reportInvalid(snapshot);
        PaperRuntimeOwnerPose owner = resolved.orElseThrow();
        long nowNanos = nanoTime.getAsLong();
        LinkedHashMap<UUID, PaperRuntimePetState> ownerStates =
                states.computeIfAbsent(ownerId, ignored -> new LinkedHashMap<>());
        List<RendererSpawnRequest> requests = new ArrayList<>(snapshot.desiredPets().size());
        Set<UUID> retained = new LinkedHashSet<>();
        for (DesiredPet pet : snapshot.desiredPets()) {
            UUID petId = pet.instance().id();
            try {
                PaperRuntimePetState state = ownerStates.get(petId);
                if (state == null || !state.rendererProviderMatches(pet)) {
                    state = PaperRuntimePetState.create(
                            pet, owner, nowNanos, nextRendererGeneration(), movement);
                    ownerStates.put(petId, state);
                } else {
                    state.advance(pet, owner, nowNanos, movement);
                }
                requests.add(state.request(ownerId, pet, owner));
                retained.add(petId);
            } catch (RuntimeException failure) {
                ownerStates.remove(petId);
                report(ownerId, petId, PaperRuntimeFailure.Stage.MOVEMENT, failure);
            }
        }
        ownerStates.keySet().removeIf(petId -> !retained.contains(petId));
        if (ownerStates.isEmpty()) states.remove(ownerId);
        try {
            PetActivationResult result = activation.reconcile(ownerId, requests, renderers);
            result.failures().forEach((petId, detail) ->
                    report(ownerId, petId, PaperRuntimeFailure.Stage.RECONCILIATION, detail));
            return true;
        } catch (RuntimeException | LinkageError failure) {
            report(ownerId, null, PaperRuntimeFailure.Stage.RECONCILIATION, failure);
            return false;
        }
    }

    boolean cleanup(UUID ownerId, PaperRuntimeFailure.Stage stage) {
        states.remove(ownerId);
        reportedInvalidSnapshots.remove(ownerId);
        try {
            PetActivationResult result = activation.removeOwner(ownerId);
            result.failures().forEach((petId, detail) -> report(ownerId, petId, stage, detail));
            return result.converged();
        } catch (RuntimeException | LinkageError failure) {
            report(ownerId, null, stage, failure);
            return false;
        }
    }

    int activeCount() {
        return activation.activeCount();
    }

    private void reportInvalid(PaperRuntimeOwnerSnapshot snapshot) {
        SnapshotKey key = new SnapshotKey(snapshot.storageRevision(), snapshot.registryGeneration());
        if (key.equals(reportedInvalidSnapshots.get(snapshot.ownerId()))) return;
        for (InvalidPet invalid : snapshot.invalidPets()) {
            report(snapshot.ownerId(), invalid.petInstanceId(), PaperRuntimeFailure.Stage.SNAPSHOT, invalid.detail());
        }
        reportedInvalidSnapshots.put(snapshot.ownerId(), key);
    }

    private long nextRendererGeneration() {
        if (nextRendererGeneration == Long.MAX_VALUE) {
            throw new IllegalStateException("renderer generation space is exhausted");
        }
        return nextRendererGeneration++;
    }

    private void report(UUID ownerId, UUID petId, PaperRuntimeFailure.Stage stage, Throwable failure) {
        String message = failure.getMessage();
        report(ownerId, petId, stage,
                message == null || message.isBlank() ? failure.getClass().getSimpleName() : message);
    }

    private void report(UUID ownerId, UUID petId, PaperRuntimeFailure.Stage stage, String detail) {
        try {
            failures.accept(new PaperRuntimeFailure(ownerId, petId, stage, detail));
        } catch (RuntimeException | LinkageError ignored) {
            // An observer must never stop the central runtime task.
        }
    }

    private record SnapshotKey(long storageRevision, long registryGeneration) {}
}
