package io.github.salyvn.omnipet.core.persistence;

import java.io.IOException;
import java.util.UUID;
import java.util.Set;
import java.util.List;
import java.util.function.UnaryOperator;

import io.github.salyvn.omnipet.core.domain.PlayerState;

public interface PlayerStateRepository {
    PlayerState snapshot(UUID playerId) throws IOException;

    PlayerState withLocked(UUID playerId, long expectedRevision, UnaryOperator<PlayerState> mutation) throws IOException;

    default Set<String> referenceScan(String definitionId) throws IOException {
        return Set.of();
    }

    default List<UUID> playerIds(int limit) throws IOException {
        if (limit < 1) throw new IllegalArgumentException("player scan limit must be positive");
        return List.of();
    }
}
