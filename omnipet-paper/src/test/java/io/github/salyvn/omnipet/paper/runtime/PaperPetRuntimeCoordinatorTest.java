package io.github.salyvn.omnipet.paper.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import io.github.salyvn.omnipet.core.domain.PetInstance;
import io.github.salyvn.omnipet.core.domain.DisplayDefinition;
import io.github.salyvn.omnipet.core.runtime.IdleBehaviour;
import io.github.salyvn.omnipet.core.runtime.InteractionIndex;
import io.github.salyvn.omnipet.core.runtime.MovementController;
import io.github.salyvn.omnipet.core.runtime.MovementFacing;
import io.github.salyvn.omnipet.core.runtime.PetActivationService;
import io.github.salyvn.omnipet.core.runtime.RuntimeTransform;
import io.github.salyvn.omnipet.core.runtime.RuntimeVector;
import io.github.salyvn.omnipet.core.storage.PetStorageLimits;

class PaperPetRuntimeCoordinatorTest {
    @Test
    void oneIdempotentTaskReconcilesUpdatesRemovesReloadsAndCloses() {
        // Time budget off: this fixture drives a fake clock, so a real elapsed-time ceiling would
        // decide when the loop stops rather than the count ceiling under test.
        Fixture fixture = new Fixture(new PaperRuntimeSettings(
                1, 1, 8, PetStorageLimits.MAX_ACTIVE_SLOT_COUNT, 0));
        UUID owner = UUID.randomUUID();
        PetInstance first = RuntimeTestFixtures.pet(UUID.randomUUID(), "wolf");
        PetInstance second = RuntimeTestFixtures.pet(UUID.randomUUID(), "fox");
        fixture.coordinator.acceptSnapshot(
                RuntimeTestFixtures.storage(owner, 0, List.of(first, second), List.of(first.id(), second.id())),
                RuntimeTestFixtures.registry(0,
                        RuntimeTestFixtures.definition("wolf"), RuntimeTestFixtures.definition("fox")));

        fixture.coordinator.start();
        fixture.coordinator.start();
        assertEquals(1, fixture.scheduler.scheduleCalls);
        fixture.scheduler.runTick();
        assertEquals(2, fixture.coordinator.activePetCount());
        assertEquals(2, fixture.renderer.spawnCounts.size());

        fixture.clock.addAndGet(50_000_000);
        fixture.scheduler.runTick();
        assertEquals(1, fixture.renderer.updateCounts.get(first.id()));
        assertEquals(1, fixture.renderer.updateCounts.get(second.id()));

        fixture.coordinator.acceptSnapshot(
                RuntimeTestFixtures.storage(owner, 1, List.of(first, second), List.of(second.id())),
                RuntimeTestFixtures.registry(0,
                        RuntimeTestFixtures.definition("wolf"), RuntimeTestFixtures.definition("fox")));
        fixture.scheduler.runTick();
        assertEquals(1, fixture.coordinator.activePetCount());
        assertEquals(1, fixture.renderer.removeCounts.get(first.id()));

        fixture.coordinator.ownerWorldChanged(owner);
        assertEquals(0, fixture.coordinator.activePetCount());
        fixture.scheduler.runTick();
        assertEquals(1, fixture.coordinator.activePetCount());
        assertEquals(2, fixture.renderer.spawnCounts.get(second.id()));

        fixture.coordinator.reload();
        assertEquals(0, fixture.coordinator.activePetCount());
        fixture.scheduler.runTick();
        assertEquals(1, fixture.coordinator.activePetCount());
        assertEquals(3, fixture.renderer.spawnCounts.get(second.id()));

        fixture.coordinator.close();
        assertTrue(fixture.scheduler.task.cancelled());
        assertEquals(0, fixture.coordinator.activePetCount());
        assertFalse(fixture.coordinator.started());
        assertThrows(IllegalStateException.class, fixture.coordinator::start);
    }

    @Test
    void invalidDefinitionAndRendererFailureAreIsolatedPerPet() {
        Fixture fixture = new Fixture(PaperRuntimeSettings.defaults());
        UUID owner = UUID.randomUUID();
        PetInstance valid = RuntimeTestFixtures.pet(UUID.randomUUID(), "wolf");
        PetInstance missing = RuntimeTestFixtures.pet(UUID.randomUUID(), "missing");
        PetInstance rendererFailure = RuntimeTestFixtures.pet(UUID.randomUUID(), "fox");
        fixture.renderer.failSpawnPet = rendererFailure.id();
        fixture.coordinator.acceptSnapshot(
                RuntimeTestFixtures.storage(owner, 0, List.of(valid, missing, rendererFailure),
                        List.of(valid.id(), missing.id(), rendererFailure.id())),
                RuntimeTestFixtures.registry(0,
                        RuntimeTestFixtures.definition("wolf"), RuntimeTestFixtures.definition("fox")));
        fixture.coordinator.start();

        fixture.scheduler.runTick();

        assertEquals(1, fixture.coordinator.activePetCount());
        assertTrue(fixture.renderer.handles.containsKey(valid.id()));
        assertTrue(fixture.failures.stream().anyMatch(failure ->
                missing.id().equals(failure.petInstanceId())
                        && failure.stage() == PaperRuntimeFailure.Stage.SNAPSHOT));
        assertTrue(fixture.failures.stream().anyMatch(failure ->
                rendererFailure.id().equals(failure.petInstanceId())
                        && failure.stage() == PaperRuntimeFailure.Stage.RECONCILIATION));
    }

    @Test
    void persistedAppearanceKeepsAnActivePetRenderableWhenDefinitionIsMissing() {
        Fixture fixture = new Fixture(PaperRuntimeSettings.defaults());
        UUID owner = UUID.randomUUID();
        UUID petId = UUID.randomUUID();
        PetInstance persisted = new PetInstance(
                petId,
                "archived_wolf",
                7,
                Map.of("appearance", Map.of(
                        "provider", "HEAD",
                        "fallbackHeadSource", "TEXTURE_URL",
                        "fallbackHeadValue", "https://textures.minecraft.net/texture/archived_wolf")),
                Map.of());
        fixture.coordinator.acceptSnapshot(
                RuntimeTestFixtures.storage(owner, 0, List.of(persisted), List.of(petId)),
                RuntimeTestFixtures.registry(1));
        fixture.coordinator.start();

        fixture.scheduler.runTick();

        assertEquals(1, fixture.coordinator.activePetCount());
        assertEquals("archived_wolf", fixture.renderer.spawnRequests.get(petId).definitionId());
        assertEquals(
                "https://textures.minecraft.net/texture/archived_wolf",
                fixture.renderer.spawnRequests.get(petId).appearance().fallbackHeadValue());
        assertFalse(fixture.failures.stream().anyMatch(failure -> petId.equals(failure.petInstanceId())));
    }

    /**
     * An activated pet reaches its renderer carrying a name.
     *
     * <p>The nameplate was reported missing three times, and each fix was checked one layer at a time — the
     * resolver returned the right string, the renderer wrote whatever it was handed. Both passed while no
     * pet on a live server had a plate, because nothing asserted that the string the resolver produces is
     * the string that arrives in a spawn request.
     *
     * <p>This is the case that was actually broken: a name only existed if an operator had found
     * {@code display.name}, an undocumented raw-node key with no Studio field, so in practice no definition
     * had one and every pet was correctly resolved to nothing.
     */
    @Test
    void anActivatedPetReachesItsRendererCarryingANameAndLevel() {
        Fixture fixture = new Fixture(PaperRuntimeSettings.defaults());
        UUID owner = UUID.randomUUID();
        PetInstance pet = new PetInstance(
                UUID.randomUUID(), "ember_fox", 0,
                Map.of("progression", Map.of("level", 12)), Map.of());
        fixture.coordinator.acceptSnapshot(
                RuntimeTestFixtures.storage(owner, 0, List.of(pet), List.of(pet.id())),
                RuntimeTestFixtures.registry(0, RuntimeTestFixtures.definition("ember_fox")));
        fixture.coordinator.start();

        fixture.scheduler.runTick();

        assertEquals("Ember fox",
                fixture.renderer.spawnRequests.get(pet.id()).appearance().displayName(),
                "an unnamed definition falls back to its readable ID, so no pet is anonymous");
        assertEquals(12, fixture.renderer.spawnRequests.get(pet.id()).appearance().level(),
                "the level travels beside the name so the plate template can place it");
    }

    /** An operator's name wins over the readable ID, and reaches the renderer unparsed. */
    @Test
    void anOperatorAuthoredNameReachesTheRenderer() {
        Fixture fixture = new Fixture(PaperRuntimeSettings.defaults());
        UUID owner = UUID.randomUUID();
        PetInstance pet = RuntimeTestFixtures.pet(UUID.randomUUID(), "ember_fox");
        fixture.coordinator.acceptSnapshot(
                RuntimeTestFixtures.storage(owner, 0, List.of(pet), List.of(pet.id())),
                RuntimeTestFixtures.registry(0, RuntimeTestFixtures.definition(
                        "ember_fox", DisplayDefinition.Provider.HEAD,
                        Map.of("display", Map.of("name", "<gold>Ember</gold>")))));
        fixture.coordinator.start();

        fixture.scheduler.runTick();

        assertEquals("<gold>Ember</gold>",
                fixture.renderer.spawnRequests.get(pet.id()).appearance().displayName(),
                "MiniMessage travels unparsed; the renderer parses it at the plate");
    }

    /** A player's own rename outranks both, which is the point of renaming. */
    @Test
    void aPlayerRenameOutranksTheDefinitionName() {
        Fixture fixture = new Fixture(PaperRuntimeSettings.defaults());
        UUID owner = UUID.randomUUID();
        PetInstance pet = new PetInstance(
                UUID.randomUUID(), "ember_fox", 0,
                Map.of(),
                // extensions, not components: a rename is per-instance metadata, which is where
                // PetManagementMetadata reads and writes it.
                Map.of("management", Map.of("customName", "Shadow")));
        fixture.coordinator.acceptSnapshot(
                RuntimeTestFixtures.storage(owner, 0, List.of(pet), List.of(pet.id())),
                RuntimeTestFixtures.registry(0, RuntimeTestFixtures.definition(
                        "ember_fox", DisplayDefinition.Provider.HEAD,
                        Map.of("display", Map.of("name", "Ember")))));
        fixture.coordinator.start();

        fixture.scheduler.runTick();

        assertEquals("Shadow",
                fixture.renderer.spawnRequests.get(pet.id()).appearance().displayName());
    }

    /** A pet whose definition was deleted still gets a plate, from the only source left: its own ID. */
    @Test
    void aPetWhoseDefinitionIsGoneStillArrivesNamed() {
        Fixture fixture = new Fixture(PaperRuntimeSettings.defaults());
        UUID owner = UUID.randomUUID();
        PetInstance persisted = new PetInstance(
                UUID.randomUUID(), "archived_wolf", 7,
                Map.of("appearance", Map.of(
                        "provider", "HEAD",
                        "fallbackHeadSource", "TEXTURE_URL",
                        "fallbackHeadValue", "https://textures.minecraft.net/texture/archived_wolf")),
                Map.of());
        fixture.coordinator.acceptSnapshot(
                RuntimeTestFixtures.storage(owner, 0, List.of(persisted), List.of(persisted.id())),
                RuntimeTestFixtures.registry(1));
        fixture.coordinator.start();

        fixture.scheduler.runTick();

        assertEquals("Archived wolf",
                fixture.renderer.spawnRequests.get(persisted.id()).appearance().displayName());
        // Level 1 because a pet with no progression node is level 1, matching ProgressionState.initial and
        // the vault's own reader — an archived pet is not a level-less pet.
        assertEquals(1, fixture.renderer.spawnRequests.get(persisted.id()).appearance().level());
    }

    @Test
    void reloadRecompilesCachedStorageAgainstTheActivatedRegistry() {
        Fixture fixture = new Fixture(PaperRuntimeSettings.defaults());
        UUID owner = UUID.randomUUID();
        PetInstance pet = RuntimeTestFixtures.pet(UUID.randomUUID(), "wolf");
        fixture.coordinator.acceptSnapshot(
                RuntimeTestFixtures.storage(owner, 0, List.of(pet), List.of(pet.id())),
                RuntimeTestFixtures.registry(0, RuntimeTestFixtures.definition("wolf")));
        fixture.coordinator.start();
        fixture.scheduler.runTick();

        fixture.coordinator.reload(RuntimeTestFixtures.registry(1,
                RuntimeTestFixtures.definition("wolf", DisplayDefinition.Provider.MODELENGINE, Map.of())));
        fixture.scheduler.runTick();

        assertEquals("MODELENGINE", fixture.renderer.spawnRequests.get(pet.id()).appearance().provider());
        assertEquals(2, fixture.renderer.spawnCounts.get(pet.id()));
    }

    @Test
    void ownerBudgetAdvancesRoundRobinWithoutCreatingPetTasks() {
        Fixture fixture = new Fixture(new PaperRuntimeSettings(
                0, 1, 1, PetStorageLimits.MAX_ACTIVE_SLOT_COUNT, 0));
        UUID firstOwner = UUID.randomUUID();
        UUID secondOwner = UUID.randomUUID();
        PetInstance firstPet = RuntimeTestFixtures.pet(UUID.randomUUID(), "wolf");
        PetInstance secondPet = RuntimeTestFixtures.pet(UUID.randomUUID(), "fox");
        fixture.coordinator.acceptSnapshot(
                RuntimeTestFixtures.storage(firstOwner, 0, List.of(firstPet), List.of(firstPet.id())),
                RuntimeTestFixtures.registry(0, RuntimeTestFixtures.definition("wolf")));
        fixture.coordinator.acceptSnapshot(
                RuntimeTestFixtures.storage(secondOwner, 0, List.of(secondPet), List.of(secondPet.id())),
                RuntimeTestFixtures.registry(0, RuntimeTestFixtures.definition("fox")));
        fixture.coordinator.start();

        fixture.scheduler.runTick();
        assertEquals(1, fixture.coordinator.activePetCount());
        fixture.scheduler.runTick();
        assertEquals(2, fixture.coordinator.activePetCount());
        assertEquals(1, fixture.scheduler.scheduleCalls);
    }

    @Test
    void theTimeBudgetStopsTheTickButNeverLosesAnOwner() {
        // The count ceiling bounds how much work is attempted; it cannot bound how long that work takes,
        // because pets do not all cost the same. An owner the budget stopped us reaching must be picked
        // up next tick rather than skipped until the cursor wraps all the way round.
        long budgetNanos = 5_000L;
        Fixture fixture = new Fixture(new PaperRuntimeSettings(
                0, 1, 4, PetStorageLimits.MAX_ACTIVE_SLOT_COUNT, budgetNanos));
        List<UUID> owners = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            UUID owner = UUID.randomUUID();
            PetInstance pet = RuntimeTestFixtures.pet(UUID.randomUUID(), "wolf");
            owners.add(owner);
            fixture.coordinator.acceptSnapshot(
                    RuntimeTestFixtures.storage(owner, 0, List.of(pet), List.of(pet.id())),
                    RuntimeTestFixtures.registry(0, RuntimeTestFixtures.definition("wolf")));
        }
        // Every owner costs the whole budget, so each tick gets through exactly one.
        fixture.nanosPerRead = budgetNanos;
        fixture.coordinator.start();

        fixture.scheduler.runTick();
        assertEquals(1, fixture.coordinator.activePetCount(), "the budget must stop the loop early");

        for (int tick = 0; tick < 3; tick++) fixture.scheduler.runTick();
        assertEquals(owners.size(), fixture.coordinator.activePetCount(),
                "every owner must still be reached across later ticks");
    }

    @Test
    void aBudgetSmallerThanOneOwnerStillMakesProgress() {
        // Checked after the owner rather than before, so an unreachably small budget degrades to
        // one-owner-per-tick instead of stalling the runtime completely.
        Fixture fixture = new Fixture(new PaperRuntimeSettings(
                0, 1, 4, PetStorageLimits.MAX_ACTIVE_SLOT_COUNT, 1));
        UUID owner = UUID.randomUUID();
        PetInstance pet = RuntimeTestFixtures.pet(UUID.randomUUID(), "wolf");
        fixture.coordinator.acceptSnapshot(
                RuntimeTestFixtures.storage(owner, 0, List.of(pet), List.of(pet.id())),
                RuntimeTestFixtures.registry(0, RuntimeTestFixtures.definition("wolf")));
        fixture.nanosPerRead = 1_000_000L;
        fixture.coordinator.start();

        fixture.scheduler.runTick();

        assertEquals(1, fixture.coordinator.activePetCount());
    }

    @Test
    void aZeroBudgetVisitsEveryOwnerInOneTick() {
        // The escape hatch for an operator diagnosing the count ceilings.
        Fixture fixture = new Fixture(new PaperRuntimeSettings(
                0, 1, 4, PetStorageLimits.MAX_ACTIVE_SLOT_COUNT, 0));
        for (int index = 0; index < 3; index++) {
            UUID owner = UUID.randomUUID();
            PetInstance pet = RuntimeTestFixtures.pet(UUID.randomUUID(), "wolf");
            fixture.coordinator.acceptSnapshot(
                    RuntimeTestFixtures.storage(owner, 0, List.of(pet), List.of(pet.id())),
                    RuntimeTestFixtures.registry(0, RuntimeTestFixtures.definition("wolf")));
        }
        fixture.nanosPerRead = 1_000_000L;
        fixture.coordinator.start();

        fixture.scheduler.runTick();

        assertEquals(3, fixture.coordinator.activePetCount());
    }

    @Test
    void quitForgetsPersistedIntentAndMainThreadGuardRejectsMutation() {
        Fixture fixture = new Fixture(PaperRuntimeSettings.defaults());
        UUID owner = UUID.randomUUID();
        PetInstance pet = RuntimeTestFixtures.pet(UUID.randomUUID(), "wolf");
        fixture.coordinator.acceptSnapshot(
                RuntimeTestFixtures.storage(owner, 0, List.of(pet), List.of(pet.id())),
                RuntimeTestFixtures.registry(0, RuntimeTestFixtures.definition("wolf")));
        fixture.coordinator.start();
        fixture.scheduler.runTick();

        fixture.coordinator.ownerQuit(owner);
        fixture.scheduler.runTick();
        assertEquals(0, fixture.coordinator.activePetCount());

        fixture.scheduler.mainThread = false;
        assertThrows(IllegalStateException.class, fixture.coordinator::tickNow);
    }

    @Test
    void aPetSettlesWhileItsOwnerStandsStillAndWakesWhenTheyMove() {
        // The whole feature seen from outside: nothing about idle is visible unless it reaches the
        // renderer, and the state only advances because real elapsed time flows through the tick loop.
        Fixture fixture = new Fixture(new PaperRuntimeSettings(
                0, 1, 4, PetStorageLimits.MAX_ACTIVE_SLOT_COUNT, 0));
        UUID owner = UUID.randomUUID();
        PetInstance pet = RuntimeTestFixtures.pet(UUID.randomUUID(), "wolf");
        fixture.coordinator.acceptSnapshot(
                RuntimeTestFixtures.storage(owner, 0, List.of(pet), List.of(pet.id())),
                RuntimeTestFixtures.registry(0, RuntimeTestFixtures.definition("wolf")));
        fixture.coordinator.start();
        fixture.scheduler.runTick();

        // A minute of standing still is past every temperament's settle threshold.
        fixture.advanceSeconds(60);
        assertTrue(fixture.transform(pet).resting(),
                "an owner who has stood still for a minute must have a settled pet");

        // And one stride is enough to put it back to work.
        fixture.walkSeconds(1);
        assertEquals(IdleBehaviour.State.ACTIVE, fixture.transform(pet).idle().state());
    }

    @Test
    void anIdlePetTurnsToItsOwnerRatherThanKeepingTheHeadingItStoppedOn() {
        Fixture fixture = new Fixture(new PaperRuntimeSettings(
                0, 1, 4, PetStorageLimits.MAX_ACTIVE_SLOT_COUNT, 0));
        UUID owner = UUID.randomUUID();
        PetInstance pet = RuntimeTestFixtures.pet(UUID.randomUUID(), "wolf");
        fixture.coordinator.acceptSnapshot(
                RuntimeTestFixtures.storage(owner, 0, List.of(pet), List.of(pet.id())),
                RuntimeTestFixtures.registry(0, RuntimeTestFixtures.definition("wolf")));
        fixture.coordinator.start();
        fixture.scheduler.runTick();
        fixture.advanceSeconds(60);

        RuntimeTransform settled = fixture.transform(pet);
        RuntimeVector toOwner = fixture.ownerPosition.subtract(settled.position());
        // Only meaningful if the pet is actually standing off to one side, which orbiting guarantees.
        assertTrue(Math.sqrt(toOwner.x() * toOwner.x() + toOwner.z() * toOwner.z()) > 0.2,
                "the pet has to be beside its owner for facing to mean anything");
        float wanted = (float) Math.toDegrees(Math.atan2(-toOwner.x(), toOwner.z()));
        float error = Math.abs(MovementFacing.normalize(wanted - settled.yaw()));

        assertTrue(error < 5, "a settled pet must be looking at its owner, off by " + error + " degrees");
    }

    @Test
    void aPetPerformsIdleFlourishesButNoneWhileItIsBeingSteered() {
        Fixture fixture = new Fixture(new PaperRuntimeSettings(
                0, 1, 4, PetStorageLimits.MAX_ACTIVE_SLOT_COUNT, 0));
        UUID owner = UUID.randomUUID();
        PetInstance pet = RuntimeTestFixtures.pet(UUID.randomUUID(), "wolf");
        fixture.coordinator.acceptSnapshot(
                RuntimeTestFixtures.storage(owner, 0, List.of(pet), List.of(pet.id())),
                RuntimeTestFixtures.registry(0, RuntimeTestFixtures.definition("wolf")));
        fixture.coordinator.start();
        fixture.scheduler.runTick();

        // Long enough to cover the longest jittered gap several times over.
        Set<IdleBehaviour.OneShot> seen = new LinkedHashSet<>();
        for (int step = 0; step < 240; step++) {
            fixture.advanceSeconds(1);
            IdleBehaviour.OneShot flourish = fixture.transform(pet).idle().flourish();
            if (flourish != null) seen.add(flourish);
        }
        assertTrue(seen.size() >= 2, "an idle pet has to do more than one thing, saw " + seen);

        // Walking cancels it outright: a flourish that outlived the owner leaving would play mid-chase.
        boolean flourishedWhileWalking = false;
        for (int step = 0; step < 60; step++) {
            fixture.walkSeconds(1);
            if (fixture.transform(pet).idle().flourish() != null) flourishedWhileWalking = true;
        }
        assertFalse(flourishedWhileWalking, "a pet being steered must not be performing idle flourishes");
    }

    @Test
    void noIdleStateAdvancesWhileNobodyIsThereToSeeIt() {
        // A pet's only viewer is its owner, so an absent owner is exactly the no-viewer case. The pet is
        // torn down rather than left ticking, and it comes back unsettled rather than resuming a count
        // that ran while the world was empty.
        Fixture fixture = new Fixture(new PaperRuntimeSettings(
                0, 1, 4, PetStorageLimits.MAX_ACTIVE_SLOT_COUNT, 0));
        UUID owner = UUID.randomUUID();
        PetInstance pet = RuntimeTestFixtures.pet(UUID.randomUUID(), "wolf");
        fixture.coordinator.acceptSnapshot(
                RuntimeTestFixtures.storage(owner, 0, List.of(pet), List.of(pet.id())),
                RuntimeTestFixtures.registry(0, RuntimeTestFixtures.definition("wolf")));
        fixture.coordinator.start();
        fixture.scheduler.runTick();

        fixture.ownerPresent = false;
        int updatesBefore = fixture.renderer.updateCounts.getOrDefault(pet.id(), 0);
        fixture.advanceSeconds(120);
        assertEquals(0, fixture.coordinator.activePetCount());
        assertEquals(updatesBefore, fixture.renderer.updateCounts.getOrDefault(pet.id(), 0),
                "an absent owner must cost zero renderer updates, not a tick of settling");

        fixture.ownerPresent = true;
        fixture.scheduler.runTick();
        fixture.advanceSeconds(1);
        assertEquals(IdleBehaviour.State.ATTENTIVE, fixture.transform(pet).idle().state(),
                "time that passed with nobody watching must not count towards settling");
    }

    private static final class Fixture {
        private final FakeScheduler scheduler = new FakeScheduler();
        private final RuntimeTestRenderer renderer = new RuntimeTestRenderer();
        private final AtomicLong clock = new AtomicLong(1_000_000_000);
        /**
         * How far the clock jumps per read, so elapsed time is deterministic.
         *
         * <p>Zero keeps the clock still, which is what every test that is not about the time budget
         * wants: a frozen clock cannot trip a deadline by accident.
         */
        private long nanosPerRead;
        /** Where every owner stands. Moved by a test to drive the pet's idle state. */
        private RuntimeVector ownerPosition = new RuntimeVector(0, 64, 0);
        /** Whether owners are reachable at all, which is the only sense in which a pet has a viewer. */
        private boolean ownerPresent = true;
        private final List<PaperRuntimeFailure> failures = new ArrayList<>();
        private final PaperPetRuntimeCoordinator coordinator;

        private Fixture(PaperRuntimeSettings settings) {
            coordinator = new PaperPetRuntimeCoordinator(
                    scheduler,
                    settings,
                    new PetActivationService(new InteractionIndex()),
                    new MovementController(),
                    new HeadFallbackRendererResolver(request -> renderer, renderer),
                    ownerId -> ownerPresent
                            ? Optional.of(new PaperRuntimeOwnerPose(
                                    ownerPosition, new RuntimeVector(0, 0, 1), 0, 0, true))
                            : Optional.empty(),
                    this::readClock,
                    failures::add);
        }

        private long readClock() {
            return clock.getAndAdd(nanosPerRead);
        }

        /**
         * Runs ticks until {@code seconds} of pet-visible time has passed.
         *
         * <p>Twenty ticks a second, matching the real loop, because idle accumulates per update rather
         * than from wall time: one tick that jumped a minute would settle a pet a real server would not.
         */
        private void advanceSeconds(double seconds) {
            int ticks = (int) Math.round(seconds * 20);
            for (int tick = 0; tick < ticks; tick++) {
                clock.addAndGet(50_000_000L);
                scheduler.runTick();
            }
        }

        /** The last pose the renderer was handed for one pet. */
        private RuntimeTransform transform(PetInstance pet) {
            RuntimeTransform transform = renderer.updateTransforms.get(pet.id());
            if (transform == null) throw new AssertionError("the renderer was never updated for this pet");
            return transform;
        }

        /**
         * Runs ticks with the owner walking in a straight line the whole time.
         *
         * <p>Distinct from moving them once and then advancing: a single jump is one moving tick followed
         * by a stationary owner, which is a pet whose owner stopped, not a pet being walked.
         */
        private void walkSeconds(double seconds) {
            int ticks = (int) Math.round(seconds * 20);
            for (int tick = 0; tick < ticks; tick++) {
                // 4 m/s, comfortably above the still threshold and roughly a sprinting player.
                ownerPosition = ownerPosition.add(new RuntimeVector(0.2, 0, 0));
                clock.addAndGet(50_000_000L);
                scheduler.runTick();
            }
        }
    }

    private static final class FakeScheduler implements PaperRuntimeScheduler {
        private boolean mainThread = true;
        private int scheduleCalls;
        private FakeTask task;

        @Override public boolean isMainThread() { return mainThread; }

        @Override
        public ScheduledTask scheduleRepeating(Runnable runnable, long initialDelayTicks, long periodTicks) {
            scheduleCalls++;
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
