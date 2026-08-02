package io.github.salyvn.omnipet.paper.management;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.salyvn.omnipet.core.domain.PetInstance;
import io.github.salyvn.omnipet.core.domain.PlayerState;
import io.github.salyvn.omnipet.core.management.PetManagementMetadata;
import io.github.salyvn.omnipet.core.management.PetManagementResult;
import io.github.salyvn.omnipet.core.incubation.EggEscrowItemObservation;
import io.github.salyvn.omnipet.core.progression.CultivationItemActionFileJournal;
import io.github.salyvn.omnipet.core.progression.CultivationItemActionService;
import io.github.salyvn.omnipet.core.progression.ProgressionConfig;
import io.github.salyvn.omnipet.core.progression.ProgressionMutationContext;
import io.github.salyvn.omnipet.core.progression.ProgressionResult;
import io.github.salyvn.omnipet.core.progression.ProgressionState;
import io.github.salyvn.omnipet.core.progression.RepositoryProgressionResult;
import io.github.salyvn.omnipet.core.release.InternalOutboxResult;
import io.github.salyvn.omnipet.core.release.ReleaseOutboxEntry;
import io.github.salyvn.omnipet.core.release.ReleasePreview;
import io.github.salyvn.omnipet.core.release.ReleasePreviewResult;
import io.github.salyvn.omnipet.core.release.ReleaseResult;
import io.github.salyvn.omnipet.core.release.ReleaseRewardBundle;
import io.github.salyvn.omnipet.paper.incubation.EggInventoryMutationResult;

class PaperPetManagementControllerTest {
    @TempDir Path temporary;
    private UUID viewer;
    private UUID petId;
    private FakeRepository repository;
    private FakeConsumables consumables;
    private PaperPetManagementController controller;

    @BeforeEach
    void setUp() {
        viewer = UUID.randomUUID();
        petId = UUID.randomUUID();
        repository = new FakeRepository(state(1, pet(false, false)));
        consumables = new FakeConsumables(viewer);
        AtomicInteger ids = new AtomicInteger();
        controller = new PaperPetManagementController(
                repository,
                PetManagementAuthorizationPort.ownerOnly(),
                new CultivationItemActionService(new CultivationItemActionFileJournal(
                        temporary.resolve("cultivation-actions"))),
                consumables,
                new DirectMainThread(),
                (owner, transaction) -> CompletableFuture.completedFuture(
                        new InternalOutboxResult(InternalOutboxResult.Status.ACKNOWLEDGED, null, "acknowledged")),
                Runnable::run,
                () -> new UUID(0, ids.incrementAndGet()));
    }

    @Test
    void stableSessionAdvancesRevisionAndRejectsTheOldView() {
        PetManagementOutcome opened = join(controller.open(viewer, viewer, petId));
        PetManagementSession first = opened.session();
        PetManagementOutcome favorite = join(controller.favorite(first, true));
        PetManagementOutcome stale = join(controller.lock(first, true));

        assertEquals(PetManagementOutcome.Status.VIEW_READY, opened.status());
        assertEquals(PetManagementOutcome.Status.PERSISTED, favorite.status());
        assertEquals(2, favorite.session().expectedRevision());
        assertEquals(true, PetManagementMetadata.read(favorite.view().pet()).favorite());
        assertEquals(PetManagementOutcome.Status.STALE_SESSION, stale.status());
        assertEquals(1, repository.managementCalls);
    }

    @Test
    void consumableIsRemovedFirstAndExactlyRefundedWhenProgressionRejects() {
        PetManagementSession session = join(controller.open(viewer, viewer, petId)).session();
        repository.persistProgression = false;
        PetManagementOutcome rejected = join(controller.addExperience(session, context()));

        assertEquals(PetManagementOutcome.Status.REJECTED, rejected.status(), rejected.detail());
        assertEquals(List.of("capture", "observe", "remove", "observe", "progression", "observe", "refund"),
                repository.events);
        assertEquals(1, consumables.removals);
        assertEquals(1, consumables.refunds);

        repository.events.clear();
        repository.persistProgression = true;
        PetManagementOutcome persisted = join(controller.addExperience(session, context()));

        assertEquals(PetManagementOutcome.Status.PERSISTED, persisted.status());
        assertEquals(List.of("capture", "observe", "remove", "observe", "progression"), repository.events);
        assertEquals(2, consumables.removals);
        assertEquals(1, consumables.refunds);
        assertEquals(2, persisted.session().expectedRevision());
    }

    @Test
    void releaseUsesFrozenSessionPreviewThenReturnsOutboxRecoveryBoundary() {
        PetManagementSession session = join(controller.open(viewer, viewer, petId)).session();
        PetManagementOutcome previewed = join(controller.previewRelease(session));
        PetManagementOutcome released = join(controller.confirmRelease(session, previewed.releasePreview()));
        PetManagementOutcome recovered = join(controller.recoverRelease(
                viewer, viewer, petId, released.transactionId()));

        assertEquals(PetManagementOutcome.Status.RELEASE_PREVIEW_READY, previewed.status());
        assertNotNull(previewed.view().releasePreview());
        assertEquals(PetManagementOutcome.Status.RELEASE_COMMITTED_OUTBOX_PENDING, released.status());
        assertEquals(PetManagementOutcome.Status.OUTBOX_RECOVERED, recovered.status());
        assertEquals(InternalOutboxResult.Status.ACKNOWLEDGED, recovered.outbox().status());
        assertEquals(PetManagementOutcome.Status.STALE_SESSION,
                join(controller.favorite(session, true)).status());
    }

    private ProgressionMutationContext context() {
        ProgressionConfig config = new ProgressionConfig(
                100, 100, 1, values -> 100, Map.of(), ProgressionConfig.OverflowPolicy.CARRY);
        return new ProgressionMutationContext(config, null, Map.of(), 100, 1_000);
    }

    private PlayerState state(long revision, PetInstance... pets) {
        return new PlayerState(viewer, revision, List.of(pets), 30, 1, List.of(), List.of(), Map.of(), null, Map.of());
    }

    private PetInstance pet(boolean favorite, boolean locked) {
        PetInstance pet = new PetInstance(petId, "wolf", 1, Map.of(), Map.of());
        return PetManagementMetadata.write(pet, new PetManagementMetadata(favorite, locked, "Luna"));
    }

    private static PetManagementOutcome join(java.util.concurrent.CompletionStage<PetManagementOutcome> stage) {
        return stage.toCompletableFuture().join();
    }

    private final class FakeRepository implements PetManagementRepositoryPort {
        private PlayerState state;
        private int managementCalls;
        private boolean persistProgression;
        private final List<String> events = new ArrayList<>();

        private FakeRepository(PlayerState state) { this.state = state; }
        @Override public PlayerState snapshot(UUID ownerId) { return state; }
        @Override public PetManagementResult favorite(UUID ownerId, long revision, UUID id, boolean value) {
            managementCalls++;
            PetInstance updated = PetManagementMetadata.write(state.pets().getFirst(),
                    PetManagementMetadata.read(state.pets().getFirst()).withFavorite(value));
            state = state(revision + 1, updated);
            return new PetManagementResult(PetManagementResult.Status.PERSISTED, state, updated, "favorite updated");
        }
        @Override public PetManagementResult lock(UUID ownerId, long revision, UUID id, boolean value) {
            managementCalls++;
            return new PetManagementResult(PetManagementResult.Status.REJECTED, state, state.pets().getFirst(), "rejected");
        }
        @Override public PetManagementResult move(UUID ownerId, long revision, UUID id, int targetIndex) {
            managementCalls++;
            return new PetManagementResult(PetManagementResult.Status.REJECTED, state, state.pets().getFirst(), "rejected");
        }
        @Override public RepositoryProgressionResult addExperience(
                UUID ownerId, long revision, UUID id, double amount, ProgressionMutationContext context) {
            events.add("progression");
            if (!persistProgression) {
                return new RepositoryProgressionResult(RepositoryProgressionResult.Status.REJECTED, state,
                        state.pets().getFirst(), new ProgressionResult(
                                ProgressionResult.Status.INVALID_AMOUNT,
                                ProgressionState.initial(100, 1_000), 0, "rejected"));
            }
            state = state(revision + 1, state.pets().getFirst());
            return new RepositoryProgressionResult(RepositoryProgressionResult.Status.PERSISTED, state,
                    state.pets().getFirst(), new ProgressionResult(
                            ProgressionResult.Status.APPLIED,
                            ProgressionState.initial(100, 1_000), 0, "applied"));
        }
        @Override public RepositoryProgressionResult breakthrough(
                UUID ownerId, long revision, UUID id, int level, int evolution, ProgressionMutationContext context) {
            return addExperience(ownerId, revision, id, 1, context);
        }
        @Override public ReleasePreviewResult previewRelease(UUID transactionId, UUID ownerId, UUID id) {
            ReleaseRewardBundle rewards = new ReleaseRewardBundle(
                    List.of(new ReleaseRewardBundle.InternalReward("pet_dust", 3, Map.of())),
                    List.of(new ReleaseRewardBundle.ExternalReward(
                            "vault", "coins", new BigDecimal("25"), Map.of())));
            return new ReleasePreviewResult(ReleasePreviewResult.Status.READY,
                    new ReleasePreview(transactionId, ownerId, id, state.revision(), "f".repeat(64), rewards, "token"),
                    "ready");
        }
        @Override public ReleaseResult release(ReleasePreview preview) {
            ReleaseOutboxEntry entry = new ReleaseOutboxEntry(
                    preview.transactionId(), preview.playerId(), preview.petId(), preview.petFingerprint(),
                    preview.confirmationToken(), preview.rewards(), ReleaseOutboxEntry.InternalState.PENDING,
                    ReleaseOutboxEntry.ExternalState.PENDING, "", Map.of());
            state = state(state.revision() + 1);
            return new ReleaseResult(ReleaseResult.Status.COMMITTED, state, entry, "committed");
        }
    }

    private final class FakeConsumables implements PetConsumableInventoryPort {
        private final UUID viewerId;
        private int removals;
        private int refunds;
        private boolean itemPresent = true;

        private FakeConsumables(UUID viewerId) { this.viewerId = viewerId; }
        @Override public CaptureResult capture(UUID viewer, Kind kind) {
            repository.events.add("capture");
            return new CaptureResult(CaptureResult.Status.CAPTURED,
                    kind == Kind.EXPERIENCE_CANDY
                            ? new Capture(viewerId, UUID.randomUUID(), "f".repeat(64), 4, kind, 50, 0, 0)
                            : new Capture(viewerId, UUID.randomUUID(), "f".repeat(64), 4, kind, 0, 10, 0),
                    "captured");
        }
        @Override public ConsumeResult consumeOne(Capture capture) {
            repository.events.add("consume");
            return new ConsumeResult(ConsumeResult.Status.AMBIGUOUS, "legacy path is not used");
        }
        @Override public EggEscrowItemObservation observe(UUID playerId, io.github.salyvn.omnipet.core.incubation.EggItemIdentity item) {
            repository.events.add("observe");
            return itemPresent
                    ? EggEscrowItemObservation.MATCHING_ITEM_PRESENT
                    : EggEscrowItemObservation.MATCHING_ITEM_ABSENT;
        }
        @Override public EggInventoryMutationResult removeOne(
                UUID playerId, io.github.salyvn.omnipet.core.incubation.EggItemIdentity item) {
            repository.events.add("remove");
            if (!itemPresent) return EggInventoryMutationResult.NOT_MATCHING;
            itemPresent = false;
            removals++;
            return EggInventoryMutationResult.REMOVED;
        }
        @Override public EggInventoryMutationResult refundOne(
                UUID playerId, io.github.salyvn.omnipet.core.incubation.EggItemIdentity item) {
            repository.events.add("refund");
            if (itemPresent) return EggInventoryMutationResult.ALREADY_PRESENT;
            itemPresent = true;
            refunds++;
            return EggInventoryMutationResult.REFUNDED;
        }
    }

    private static final class DirectMainThread implements PetManagementMainThread {
        @Override public boolean isMainThread() { return true; }
        @Override public void execute(Runnable task) { task.run(); }
    }
}
