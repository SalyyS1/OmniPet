package io.github.salyvn.omnipet.paper.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.Particle;
import org.junit.jupiter.api.Test;

import io.github.salyvn.omnipet.core.domain.PetInstance;
import io.github.salyvn.omnipet.core.runtime.InteractionIndex;
import io.github.salyvn.omnipet.core.runtime.MovementController;
import io.github.salyvn.omnipet.core.runtime.PetActivationService;
import io.github.salyvn.omnipet.core.runtime.RuntimeTransform;
import io.github.salyvn.omnipet.core.runtime.RuntimeVector;
import io.github.salyvn.omnipet.core.storage.PetStorageLimits;

/**
 * Idle play seen end-to-end: a pet whose owner has stopped circles them and trails a particle for
 * nearby players, and a following pet does neither. Both properties only exist if they survive the
 * whole tick loop, so the fixture drives the real coordinator with a fake clock rather than poking
 * {@code PaperRuntimePetState} directly.
 */
class PaperRuntimeIdlePlayTest {
    @Test
    void aPlayingPetTrailsParticlesAtItsCadenceButNoneWhileFollowing() {
        Fixture fixture = new Fixture(IdlePlaySettings.defaults());
        fixture.spawn();

        // Standing still for five seconds: the pet is playing, so a heart trails at the ten-tick cadence.
        fixture.stillSeconds(5);
        int whilePlaying = fixture.particleBursts.get();
        assertTrue(whilePlaying > 0, "a playing pet has to trail a particle");
        // 100 playing ticks at one burst per ten, minus a warm-up tick or two before it starts playing.
        assertTrue(whilePlaying >= 7 && whilePlaying <= 10,
                "bursts must follow the ten-tick cadence, saw " + whilePlaying);

        // The burst is drawn where the pet actually is, not at the owner's feet.
        RuntimeVector owner = fixture.ownerPosition;
        RuntimeVector burst = fixture.lastBurst.get();
        double dx = burst.x() - owner.x();
        double dz = burst.z() - owner.z();
        assertTrue(Math.sqrt(dx * dx + dz * dz) > 0.2, "a playing pet trails its particle out where it orbits");

        // Walking cancels play outright: a following pet trails nothing.
        int before = fixture.particleBursts.get();
        fixture.walkSeconds(3);
        assertEquals(before, fixture.particleBursts.get(),
                "a pet being steered must not trail idle particles");
    }

    @Test
    void aPlayingPetLeavesItsFollowSpotToCircleTheOwner() {
        Fixture fixture = new Fixture(IdlePlaySettings.defaults());
        PetInstance pet = fixture.spawn();

        // Sampled across a few seconds of standing still: the pet keeps moving rather than parking on
        // one hover spot, which is the whole point of playing.
        fixture.stillSeconds(1);
        RuntimeVector first = fixture.transform(pet).position();
        fixture.stillSeconds(1);
        RuntimeVector later = fixture.transform(pet).position();

        assertTrue(first.subtract(later).length() > 0.05,
                "a playing pet has to keep circling, not settle onto a single spot");
    }

    @Test
    void disablingPlayInstallsANoOpSinkSoNoParticleIsEverDrawn() {
        // Same still owner, but the feature is off: the wiring must hand the engine the no-op sink, not
        // the capturing one, so a disabled operator gets exactly the older behaviour back.
        Fixture fixture = new Fixture(new IdlePlaySettings(false, Particle.HEART, 1, 0.3, 10));
        fixture.spawn();

        fixture.stillSeconds(5);

        assertEquals(0, fixture.particleBursts.get(),
                "a disabled feature must draw no particle even for a still owner");
    }

    private static final class Fixture {
        private final FakeScheduler scheduler = new FakeScheduler();
        private final RuntimeTestRenderer renderer = new RuntimeTestRenderer();
        private final AtomicLong clock = new AtomicLong(1_000_000_000);
        private RuntimeVector ownerPosition = new RuntimeVector(0, 64, 0);
        private final AtomicInteger particleBursts = new AtomicInteger();
        private final AtomicReference<RuntimeVector> lastBurst = new AtomicReference<>();
        private final UUID owner = UUID.randomUUID();
        private final PaperPetRuntimeCoordinator coordinator;

        private Fixture(IdlePlaySettings idlePlay) {
            PetVanityParticleSink sink = (ownerId, position) -> {
                particleBursts.incrementAndGet();
                lastBurst.set(position);
            };
            coordinator = new PaperPetRuntimeCoordinator(
                    scheduler,
                    new PaperRuntimeSettings(0, 1, 4, PetStorageLimits.MAX_ACTIVE_SLOT_COUNT, 0),
                    new PetActivationService(new InteractionIndex()),
                    new MovementController(),
                    new HeadFallbackRendererResolver(request -> renderer, renderer),
                    ownerId -> Optional.of(new PaperRuntimeOwnerPose(
                            ownerPosition, new RuntimeVector(0, 0, 1), 0, 0, true)),
                    clock::get,
                    failure -> {},
                    null,
                    idlePlay,
                    sink);
        }

        private PetInstance spawn() {
            PetInstance pet = RuntimeTestFixtures.pet(UUID.randomUUID(), "wolf");
            coordinator.acceptSnapshot(
                    RuntimeTestFixtures.storage(owner, 0, List.of(pet), List.of(pet.id())),
                    RuntimeTestFixtures.registry(0, RuntimeTestFixtures.definition("wolf")));
            coordinator.start();
            scheduler.runTick();
            return pet;
        }

        /** Ticks the loop with the owner standing still, twenty ticks a second like the real loop. */
        private void stillSeconds(double seconds) {
            int ticks = (int) Math.round(seconds * 20);
            for (int tick = 0; tick < ticks; tick++) {
                clock.addAndGet(50_000_000L);
                scheduler.runTick();
            }
        }

        /** Ticks the loop with the owner walking, so the pet is steered rather than playing. */
        private void walkSeconds(double seconds) {
            int ticks = (int) Math.round(seconds * 20);
            for (int tick = 0; tick < ticks; tick++) {
                ownerPosition = ownerPosition.add(new RuntimeVector(0.2, 0, 0));
                clock.addAndGet(50_000_000L);
                scheduler.runTick();
            }
        }

        private RuntimeTransform transform(PetInstance pet) {
            RuntimeTransform transform = renderer.updateTransforms.get(pet.id());
            if (transform == null) throw new AssertionError("the renderer was never updated for this pet");
            return transform;
        }
    }

    private static final class FakeScheduler implements PaperRuntimeScheduler {
        private FakeTask task;

        @Override public boolean isMainThread() { return true; }

        @Override
        public ScheduledTask scheduleRepeating(Runnable runnable, long initialDelayTicks, long periodTicks) {
            task = new FakeTask(runnable);
            return task;
        }

        private void runTick() { task.runnable.run(); }
    }

    private static final class FakeTask implements PaperRuntimeScheduler.ScheduledTask {
        private final Runnable runnable;
        private boolean cancelled;

        private FakeTask(Runnable runnable) { this.runnable = runnable; }
        @Override public void cancel() { cancelled = true; }
        @Override public boolean cancelled() { return cancelled; }
    }
}
