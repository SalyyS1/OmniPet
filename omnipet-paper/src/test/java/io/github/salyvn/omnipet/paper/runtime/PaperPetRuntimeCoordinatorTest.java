package io.github.salyvn.omnipet.paper.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import io.github.salyvn.omnipet.core.domain.PetInstance;
import io.github.salyvn.omnipet.core.domain.DisplayDefinition;
import io.github.salyvn.omnipet.core.runtime.InteractionIndex;
import io.github.salyvn.omnipet.core.runtime.MovementController;
import io.github.salyvn.omnipet.core.runtime.PetActivationService;
import io.github.salyvn.omnipet.core.runtime.RuntimeVector;
import io.github.salyvn.omnipet.core.storage.PetStorageLimits;

class PaperPetRuntimeCoordinatorTest {
    @Test
    void oneIdempotentTaskReconcilesUpdatesRemovesReloadsAndCloses() {
        Fixture fixture = new Fixture(new PaperRuntimeSettings(
                1, 1, 8, PetStorageLimits.MAX_ACTIVE_SLOT_COUNT));
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
                0, 1, 1, PetStorageLimits.MAX_ACTIVE_SLOT_COUNT));
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

    private static final class Fixture {
        private final FakeScheduler scheduler = new FakeScheduler();
        private final RuntimeTestRenderer renderer = new RuntimeTestRenderer();
        private final AtomicLong clock = new AtomicLong(1_000_000_000);
        private final List<PaperRuntimeFailure> failures = new ArrayList<>();
        private final PaperPetRuntimeCoordinator coordinator;

        private Fixture(PaperRuntimeSettings settings) {
            coordinator = new PaperPetRuntimeCoordinator(
                    scheduler,
                    settings,
                    new PetActivationService(new InteractionIndex()),
                    new MovementController(),
                    new HeadFallbackRendererResolver(request -> renderer, renderer),
                    ownerId -> Optional.of(new PaperRuntimeOwnerPose(
                            new RuntimeVector(0, 64, 0), new RuntimeVector(0, 0, 1), 0, 0, true)),
                    clock::get,
                    failures::add);
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
