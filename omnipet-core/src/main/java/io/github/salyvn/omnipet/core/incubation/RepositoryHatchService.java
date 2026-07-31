package io.github.salyvn.omnipet.core.incubation;

import java.io.IOException;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

import io.github.salyvn.omnipet.core.domain.PlayerState;
import io.github.salyvn.omnipet.core.domain.incubation.EggDefinition;
import io.github.salyvn.omnipet.core.persistence.PlayerStateRepository;
import io.github.salyvn.omnipet.core.persistence.RegistrySnapshot;
import io.github.salyvn.omnipet.core.storage.PetStorageLimits;

public final class RepositoryHatchService {
    private final PlayerStateRepository repository;
    private final HatchService hatches;

    public RepositoryHatchService(PlayerStateRepository repository) {
        this(repository, new HatchService());
    }

    RepositoryHatchService(PlayerStateRepository repository, HatchService hatches) {
        this.repository = Objects.requireNonNull(repository, "player state repository");
        this.hatches = Objects.requireNonNull(hatches, "hatch service");
    }

    public PlayerState snapshot(UUID playerId) throws IOException {
        return repository.snapshot(requirePlayer(playerId));
    }

    public HatchResult start(
            UUID playerId,
            long expectedRevision,
            UUID incubationId,
            EggDefinition egg,
            RegistrySnapshot registry,
            long seed,
            PetStorageLimits limits) throws IOException {
        return mutate(playerId, expectedRevision,
                state -> hatches.start(state, incubationId, egg, registry, seed, limits));
    }

    public HatchResult tick(UUID playerId, long expectedRevision, UUID incubationId, long elapsedMillis)
            throws IOException {
        return mutate(playerId, expectedRevision, state -> hatches.tick(state, incubationId, elapsedMillis));
    }

    public HatchResult reduce(
            UUID playerId,
            long expectedRevision,
            UUID incubationId,
            long reductionMillis,
            UUID actionToken) throws IOException {
        return mutate(playerId, expectedRevision,
                state -> hatches.reduce(state, incubationId, reductionMillis, actionToken));
    }

    public HatchResult setRemaining(
            UUID playerId,
            long expectedRevision,
            UUID incubationId,
            long remainingMillis,
            UUID actionToken) throws IOException {
        return mutate(playerId, expectedRevision,
                state -> hatches.setRemaining(state, incubationId, remainingMillis, actionToken));
    }

    public HatchResult complete(
            UUID playerId,
            long expectedRevision,
            UUID incubationId,
            UUID actionToken) throws IOException {
        return mutate(playerId, expectedRevision, state -> hatches.complete(state, incubationId, actionToken));
    }

    public HatchResult cancel(
            UUID playerId,
            long expectedRevision,
            UUID incubationId,
            UUID actionToken) throws IOException {
        return mutate(playerId, expectedRevision, state -> hatches.cancel(state, incubationId, actionToken));
    }

    public HatchResult claim(
            UUID playerId,
            long expectedRevision,
            UUID incubationId,
            PetStorageLimits limits) throws IOException {
        return mutate(playerId, expectedRevision, state -> hatches.claim(state, incubationId, limits));
    }

    private HatchResult mutate(
            UUID playerId,
            long expectedRevision,
            Function<PlayerState, HatchResult> mutation) throws IOException {
        UUID target = requirePlayer(playerId);
        final HatchResult[] captured = new HatchResult[1];
        try {
            PlayerState saved = repository.withLocked(target, expectedRevision, current -> {
                HatchResult result = mutation.apply(current);
                captured[0] = result;
                if (!result.changedFrom(current)) throw new Unchanged(result);
                return result.state();
            });
            HatchResult result = captured[0];
            return new HatchResult(result.status(), saved, saved.incubation(), result.claimedPet());
        } catch (Unchanged unchanged) {
            return unchanged.result;
        }
    }

    private static UUID requirePlayer(UUID playerId) {
        return Objects.requireNonNull(playerId, "player id");
    }

    private static final class Unchanged extends RuntimeException {
        private final HatchResult result;

        private Unchanged(HatchResult result) {
            super(null, null, false, false);
            this.result = result;
        }
    }
}
