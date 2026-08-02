package io.github.salyvn.omnipet.paper.incubation.action;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.salyvn.omnipet.core.domain.incubation.IncubationState;
import io.github.salyvn.omnipet.core.incubation.EggEscrowItemObservation;
import io.github.salyvn.omnipet.core.incubation.EggInventoryHand;
import io.github.salyvn.omnipet.core.incubation.EggItemIdentity;
import io.github.salyvn.omnipet.core.incubation.IncubationItemActionFileJournal;
import io.github.salyvn.omnipet.core.incubation.IncubationItemActionItemContract;
import io.github.salyvn.omnipet.core.incubation.IncubationItemActionService;
import io.github.salyvn.omnipet.core.incubation.IncubationItemActionStage;
import io.github.salyvn.omnipet.core.incubation.IncubationItemActionTransaction;
import io.github.salyvn.omnipet.core.incubation.IncubationItemActionType;
import io.github.salyvn.omnipet.paper.incubation.EggInventoryMutationResult;

class IncubationItemActionCoordinatorTest {
    @TempDir Path temporary;
    private UUID player;
    private UUID incubation;
    private UUID token;
    private IncubationItemActionService actions;
    private FakeHatches hatches;
    private FakeInventory inventory;
    private IncubationItemActionCoordinator coordinator;

    @BeforeEach
    void setUp() {
        player = UUID.randomUUID();
        incubation = UUID.randomUUID();
        token = UUID.randomUUID();
        actions = new IncubationItemActionService(new IncubationItemActionFileJournal(temporary.resolve("actions")));
        hatches = new FakeHatches(player, incubation);
        inventory = new FakeInventory(actions, token);
        coordinator = new IncubationItemActionCoordinator(actions, hatches, inventory);
    }

    @Test
    void persistsBoundariesBeforeRemovalAndEffectThenCommitsExactlyOnce() throws Exception {
        IncubationItemActionTransaction request = transaction();
        IncubationItemActionResult first = coordinator.redeem(request);
        IncubationItemActionResult duplicate = coordinator.redeem(request);

        assertEquals(IncubationItemActionResult.Status.COMMITTED, first.status());
        assertEquals(IncubationItemActionResult.Status.ALREADY_COMMITTED, duplicate.status());
        assertEquals(List.of("remove:PREPARED", "apply:ITEM_REMOVED"), events());
        assertEquals(1, inventory.removals);
        assertEquals(1, hatches.applies);
    }

    @Test
    void restartAtItemRemovedAppliesOnlyWhenItemIsProvablyAbsent() throws Exception {
        actions.prepare(transaction());
        actions.markItemRemoved(token);
        inventory.present = false;

        assertEquals(IncubationItemActionResult.Status.COMMITTED, coordinator.recover(token).status());
        assertEquals(1, hatches.applies);
    }

    @Test
    void preparedAbsentWithoutTokenAndCommittedMissingTokenRequireReview() throws Exception {
        actions.prepare(transaction());
        inventory.present = false;
        assertEquals(IncubationItemActionResult.Status.OPERATOR_REVIEW, coordinator.recover(token).status());

        UUID second = UUID.randomUUID();
        IncubationItemActionTransaction other = transaction(second);
        actions.prepare(other);
        actions.markItemRemoved(second);
        actions.commit(second);
        assertEquals(IncubationItemActionResult.Status.OPERATOR_REVIEW, coordinator.recover(second).status());
    }

    @Test
    void refundPendingRecoversPresentOrAbsentItemIdempotently() throws Exception {
        actions.prepare(transaction());
        actions.markItemRemoved(token);
        actions.markRefundPending(token);
        inventory.present = false;
        assertEquals(IncubationItemActionResult.Status.REFUNDED, coordinator.recover(token).status());
        assertEquals(1, inventory.refunds);
        assertEquals(IncubationItemActionResult.Status.REFUNDED, coordinator.recover(token).status());
    }

    @Test
    void copiedTokenAndCapacityAndRefundAmbiguityFailClosed() throws Exception {
        actions.prepare(transaction());
        assertEquals(IncubationItemActionResult.Status.INVALID_TARGET,
                coordinator.redeem(new IncubationItemActionTransaction(
                        token, UUID.randomUUID(), UUID.randomUUID(), 1, item(), IncubationItemActionType.REDUCE,
                        500, IncubationItemActionStage.PREPARED)).status());

        UUID capacityToken = UUID.randomUUID();
        hatches.tokens.clear();
        for (int index = 0; index < IncubationState.MAX_ACTION_TOKENS; index++) hatches.tokens.add(UUID.randomUUID());
        assertEquals(IncubationItemActionResult.Status.CAPACITY_REACHED,
                coordinator.redeem(transaction(capacityToken)).status());

        hatches.tokens.clear();
        UUID ambiguous = UUID.randomUUID();
        hatches.applyEnabled = false;
        inventory.present = true;
        inventory.refundResult = EggInventoryMutationResult.AMBIGUOUS;
        inventory.observationAfterRemoval = EggEscrowItemObservation.AMBIGUOUS;
        inventory.assertedToken = ambiguous;
        assertEquals(IncubationItemActionResult.Status.OPERATOR_REVIEW,
                coordinator.redeem(transaction(ambiguous)).status());
    }

    @Test
    void rejectsOffMainThreadBeforeMutation() {
        inventory.mainThread = false;
        assertThrows(IllegalStateException.class, () -> coordinator.redeem(transaction()));
        assertEquals(0, inventory.removals);
    }

    @Test
    void rejectsTransactionTypeOrEffectThatDiffersFromBoundItemBeforeMutation() {
        EggItemIdentity reducer = item();

        assertThrows(IllegalArgumentException.class,
                () -> new IncubationItemActionTransaction(
                        token, player, incubation, hatches.revision, reducer,
                        IncubationItemActionType.COMPLETE, 0, IncubationItemActionStage.PREPARED));
        assertThrows(IllegalArgumentException.class,
                () -> new IncubationItemActionTransaction(
                        token, player, incubation, hatches.revision, reducer,
                        IncubationItemActionType.REDUCE, 5_000, IncubationItemActionStage.PREPARED));
        assertEquals(0, inventory.removals);
        assertEquals(0, hatches.applies);
    }

    private List<String> events() {
        List<String> result = new ArrayList<>();
        result.addAll(inventory.events);
        result.addAll(hatches.events);
        result.sort(java.util.Comparator.comparing(value -> value.startsWith("remove") ? 0 : 1));
        return result;
    }

    private IncubationItemActionTransaction transaction() { return transaction(token); }
    private IncubationItemActionTransaction transaction(UUID actionToken) {
        return new IncubationItemActionTransaction(actionToken, player, incubation, hatches.revision, item(),
                IncubationItemActionType.REDUCE, 500, IncubationItemActionStage.PREPARED);
    }
    private static EggItemIdentity item() {
        return new EggItemIdentity(4, EggInventoryHand.MAIN_HAND, "minecraft:paper", UUID.randomUUID(),
                "a".repeat(64), 1, IncubationItemActionItemContract.bind(
                        Map.of("payload", "reducer"), IncubationItemActionType.REDUCE, 500));
    }

    private final class FakeInventory implements IncubationItemActionInventoryPort {
        private final IncubationItemActionService service;
        private UUID assertedToken;
        private boolean mainThread = true;
        private boolean present = true;
        private int removals;
        private int refunds;
        private EggInventoryMutationResult refundResult = EggInventoryMutationResult.REFUNDED;
        private EggEscrowItemObservation observationAfterRemoval = EggEscrowItemObservation.MATCHING_ITEM_ABSENT;
        private final List<String> events = new ArrayList<>();
        private FakeInventory(IncubationItemActionService service, UUID token) { this.service = service; this.assertedToken = token; }
        @Override public boolean isMainThread() { return mainThread; }
        @Override public EggEscrowItemObservation observe(UUID playerId, EggItemIdentity item) {
            return present ? EggEscrowItemObservation.MATCHING_ITEM_PRESENT : observationAfterRemoval;
        }
        @Override public EggInventoryMutationResult removeOne(UUID playerId, EggItemIdentity item) throws IOException {
            IncubationItemActionStage stage = service.find(assertedToken).orElseThrow().stage();
            events.add("remove:" + stage);
            removals++;
            if (!present) return EggInventoryMutationResult.NOT_MATCHING;
            present = false;
            return EggInventoryMutationResult.REMOVED;
        }
        @Override public EggInventoryMutationResult refundOne(UUID playerId, EggItemIdentity item) {
            refunds++;
            if (refundResult == EggInventoryMutationResult.REFUNDED) present = true;
            return refundResult;
        }
    }

    private final class FakeHatches implements IncubationItemActionHatchPort {
        private final UUID player;
        private final UUID incubation;
        private long revision = 1;
        private final Set<UUID> tokens = new LinkedHashSet<>();
        private boolean applyEnabled = true;
        private int applies;
        private final List<String> events = new ArrayList<>();
        private FakeHatches(UUID player, UUID incubation) { this.player = player; this.incubation = incubation; }
        @Override public Snapshot snapshot(UUID playerId) { return new Snapshot(player, revision, incubation, tokens); }
        @Override public Mutation reduce(UUID playerId, long revision, UUID incubationId, long millis, UUID actionToken) throws IOException {
            IncubationItemActionStage stage = actions.find(actionToken).orElseThrow().stage();
            events.add("apply:" + stage);
            applies++;
            if (applyEnabled) { tokens.add(actionToken); this.revision++; }
            return new Mutation(applyEnabled, applyEnabled ? "REDUCED" : "INVALID_STATE");
        }
        @Override public Mutation complete(UUID playerId, long revision, UUID incubationId, UUID actionToken) throws IOException {
            return reduce(playerId, revision, incubationId, 0, actionToken);
        }
    }
}
