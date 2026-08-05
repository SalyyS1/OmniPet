package io.github.salyvn.omnipet.paper.feedback;

import java.util.Objects;

import org.bukkit.Particle;

/**
 * A particle burst, resolved once at load like a sound.
 *
 * <p>Kept as a value rather than a raw {@link Particle} so the count and spread travel with it: a burst
 * is only recognisable as celebratory or as a refusal by how much of it there is, and letting call sites
 * pick those numbers would put presentation decisions in click handlers.
 */
public record ResolvedParticle(Particle particle, int count, double spread) {
    /** Bounded so a mistyped count cannot ask the server to send thousands of particles per click. */
    public static final int MAX_COUNT = 64;

    public ResolvedParticle {
        particle = Objects.requireNonNull(particle, "particle");
        if (count < 1 || count > MAX_COUNT) {
            throw new IllegalArgumentException("particle count must be between 1 and " + MAX_COUNT);
        }
        if (!Double.isFinite(spread) || spread < 0 || spread > 4) {
            throw new IllegalArgumentException("particle spread must be between 0 and 4 blocks");
        }
    }
}
