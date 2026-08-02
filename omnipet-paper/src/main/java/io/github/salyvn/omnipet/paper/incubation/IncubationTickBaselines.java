package io.github.salyvn.omnipet.paper.incubation;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Starts elapsed-time accounting only after committed escrow is first observed. */
final class IncubationTickBaselines {
    private final Map<UUID, Baseline> baselines = new HashMap<>();

    synchronized long elapsedMillis(
            UUID playerId,
            UUID incubationId,
            long sampledNanos,
            long observedNanos) {
        UUID player = Objects.requireNonNull(playerId, "player id");
        UUID incubation = Objects.requireNonNull(incubationId, "incubation id");
        Baseline current = baselines.get(player);
        if (current == null || !current.incubationId().equals(incubation)) {
            baselines.put(player, new Baseline(incubation, observedNanos));
            return 0L;
        }
        long elapsedNanos = sampledNanos - current.sampledNanos();
        return elapsedNanos <= 0L ? 0L : Duration.ofNanos(elapsedNanos).toMillis();
    }

    synchronized void observeCommitted(UUID playerId, UUID incubationId, long committedNanos) {
        UUID player = Objects.requireNonNull(playerId, "player id");
        UUID incubation = Objects.requireNonNull(incubationId, "incubation id");
        Baseline current = baselines.get(player);
        if (current == null || !current.incubationId().equals(incubation)) {
            baselines.put(player, new Baseline(incubation, committedNanos));
        }
    }

    synchronized void commit(UUID playerId, UUID incubationId, long sampledNanos) {
        UUID player = Objects.requireNonNull(playerId, "player id");
        UUID incubation = Objects.requireNonNull(incubationId, "incubation id");
        Baseline current = baselines.get(player);
        if (current != null && current.incubationId().equals(incubation)
                && sampledNanos - current.sampledNanos() > 0L) {
            baselines.put(player, new Baseline(incubation, sampledNanos));
        }
    }

    synchronized void reset(UUID playerId) {
        if (playerId != null) baselines.remove(playerId);
    }

    synchronized void clear() {
        baselines.clear();
    }

    private record Baseline(UUID incubationId, long sampledNanos) {}
}
