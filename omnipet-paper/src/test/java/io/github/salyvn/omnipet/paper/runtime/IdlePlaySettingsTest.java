package io.github.salyvn.omnipet.paper.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.Particle;
import org.junit.jupiter.api.Test;

/**
 * The bounds on idle play exist so a mistyped config cannot turn a happy pet into a particle cannon.
 * A count or cadence outside the range is a configuration error, so it is rejected at construction
 * rather than clamped silently, which would hide the mistake from the operator who made it.
 */
class IdlePlaySettingsTest {
    @Test
    void defaultsAreOnAndGentle() {
        IdlePlaySettings defaults = IdlePlaySettings.defaults();

        assertTrue(defaults.enabled(), "the feature ships on: a frozen pet is the bug it fixes");
        assertEquals(Particle.HEART, defaults.particle());
        assertEquals(1, defaults.particleCount(), "one particle a burst is a pet, not a fountain");
        assertEquals(10, defaults.effectiveParticleEveryTicks());
    }

    @Test
    void aParticleCountOutsideItsBoundsIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> settings(0, 0.3, 10));
        assertThrows(IllegalArgumentException.class,
                () -> settings(IdlePlaySettings.MAX_PARTICLE_COUNT + 1, 0.3, 10));
    }

    @Test
    void anImpossibleSpreadIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> settings(1, -0.1, 10));
        assertThrows(IllegalArgumentException.class, () -> settings(1, 2.5, 10));
        assertThrows(IllegalArgumentException.class, () -> settings(1, Double.NaN, 10));
    }

    @Test
    void aCadenceOutsideItsBoundsIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> settings(1, 0.3, IdlePlaySettings.MIN_PARTICLE_EVERY_TICKS - 1));
        assertThrows(IllegalArgumentException.class,
                () -> settings(1, 0.3, IdlePlaySettings.MAX_PARTICLE_EVERY_TICKS + 1));
    }

    @Test
    void aNullParticleIsRejected() {
        assertThrows(NullPointerException.class, () -> settings(null, 1, 0.3, 10));
    }

    @Test
    void turningPlayOffSilencesTheParticleCadence() {
        // A disabled feature reports a zero cadence so the wiring installs a no-op sink rather than
        // branching on enabled for every pet on every tick.
        IdlePlaySettings off = new IdlePlaySettings(false, Particle.HEART, 1, 0.3, 10);
        assertEquals(0, off.effectiveParticleEveryTicks());

        IdlePlaySettings on = new IdlePlaySettings(true, Particle.HEART, 1, 0.3, 25);
        assertEquals(25, on.effectiveParticleEveryTicks());
    }

    private static IdlePlaySettings settings(int count, double spread, int everyTicks) {
        return settings(Particle.HEART, count, spread, everyTicks);
    }

    private static IdlePlaySettings settings(Particle particle, int count, double spread, int everyTicks) {
        return new IdlePlaySettings(true, particle, count, spread, everyTicks);
    }
}
