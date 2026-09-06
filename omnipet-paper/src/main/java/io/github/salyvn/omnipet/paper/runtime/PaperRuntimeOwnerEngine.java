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
    private final boolean idlePlayEnabled;
    private final int particleEveryTicks;
    private final PetVanityParticleSink particles;
    private final Map<UUID, LinkedHashMap<UUID, PaperRuntimePetState>> states = new LinkedHashMap<>();
    private final Map<UUID, SnapshotKey> reportedInvalidSnapshots = new LinkedHashMap<>();
    /**
     * Scratch reused across owners and ticks.
     *
     * <p>Safe because both are consumed before {@link #process} returns: the request list is read into a
     * map by the activation service, and the retained set is only used to prune this owner's states. Held
     * as fields because allocating them per owner per tick was two collections times sixty-four owners
     * times twenty ticks a second.
     */
    private final List<RendererSpawnRequest> requests = new ArrayList<>();
    private final Set<UUID> retained = new LinkedHashSet<>();
    private long nextRendererGeneration = 1;

    PaperRuntimeOwnerEngine(
            PetActivationService activation,
            MovementController movement,
            ActivationRendererResolver renderers,
            PaperRuntimeOwnerPoseSource poses,
            LongSupplier nanoTime,
            PaperRuntimeFailureSink failures,
            int maximumPetsPerOwner) {
        this(activation, movement, renderers, poses, nanoTime, failures, maximumPetsPerOwner,
                false, 0, PetVanityParticleSink.NONE);
    }

    PaperRuntimeOwnerEngine(
            PetActivationService activation,
            MovementController movement,
            ActivationRendererResolver renderers,
            PaperRuntimeOwnerPoseSource poses,
            LongSupplier nanoTime,
            PaperRuntimeFailureSink failures,
            int maximumPetsPerOwner,
            boolean idlePlayEnabled,
            int particleEveryTicks,
            PetVanityParticleSink particles) {
        this.activation = activation;
        this.movement = movement;
        this.renderers = renderers;
        this.poses = poses;
        this.nanoTime = nanoTime;
        this.failures = failures;
        this.maximumPetsPerOwner = maximumPetsPerOwner;
        this.idlePlayEnabled = idlePlayEnabled;
        this.particleEveryTicks = particleEveryTicks;
        this.particles = particles == null ? PetVanityParticleSink.NONE : particles;
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
        List<RendererSpawnRequest> requests = this.requests;
        Set<UUID> retained = this.retained;
        requests.clear();
        retained.clear();
        for (DesiredPet pet : snapshot.desiredPets()) {
            UUID petId = pet.instance().id();
            try {
                PaperRuntimePetState state = ownerStates.get(petId);
                if (state == null || !state.rendererProviderMatches(pet)) {
                    state = PaperRuntimePetState.create(
                            pet, owner, nowNanos, nextRendererGeneration(), movement);
                    ownerStates.put(petId, state);
                } else {
                    state.advance(pet, owner, nowNanos, movement, idlePlayEnabled, particleEveryTicks);
                }
                requests.add(state.request(ownerId, pet, owner));
                retained.add(petId);
                // A playing pet trails particles for the whole world to see; a following one does not. The
                // burst is best-effort and isolated so a particle backend fault cannot fail the tick.
                if (state.particleDue()) emitParticle(ownerId, petId, state.position());
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

    /** Live renderers for one owner, used to reject a stale interaction-index entry. */
    java.util.List<io.github.salyvn.omnipet.core.runtime.ActiveRendererSnapshot> activeRenderers(
            java.util.UUID ownerId) {
        return activation.activeRenderers(ownerId);
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

    private void emitParticle(UUID ownerId, UUID petId, io.github.salyvn.omnipet.core.runtime.RuntimeVector position) {
        try {
            particles.emit(ownerId, position);
        } catch (RuntimeException | LinkageError failure) {
            report(ownerId, petId, PaperRuntimeFailure.Stage.PARTICLE, failure);
        }
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
