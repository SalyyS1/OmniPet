package io.github.salyvn.omnipet.core.runtime;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Deterministic per-tick and per-pet limiter; callers supply monotonic time. */
public final class EffectBudget {
    private final int globalPerTick;
    private final int perPetPerTick;
    private final Map<UUID, Integer> petCounts = new HashMap<>();
    private final Map<EffectKey, Long> lastEmissionNanos = new HashMap<>();
    private long tick = Long.MIN_VALUE;
    private int globalCount;

    public EffectBudget(int globalPerTick, int perPetPerTick) {
        if (globalPerTick < 0 || perPetPerTick < 0 || perPetPerTick > globalPerTick) {
            throw new IllegalArgumentException("effect budgets are invalid");
        }
        this.globalPerTick = globalPerTick;
        this.perPetPerTick = perPetPerTick;
    }

    public synchronized boolean tryAcquire(
            long currentTick,
            long nowNanos,
            UUID petId,
            String effectId,
            long cooldownNanos) {
        if (currentTick < 0 || nowNanos < 0 || cooldownNanos < 0) {
            throw new IllegalArgumentException("effect timing values must be non-negative");
        }
        Objects.requireNonNull(petId, "effect pet ID");
        if (effectId == null || effectId.isBlank()) throw new IllegalArgumentException("effect ID is required");
        if (tick != currentTick) {
            tick = currentTick;
            globalCount = 0;
            petCounts.clear();
        }
        if (globalCount >= globalPerTick || petCounts.getOrDefault(petId, 0) >= perPetPerTick) return false;
        EffectKey key = new EffectKey(petId, effectId);
        Long previous = lastEmissionNanos.get(key);
        if (previous != null && (nowNanos < previous || nowNanos - previous < cooldownNanos)) return false;
        globalCount++;
        petCounts.merge(petId, 1, Integer::sum);
        lastEmissionNanos.put(key, nowNanos);
        return true;
    }

    private record EffectKey(UUID petId, String effectId) {}
}
