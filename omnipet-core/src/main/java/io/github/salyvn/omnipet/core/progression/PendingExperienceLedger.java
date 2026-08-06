package io.github.salyvn.omnipet.core.progression;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Experience earned but not yet written to disk.
 *
 * <p>A kill must not cost a disk write. A player farming a grinder kills several mobs a second, each write
 * takes the player's revision lock, and two kills landing together would make one of them lose the race and
 * retry — so the cost is not one write per kill but one write plus contention per kill. Amounts are banked
 * here and flushed on a timer instead, which turns a burst of kills into a single write.
 *
 * <p>Adding and draining are separate operations on purpose. Kills arrive on the server's main thread, where
 * nothing may block; the flush runs off it. {@link #drain()} takes everything at once and empties the ledger,
 * so a kill landing during a flush is banked for the next one rather than lost or double-counted.
 *
 * <p>Not durable, and deliberately so. A crash loses at most one flush interval of experience, which is a
 * fair trade against journalling every mob kill — the alternative writes a file per kill to make a few
 * seconds of progress survive a crash that also lost the player's position and inventory changes.
 */
public final class PendingExperienceLedger {
    private final Map<UUID, Map<UUID, Double>> pending = new LinkedHashMap<>();

    /**
     * Banks experience for one pet.
     *
     * <p>Ignores amounts that are not positive rather than rejecting them, so a rule that works out to zero
     * for a particular mob is a no-op instead of an error the caller has to guard against.
     */
    public synchronized void add(UUID ownerId, UUID petId, double amount) {
        Objects.requireNonNull(ownerId, "experience owner ID");
        Objects.requireNonNull(petId, "experience pet ID");
        if (!Double.isFinite(amount) || amount <= 0) return;
        pending.computeIfAbsent(ownerId, key -> new LinkedHashMap<>())
                .merge(petId, amount, Double::sum);
    }

    /** Whether anything is waiting, so a flush can skip scheduling work when nothing was killed. */
    public synchronized boolean isEmpty() {
        return pending.isEmpty();
    }

    /**
     * Everything banked so far, leaving the ledger empty.
     *
     * <p>One call rather than a read followed by a clear: a kill arriving between the two would be dropped,
     * and on a busy server that gap is hit constantly rather than rarely.
     */
    public synchronized Map<UUID, Map<UUID, Double>> drain() {
        if (pending.isEmpty()) return Map.of();
        Map<UUID, Map<UUID, Double>> taken = new LinkedHashMap<>();
        pending.forEach((owner, pets) -> taken.put(owner, Map.copyOf(pets)));
        pending.clear();
        return Map.copyOf(taken);
    }

    /**
     * Puts experience back after a failed write.
     *
     * <p>A flush that loses the revision race must not silently discard what it was carrying — the player
     * killed those mobs. Returning the amount merges it with whatever arrived meanwhile, so the next flush
     * writes both.
     */
    public synchronized void restore(UUID ownerId, Map<UUID, Double> amounts) {
        Objects.requireNonNull(ownerId, "experience owner ID");
        if (amounts == null || amounts.isEmpty()) return;
        amounts.forEach((petId, amount) -> add(ownerId, petId, amount));
    }

    /** Forgets an owner's banked experience, for a player whose pets are no longer ours to credit. */
    public synchronized void forget(UUID ownerId) {
        pending.remove(Objects.requireNonNull(ownerId, "experience owner ID"));
    }
}
