package io.github.salyvn.omnipet.paper.management;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.salyvn.omnipet.core.domain.PetInstance;
import io.github.salyvn.omnipet.core.domain.PlayerState;
import io.github.salyvn.omnipet.core.incubation.EggEscrowItemObservation;
import io.github.salyvn.omnipet.core.incubation.EggInventoryHand;
import io.github.salyvn.omnipet.core.incubation.EggItemIdentity;
import io.github.salyvn.omnipet.core.management.PetManagementResult;
import io.github.salyvn.omnipet.core.persistence.StaleRevisionException;
import io.github.salyvn.omnipet.core.progression.CultivationItemActionFileJournal;
import io.github.salyvn.omnipet.core.progression.CultivationItemActionKind;
import io.github.salyvn.omnipet.core.progression.CultivationItemActionService;
import io.github.salyvn.omnipet.core.progression.CultivationItemActionStage;
import io.github.salyvn.omnipet.core.progression.CultivationItemActionTransaction;
import io.github.salyvn.omnipet.core.progression.ProgressionConfig;
import io.github.salyvn.omnipet.core.progression.ProgressionMutationContext;
import io.github.salyvn.omnipet.core.progression.ProgressionResult;
import io.github.salyvn.omnipet.core.progression.ProgressionState;
import io.github.salyvn.omnipet.core.progression.RepositoryProgressionResult;
import io.github.salyvn.omnipet.core.release.ReleasePreview;
import io.github.salyvn.omnipet.core.release.ReleasePreviewResult;
import io.github.salyvn.omnipet.core.release.ReleaseResult;
import io.github.salyvn.omnipet.paper.incubation.EggInventoryMutationResult;

class PetCultivationActionCoordinatorTest {
    @TempDir Path temporary;

    @Test
    void restartRecoversRemovedItemAppliesOnceAndCommits() throws Exception {
        Fixture fixture = fixture(1);
        prepareRemoved(fixture.root(), fixture.action());

        PetCultivationActionCoordinator.Result result = join(
                coordinator(fixture).recover(fixture.action().actionToken(), progression()));

        assertEquals(PetCultivationActionCoordinator.Status.COMMITTED, result.status());
        assertEquals(CultivationItemActionStage.COMMITTED, load(fixture).stage());
        assertEquals(1, fixture.repository().applications);
        assertEquals(1, fixture.repository().acknowledgements);
        assertFalse(fixture.repository().receiptPresent);
        assertEquals(0, fixture.inventory().refunds);
    }

    @Test
    void durableReceiptAfterCrashCommitsAndAcknowledgesWithoutReapplying() throws Exception {
        Fixture fixture = fixture(2);
        prepareRemoved(fixture.root(), fixture.action());
        fixture.repository().receiptPresent = true;

        PetCultivationActionCoordinator.Result result = join(
                coordinator(fixture).recover(fixture.action().actionToken(), progression()));

        assertEquals(PetCultivationActionCoordinator.Status.COMMITTED, result.status());
        assertEquals(CultivationItemActionStage.COMMITTED, load(fixture).stage());
        assertEquals(0, fixture.repository().applications);
        assertEquals(1, fixture.repository().acknowledgements);
        assertFalse(fixture.repository().receiptPresent);
    }

    @Test
    void duplicateCommittedNonceReplayNeverAppliesProgressionTwice() throws Exception {
        Fixture fixture = fixture(1);
        prepareRemoved(fixture.root(), fixture.action());

        PetCultivationActionCoordinator.Result first = join(
                coordinator(fixture).recover(fixture.action().actionToken(), progression()));
        PetCultivationActionCoordinator.Result replay = join(
                coordinator(fixture).recover(fixture.action().actionToken(), progression()));

        assertEquals(PetCultivationActionCoordinator.Status.COMMITTED, first.status());
        assertEquals(PetCultivationActionCoordinator.Status.ALREADY_COMMITTED, replay.status());
        assertEquals(1, fixture.repository().applications);
        assertEquals(CultivationItemActionStage.COMMITTED, load(fixture).stage());
    }

    @Test
    void staleRevisionAfterRemovalRefundsTheExactItem() throws Exception {
        Fixture fixture = fixture(2);
        prepareRemoved(fixture.root(), fixture.action());

        PetCultivationActionCoordinator.Result result = join(
                coordinator(fixture).recover(fixture.action().actionToken(), progression()));

        assertEquals(PetCultivationActionCoordinator.Status.REFUNDED, result.status());
        assertEquals(CultivationItemActionStage.REFUNDED, load(fixture).stage());
        assertEquals(0, fixture.repository().applications);
        assertEquals(1, fixture.inventory().refunds);
        assertEquals(fixture.action().item(), fixture.inventory().lastRefunded);
        assertEquals(EggEscrowItemObservation.MATCHING_ITEM_PRESENT, fixture.inventory().observation);
    }

    @Test
    void preparedAbsentItemWithoutReceiptRequiresOperatorReview() throws Exception {
        Fixture fixture = fixture(1);
        actionService(fixture.root()).prepare(fixture.action());

        PetCultivationActionCoordinator.Result result = join(
                coordinator(fixture).recover(fixture.action().actionToken(), progression()));

        assertEquals(PetCultivationActionCoordinator.Status.OPERATOR_REVIEW, result.status());
        assertEquals(CultivationItemActionStage.OPERATOR_REVIEW, load(fixture).stage());
        assertEquals(0, fixture.repository().applications);
        assertEquals(0, fixture.inventory().removals);
        assertEquals(0, fixture.inventory().refunds);
    }

    private Fixture fixture(long revision) {
        UUID playerId = UUID.randomUUID();
        UUID petId = UUID.randomUUID();
        UUID token = UUID.randomUUID();
        Path root = temporary.resolve(token.toString());
        CultivationItemActionTransaction action = new CultivationItemActionTransaction(
                token,
                playerId,
                petId,
                1,
                new EggItemIdentity(
                        3,
                        EggInventoryHand.MAIN_HAND,
                        "minecraft:paper",
                        token,
                        "a".repeat(64),
                        1,
                        Map.of("snapshot", "test")),
                CultivationItemActionKind.EXPERIENCE_CANDY,
                250,
                1,
                0,
                CultivationItemActionStage.PREPARED);
        return new Fixture(
                root,
                action,
                new FakeRepository(playerId, petId, revision),
                new FakeInventory());
    }

    private PetCultivationActionCoordinator coordinator(Fixture fixture) {
        PetManagementControllerContext context = new PetManagementControllerContext(
                fixture.repository(),
                PetManagementAuthorizationPort.ownerOnly(),
                Runnable::run,
                UUID::randomUUID);
        return new PetCultivationActionCoordinator(
                context,
                actionService(fixture.root()),
                fixture.inventory(),
                new DirectMainThread());
    }

    private static void prepareRemoved(Path root, CultivationItemActionTransaction action) throws Exception {
        CultivationItemActionService service = actionService(root);
        service.prepare(action);
        service.markItemRemoved(action.actionToken());
    }

    private static CultivationItemActionTransaction load(Fixture fixture) throws Exception {
        return actionService(fixture.root()).find(fixture.action().actionToken()).orElseThrow();
    }

    private static CultivationItemActionService actionService(Path root) {
        return new CultivationItemActionService(new CultivationItemActionFileJournal(root));
    }

    private static ProgressionMutationContext progression() {
        return new ProgressionMutationContext(
                new ProgressionConfig(
                        5,
                        100,
                        5,
                        values -> 150,
                        Map.of(),
                        ProgressionConfig.OverflowPolicy.CARRY),
                "default",
                Map.of(),
                100,
                1_000);
    }

    private static PetCultivationActionCoordinator.Result join(
            java.util.concurrent.CompletionStage<PetCultivationActionCoordinator.Result> stage) {
        return stage.toCompletableFuture().join();
    }

    private record Fixture(
            Path root,
            CultivationItemActionTransaction action,
            FakeRepository repository,
            FakeInventory inventory) {}

    private static final class DirectMainThread implements PetManagementMainThread {
        @Override public boolean isMainThread() { return true; }
        @Override public void execute(Runnable task) { task.run(); }
    }

    private static final class FakeInventory implements PetConsumableInventoryPort {
        private EggEscrowItemObservation observation = EggEscrowItemObservation.MATCHING_ITEM_ABSENT;
        private int removals;
        private int refunds;
        private EggItemIdentity lastRefunded;

        @Override public CaptureResult capture(UUID viewerId, Kind kind) {
            throw new UnsupportedOperationException("capture is not used by recovery tests");
        }

        @Override public ConsumeResult consumeOne(Capture capture) {
            throw new UnsupportedOperationException("legacy consumption is not used by recovery tests");
        }

        @Override public EggEscrowItemObservation observe(UUID playerId, EggItemIdentity item) {
            return observation;
        }

        @Override public EggInventoryMutationResult removeOne(UUID playerId, EggItemIdentity item) {
            removals++;
            observation = EggEscrowItemObservation.MATCHING_ITEM_ABSENT;
            return EggInventoryMutationResult.REMOVED;
        }

        @Override public EggInventoryMutationResult refundOne(UUID playerId, EggItemIdentity item) {
            refunds++;
            lastRefunded = item;
            observation = EggEscrowItemObservation.MATCHING_ITEM_PRESENT;
            return EggInventoryMutationResult.REFUNDED;
        }
    }

    private static final class FakeRepository implements PetManagementRepositoryPort {
        private final UUID playerId;
        private final UUID petId;
        private PlayerState state;
        private boolean receiptPresent;
        private int applications;
        private int acknowledgements;

        private FakeRepository(UUID playerId, UUID petId, long revision) {
            this.playerId = playerId;
            this.petId = petId;
            state = new PlayerState(
                    playerId,
                    revision,
                    List.of(new PetInstance(petId, "wolf", 1, Map.of(), Map.of())),
                    10,
                    1,
                    List.of(),
                    List.of(),
                    Map.of(),
                    null,
                    Map.of());
        }

        @Override public PlayerState snapshot(UUID ownerId) { return state; }

        @Override public RepositoryProgressionResult addExperience(
                UUID ownerId,
                long revision,
                UUID id,
                double amount,
                ProgressionMutationContext context,
                CultivationItemActionTransaction action) {
            if (revision != state.revision()) throw new StaleRevisionException(revision, state.revision());
            applications++;
            receiptPresent = true;
            state = state.withRevision(state.revision() + 1);
            ProgressionState progression = new ProgressionState(2, 100, 0, 100, 1_000, Map.of());
            return new RepositoryProgressionResult(
                    RepositoryProgressionResult.Status.PERSISTED,
                    state,
                    state.pets().getFirst(),
                    new ProgressionResult(ProgressionResult.Status.APPLIED, progression, 1, "applied"));
        }

        @Override public RepositoryProgressionResult addExperience(
                UUID ownerId,
                long revision,
                UUID id,
                double amount,
                ProgressionMutationContext context) {
            throw new UnsupportedOperationException("non-action progression is not used");
        }

        @Override public RepositoryProgressionResult breakthrough(
                UUID ownerId,
                long revision,
                UUID id,
                int requiredLevel,
                int requiredEvolution,
                ProgressionMutationContext context) {
            throw new UnsupportedOperationException("breakthrough is not used");
        }

        @Override public boolean hasCultivationAction(
                UUID ownerId,
                UUID id,
                CultivationItemActionTransaction action) {
            return receiptPresent;
        }

        @Override public void acknowledgeCultivationAction(
                UUID ownerId,
                UUID id,
                CultivationItemActionTransaction action) {
            acknowledgements++;
            if (receiptPresent) {
                receiptPresent = false;
                state = state.withRevision(state.revision() + 1);
            }
        }

        @Override public PetManagementResult favorite(UUID ownerId, long revision, UUID id, boolean value) {
            throw new UnsupportedOperationException("management mutation is not used");
        }

        @Override public PetManagementResult lock(UUID ownerId, long revision, UUID id, boolean value) {
            throw new UnsupportedOperationException("management mutation is not used");
        }

        @Override public PetManagementResult move(UUID ownerId, long revision, UUID id, int targetIndex) {
            throw new UnsupportedOperationException("management mutation is not used");
        }

        @Override public ReleasePreviewResult previewRelease(UUID transactionId, UUID ownerId, UUID id) {
            throw new UnsupportedOperationException("release preview is not used");
        }

        @Override public ReleaseResult release(ReleasePreview preview) {
            throw new UnsupportedOperationException("release is not used");
        }
    }
}
