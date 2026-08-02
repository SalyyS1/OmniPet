package io.github.salyvn.omnipet.paper.incubation.action;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

public interface IncubationItemActionHatchPort {
    Snapshot snapshot(UUID playerId) throws IOException;
    Mutation reduce(UUID playerId, long revision, UUID incubationId, long millis, UUID token) throws IOException;
    Mutation complete(UUID playerId, long revision, UUID incubationId, UUID token) throws IOException;

    record Snapshot(UUID playerId, long revision, UUID incubationId, Set<UUID> actionTokens) {
        public Snapshot {
            actionTokens = Set.copyOf(actionTokens == null ? Set.of() : actionTokens);
        }
        public boolean matches(UUID player, UUID incubation) {
            return playerId != null && playerId.equals(player) && incubationId != null && incubationId.equals(incubation);
        }
        public boolean applied(UUID token) { return actionTokens.contains(token); }
    }

    record Mutation(boolean accepted, String status) {
        public Mutation { status = status == null ? "" : status; }
    }
}
