package io.github.salyvn.omnipet.paper.task;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Rejects stale async UI completions without reusing a token after logout. */
public final class PlayerRequestTracker {
    private final AtomicLong sequence = new AtomicLong();
    private final ConcurrentHashMap<UUID, Long> current = new ConcurrentHashMap<>();

    public long begin(UUID playerId) {
        if (playerId == null) throw new IllegalArgumentException("player id is required");
        long token = sequence.incrementAndGet();
        current.put(playerId, token);
        return token;
    }

    public boolean isCurrent(UUID playerId, long token) {
        return current.getOrDefault(playerId, Long.MIN_VALUE) == token;
    }

    public void invalidate(UUID playerId) {
        if (playerId != null) current.remove(playerId);
    }

    public void clear() {
        current.clear();
    }
}
