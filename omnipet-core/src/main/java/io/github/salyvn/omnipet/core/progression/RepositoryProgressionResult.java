package io.github.salyvn.omnipet.core.progression;

import io.github.salyvn.omnipet.core.domain.PetInstance;
import io.github.salyvn.omnipet.core.domain.PlayerState;

public record RepositoryProgressionResult(
        Status status,
        PlayerState playerState,
        PetInstance pet,
        ProgressionResult progression) {
    public RepositoryProgressionResult {
        if (status == null) throw new IllegalArgumentException("repository progression status is required");
        if (playerState == null) throw new IllegalArgumentException("repository progression player state is required");
    }

    public boolean persisted() {
        return status == Status.PERSISTED;
    }

    public enum Status {
        PERSISTED,
        REJECTED,
        PET_NOT_FOUND
    }
}
