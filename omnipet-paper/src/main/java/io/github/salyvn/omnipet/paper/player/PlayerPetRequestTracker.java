package io.github.salyvn.omnipet.paper.player;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Rejects stale async UI completions without reusing a token after logout. */
final class PlayerPetRequestTracker {
    private final AtomicLong sequence = new AtomicLong();
    private final ConcurrentHashMap<UUID, Long> current = new ConcurrentHashMap<>();

    long begin(UUID playerId) {
        if (playerId == null) throw new IllegalArgumentException("player id is required");
        long token = sequence.incrementAndGet();
        current.put(playerId, token);
        return token;
    }

    boolean isCurrent(UUID playerId, long token) {
        return current.getOrDefault(playerId, Long.MIN_VALUE) == token;
    }

    void invalidate(UUID playerId) {
        if (playerId != null) current.remove(playerId);
    }

    void clear() {
        current.clear();
    }
}
