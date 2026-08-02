package io.github.salyvn.omnipet.paper.incubation.action;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

import io.github.salyvn.omnipet.core.domain.PlayerState;
import io.github.salyvn.omnipet.core.incubation.HatchResult;
import io.github.salyvn.omnipet.core.incubation.RepositoryHatchService;

public final class RepositoryIncubationItemActionHatchPort implements IncubationItemActionHatchPort {
    private final RepositoryHatchService hatches;

    public RepositoryIncubationItemActionHatchPort(RepositoryHatchService hatches) {
        this.hatches = java.util.Objects.requireNonNull(hatches, "repository hatch service");
    }

    @Override public Snapshot snapshot(UUID playerId) throws IOException { return snapshotOf(hatches.snapshot(playerId)); }

    @Override
    public Mutation reduce(UUID playerId, long revision, UUID incubationId, long millis, UUID token) throws IOException {
        HatchResult result = hatches.reduce(playerId, revision, incubationId, millis, token);
        return new Mutation(result.succeeded(), result.status().name());
    }

    @Override
    public Mutation complete(UUID playerId, long revision, UUID incubationId, UUID token) throws IOException {
        HatchResult result = hatches.complete(playerId, revision, incubationId, token);
        return new Mutation(result.succeeded(), result.status().name());
    }

    private static Snapshot snapshotOf(PlayerState state) {
        if (state.incubation() == null) return new Snapshot(state.playerId(), state.revision(), null, Set.of());
        return new Snapshot(state.playerId(), state.revision(), state.incubation().id(),
                Set.copyOf(state.incubation().appliedActionTokens()));
    }
}
