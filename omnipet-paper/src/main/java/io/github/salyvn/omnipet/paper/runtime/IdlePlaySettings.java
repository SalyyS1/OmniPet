package io.github.salyvn.omnipet.paper.runtime;

import java.util.Objects;

import org.bukkit.Particle;

/**
 * Whether idle pets play around a still owner, and the vanity particle they trail while they do.
 *
 * <p>On by default because a companion that freezes when its owner stops reads as switched off, which is
 * the whole thing this feature exists to fix. An operator who wants the older, calmer pet sets
 * {@code enabled} false and gets exactly the previous behaviour back — the pet still turns to watch them,
 * it just holds its follow spot rather than circling.
 *
 * <p>The particle is world-scoped when emitted, so nearby players see the pet play too, which is why the
 * count is small and the cadence is in ticks rather than every tick: a burst of a few particles once every
 * half-second reads as a happy pet, and cannot become a way to spray effects across a server.
 */
public record IdlePlaySettings(
        boolean enabled,
        Particle particle,
        int particleCount,
        double particleSpread,
        int particleEveryTicks) {
    /** Bounded so a mistyped count cannot ask the server to broadcast thousands of particles per pet. */
    public static final int MAX_PARTICLE_COUNT = 16;

    /** Slowest and fastest a pet may trail particles, in ticks between bursts. */
    public static final int MIN_PARTICLE_EVERY_TICKS = 5;
    public static final int MAX_PARTICLE_EVERY_TICKS = 200;

    public IdlePlaySettings {
        particle = Objects.requireNonNull(particle, "idle-play particle");
        if (particleCount < 1 || particleCount > MAX_PARTICLE_COUNT) {
            throw new IllegalArgumentException(
                    "idle-play particle count must be between 1 and " + MAX_PARTICLE_COUNT);
        }
        if (!Double.isFinite(particleSpread) || particleSpread < 0 || particleSpread > 2) {
            throw new IllegalArgumentException("idle-play particle spread must be between 0 and 2 blocks");
        }
        if (particleEveryTicks < MIN_PARTICLE_EVERY_TICKS || particleEveryTicks > MAX_PARTICLE_EVERY_TICKS) {
            throw new IllegalArgumentException("idle-play particle cadence must be between "
                    + MIN_PARTICLE_EVERY_TICKS + " and " + MAX_PARTICLE_EVERY_TICKS + " ticks");
        }
    }

    public static IdlePlaySettings defaults() {
        // A gentle heart every half-second: unmistakably a pet, cheap enough to broadcast.
        return new IdlePlaySettings(true, Particle.HEART, 1, 0.3, 10);
    }

    /** The cadence a playing pet should use, or zero when play or its particles are switched off. */
    public int effectiveParticleEveryTicks() {
        return enabled ? particleEveryTicks : 0;
    }
}
