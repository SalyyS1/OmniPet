package io.github.salyvn.omnipet.paper.incubation;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Monotonic epochs prevent work accepted before quit from entering a later session. */
final class IncubationOnlineEpochs {
    private final Map<UUID, Long> versions = new HashMap<>();
    private final Map<UUID, Long> online = new HashMap<>();

    synchronized long join(UUID playerId) {
        UUID target = requirePlayer(playerId);
        long epoch = next(target);
        online.put(target, epoch);
        return epoch;
    }

    synchronized long currentOrJoin(UUID playerId) {
        UUID target = requirePlayer(playerId);
        Long current = online.get(target);
        return current == null ? join(target) : current;
    }

    synchronized void quit(UUID playerId) {
        UUID target = requirePlayer(playerId);
        next(target);
        online.remove(target);
    }

    synchronized boolean isCurrent(UUID playerId, long epoch) {
        return Long.valueOf(epoch).equals(online.get(playerId));
    }

    synchronized void clear() {
        versions.clear();
        online.clear();
    }

    private long next(UUID playerId) {
        return versions.merge(playerId, 1L, Math::addExact);
    }

    private static UUID requirePlayer(UUID playerId) {
        if (playerId == null) throw new IllegalArgumentException("player id is required");
        return playerId;
    }
}
