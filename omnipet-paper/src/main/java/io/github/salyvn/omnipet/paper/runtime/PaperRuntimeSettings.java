package io.github.salyvn.omnipet.paper.runtime;

import io.github.salyvn.omnipet.core.storage.PetStorageLimits;

/**
 * How hard the one pet-runtime task is allowed to work.
 *
 * <p>Both {@code maximum} values are throughput ceilings, not targets: the loop stops early when it has
 * visited that many, and the round-robin cursor means the rest are picked up next tick.
 */
public record PaperRuntimeSettings(
        long initialDelayTicks,
        long periodTicks,
        int maximumOwnersPerTick,
        int maximumPetsPerOwner,
        long maximumNanosPerTick) {
    /** Owners visited per tick before the loop defers the rest to the next tick. */
    public static final int DEFAULT_OWNERS_PER_TICK = 64;

    /**
     * Pets rendered per owner.
     *
     * <p>Ten rather than the 64 the active-slot schema allows. A slot ceiling is what a player may own;
     * this is what the server agrees to draw every tick, and each pet costs three tracked entities. The
     * two numbers previously disagreed — the record said 64 while the config path said 10 — so an
     * operator who never wrote the key got a different limit from one who did.
     */
    public static final int DEFAULT_PETS_PER_OWNER = 10;

    /**
     * Wall-clock the loop may spend in one tick, in nanoseconds.
     *
     * <p>Two milliseconds of a fifty-millisecond tick. The count ceilings above bound throughput but not
     * time: sixty-four owners of ten pets each is a fixed amount of work only if every pet costs the
     * same, and a distant pet crossing a chunk boundary does not. This is the ceiling that protects TPS.
     */
    public static final long DEFAULT_NANOS_PER_TICK = 2_000_000L;

    public PaperRuntimeSettings {
        if (initialDelayTicks < 0) throw new IllegalArgumentException("initial delay cannot be negative");
        if (periodTicks < 1) throw new IllegalArgumentException("runtime period must be positive");
        if (maximumOwnersPerTick < 1) throw new IllegalArgumentException("owner budget must be positive");
        if (maximumPetsPerOwner < 1 || maximumPetsPerOwner > PetStorageLimits.MAX_ACTIVE_SLOT_COUNT) {
            throw new IllegalArgumentException("pet budget is outside the supported active-slot range");
        }
        // Zero disables the time budget, which is what an operator diagnosing the count ceilings sets.
        if (maximumNanosPerTick < 0) {
            throw new IllegalArgumentException("runtime time budget cannot be negative");
        }
    }

    public static PaperRuntimeSettings defaults() {
        return new PaperRuntimeSettings(
                1, 1, DEFAULT_OWNERS_PER_TICK, DEFAULT_PETS_PER_OWNER, DEFAULT_NANOS_PER_TICK);
    }

    /** Whether the loop should stop on elapsed time rather than only on the count ceilings. */
    public boolean timeBudgeted() {
        return maximumNanosPerTick > 0;
    }
}
