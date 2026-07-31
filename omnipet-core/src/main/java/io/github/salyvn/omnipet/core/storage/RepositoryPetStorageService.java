package io.github.salyvn.omnipet.core.storage;

import java.io.IOException;
import java.util.UUID;
import java.util.function.Function;

import io.github.salyvn.omnipet.core.domain.PetInstance;
import io.github.salyvn.omnipet.core.domain.PlayerState;
import io.github.salyvn.omnipet.core.persistence.PlayerStateRepository;

/** Persists every storage mutation through the repository UUID lock and optimistic revision. */
public final class RepositoryPetStorageService {
    private final PlayerStateRepository repository;
    private final PetStorageService storage;

    public RepositoryPetStorageService(PlayerStateRepository repository) {
        this(repository, new PetStorageService());
    }

    RepositoryPetStorageService(PlayerStateRepository repository, PetStorageService storage) {
        if (repository == null) throw new IllegalArgumentException("player state repository is required");
        if (storage == null) throw new IllegalArgumentException("pet storage service is required");
        this.repository = repository;
        this.storage = storage;
    }

    public PetStorageSnapshot snapshot(UUID playerId, PetStorageLimits limits) throws IOException {
        return storage.snapshot(repository.snapshot(playerId), limits);
    }

    public PetStorageResult admit(
            UUID playerId,
            long expectedRevision,
            PetInstance pet,
            PetStorageLimits limits) throws IOException {
        return mutate(playerId, expectedRevision, limits, state -> storage.admit(state, pet, limits));
    }

    public PetStorageResult activate(
            UUID playerId,
            long expectedRevision,
            UUID petId,
            PetStorageLimits limits) throws IOException {
        return mutate(playerId, expectedRevision, limits, state -> storage.activate(state, petId, limits));
    }

    public PetStorageResult deactivate(
            UUID playerId,
            long expectedRevision,
            UUID petId,
            PetStorageLimits limits) throws IOException {
        return mutate(playerId, expectedRevision, limits, state -> storage.deactivate(state, petId, limits));
    }

    public PetStorageResult remove(
            UUID playerId,
            long expectedRevision,
            UUID petId,
            PetStorageLimits limits) throws IOException {
        return mutate(playerId, expectedRevision, limits, state -> storage.remove(state, petId, limits));
    }

    public PetStorageResult reconcileLimits(
            UUID playerId,
            long expectedRevision,
            PetStorageLimits limits) throws IOException {
        return mutate(playerId, expectedRevision, limits, state -> storage.reconcileLimits(state, limits));
    }

    private PetStorageResult mutate(
            UUID playerId,
            long expectedRevision,
            PetStorageLimits limits,
            Function<PlayerState, PetStorageResult> mutation) throws IOException {
        if (playerId == null) throw new IllegalArgumentException("player id is required");
        if (limits == null) throw new IllegalArgumentException("storage limits are required");
        try {
            final PetStorageResult[] captured = new PetStorageResult[1];
            PlayerState saved = repository.withLocked(playerId, expectedRevision, current -> {
                PetStorageResult result = mutation.apply(current);
                captured[0] = result;
                if (!result.succeeded() || result.state().equals(current)) throw new StorageRejected(result);
                return result.state();
            });
            PetStorageResult result = captured[0];
            return new PetStorageResult(
                    result.status(),
                    saved,
                    PetStorageSnapshot.from(saved, limits),
                    result.recalledPetIds(),
                    result.removedPet());
        } catch (StorageRejected rejected) {
            return rejected.result;
        }
    }

    private static final class StorageRejected extends RuntimeException {
        private final PetStorageResult result;

        private StorageRejected(PetStorageResult result) {
            super(null, null, false, false);
            this.result = result;
        }
    }
}
