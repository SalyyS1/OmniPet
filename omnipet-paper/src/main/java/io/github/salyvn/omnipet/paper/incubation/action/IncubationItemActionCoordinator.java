package io.github.salyvn.omnipet.paper.incubation.action;

import java.io.IOException;
import java.util.Objects;
import java.util.UUID;

import io.github.salyvn.omnipet.core.domain.incubation.IncubationState;
import io.github.salyvn.omnipet.core.incubation.EggEscrowItemObservation;
import io.github.salyvn.omnipet.core.incubation.IncubationItemActionService;
import io.github.salyvn.omnipet.core.incubation.IncubationItemActionStage;
import io.github.salyvn.omnipet.core.incubation.IncubationItemActionTransaction;
import io.github.salyvn.omnipet.core.incubation.IncubationItemActionType;
import io.github.salyvn.omnipet.paper.incubation.EggInventoryMutationResult;

public final class IncubationItemActionCoordinator {
    private final IncubationItemActionService actions;
    private final IncubationItemActionHatchPort hatches;
    private final IncubationItemActionInventoryPort inventory;

    public IncubationItemActionCoordinator(
            IncubationItemActionService actions,
            IncubationItemActionHatchPort hatches,
            IncubationItemActionInventoryPort inventory) {
        this.actions = Objects.requireNonNull(actions, "incubation item actions");
        this.hatches = Objects.requireNonNull(hatches, "incubation item action hatches");
        this.inventory = Objects.requireNonNull(inventory, "incubation item action inventory");
    }

    public IncubationItemActionResult redeem(IncubationItemActionTransaction requested) throws IOException {
        assertMainThread();
        Objects.requireNonNull(requested, "incubation item action request");
        IncubationItemActionHatchPort.Snapshot snapshot = hatches.snapshot(requested.playerId());
        if (!snapshot.matches(requested.playerId(), requested.incubationId())) {
            return result(IncubationItemActionResult.Status.INVALID_TARGET, requested, "player incubation differs");
        }
        if (!snapshot.applied(requested.actionToken())
                && snapshot.actionTokens().size() >= IncubationState.MAX_ACTION_TOKENS) {
            return result(IncubationItemActionResult.Status.CAPACITY_REACHED, requested, "action token capacity reached");
        }
        IncubationItemActionTransaction prepared = actions.prepare(requested.withStage(IncubationItemActionStage.PREPARED));
        return recover(prepared.actionToken());
    }

    public IncubationItemActionResult recover(UUID actionToken) throws IOException {
        assertMainThread();
        IncubationItemActionTransaction transaction = actions.find(actionToken)
                .orElseThrow(() -> new IOException("incubation item action does not exist: " + actionToken));
        IncubationItemActionHatchPort.Snapshot snapshot = hatches.snapshot(transaction.playerId());
        if (!snapshot.matches(transaction.playerId(), transaction.incubationId())) return review(transaction, "player/incubation mismatch");
        EggEscrowItemObservation observation = inventory.observe(transaction.playerId(), transaction.item());
        return switch (transaction.stage()) {
            case PREPARED -> recoverPrepared(transaction, snapshot, observation);
            case ITEM_REMOVED -> recoverRemoved(transaction, snapshot, observation);
            case REFUND_PENDING -> recoverRefund(transaction, snapshot, observation);
            case COMMITTED -> snapshot.applied(transaction.actionToken())
                    ? result(IncubationItemActionResult.Status.ALREADY_COMMITTED, transaction, "already committed")
                    : review(transaction, "committed action token is absent from incubation");
            case REFUNDED -> result(IncubationItemActionResult.Status.REFUNDED, transaction, "already refunded");
            case OPERATOR_REVIEW -> result(IncubationItemActionResult.Status.OPERATOR_REVIEW, transaction, "operator review remains required");
        };
    }

    private IncubationItemActionResult recoverPrepared(
            IncubationItemActionTransaction transaction,
            IncubationItemActionHatchPort.Snapshot snapshot,
            EggEscrowItemObservation observation) throws IOException {
        if (observation == EggEscrowItemObservation.AMBIGUOUS) return review(transaction, "prepared inventory is ambiguous");
        if (observation == EggEscrowItemObservation.MATCHING_ITEM_ABSENT) {
            if (!snapshot.applied(transaction.actionToken())) return review(transaction, "prepared item is absent without applied token");
            return commit(actions.markItemRemoved(transaction.actionToken()));
        }
        EggInventoryMutationResult removed = inventory.removeOne(transaction.playerId(), transaction.item());
        if (removed == EggInventoryMutationResult.AMBIGUOUS) return review(transaction, "item removal is ambiguous");
        if (removed != EggInventoryMutationResult.REMOVED) {
            return result(IncubationItemActionResult.Status.PENDING, transaction, "item was not removed: " + removed);
        }
        return apply(actions.markItemRemoved(transaction.actionToken()));
    }

    private IncubationItemActionResult recoverRemoved(
            IncubationItemActionTransaction transaction,
            IncubationItemActionHatchPort.Snapshot snapshot,
            EggEscrowItemObservation observation) throws IOException {
        if (snapshot.applied(transaction.actionToken())) {
            if (observation == EggEscrowItemObservation.MATCHING_ITEM_PRESENT) return review(transaction, "applied token and matching item coexist");
            if (observation == EggEscrowItemObservation.AMBIGUOUS) return review(transaction, "applied token inventory is ambiguous");
            return commit(transaction);
        }
        if (observation != EggEscrowItemObservation.MATCHING_ITEM_ABSENT) {
            return review(transaction, "ITEM_REMOVED does not have a provably absent item");
        }
        return apply(transaction);
    }

    private IncubationItemActionResult apply(IncubationItemActionTransaction transaction) throws IOException {
        IncubationItemActionHatchPort.Snapshot before = hatches.snapshot(transaction.playerId());
        if (!before.matches(transaction.playerId(), transaction.incubationId())) return review(transaction, "incubation changed before apply");
        if (before.applied(transaction.actionToken())) return commit(transaction);
        if (before.actionTokens().size() >= IncubationState.MAX_ACTION_TOKENS) return refund(transaction, "action token capacity reached after removal");
        IncubationItemActionHatchPort.Mutation mutation = transaction.type() == IncubationItemActionType.REDUCE
                ? hatches.reduce(transaction.playerId(), before.revision(), transaction.incubationId(),
                        transaction.effectMillis(), transaction.actionToken())
                : hatches.complete(transaction.playerId(), before.revision(), transaction.incubationId(),
                        transaction.actionToken());
        IncubationItemActionHatchPort.Snapshot after = hatches.snapshot(transaction.playerId());
        if (after.matches(transaction.playerId(), transaction.incubationId()) && after.applied(transaction.actionToken())) {
            return commit(transaction);
        }
        return refund(transaction, "hatch mutation did not durably apply token: " + mutation.status());
    }

    private IncubationItemActionResult recoverRefund(
            IncubationItemActionTransaction transaction,
            IncubationItemActionHatchPort.Snapshot snapshot,
            EggEscrowItemObservation observation) throws IOException {
        if (snapshot.applied(transaction.actionToken())) {
            if (observation != EggEscrowItemObservation.MATCHING_ITEM_ABSENT) return review(transaction, "applied token refund state is ambiguous");
            return commit(transaction);
        }
        if (observation == EggEscrowItemObservation.AMBIGUOUS) return review(transaction, "refund inventory is ambiguous");
        if (observation == EggEscrowItemObservation.MATCHING_ITEM_PRESENT) {
            return refunded(actions.markRefunded(transaction.actionToken()));
        }
        EggInventoryMutationResult refund = inventory.refundOne(transaction.playerId(), transaction.item());
        if (refund == EggInventoryMutationResult.REFUNDED || refund == EggInventoryMutationResult.ALREADY_PRESENT) {
            return refunded(actions.markRefunded(transaction.actionToken()));
        }
        if (refund == EggInventoryMutationResult.AMBIGUOUS) return review(transaction, "refund mutation is ambiguous");
        return result(IncubationItemActionResult.Status.PENDING, transaction, "refund remains pending: " + refund);
    }

    private IncubationItemActionResult refund(IncubationItemActionTransaction transaction, String reason) throws IOException {
        IncubationItemActionTransaction pending = actions.markRefundPending(transaction.actionToken());
        return recoverRefund(pending, hatches.snapshot(transaction.playerId()),
                inventory.observe(transaction.playerId(), transaction.item()));
    }

    private IncubationItemActionResult commit(IncubationItemActionTransaction transaction) throws IOException {
        IncubationItemActionTransaction committed = actions.commit(transaction.actionToken());
        if (committed.stage() != IncubationItemActionStage.COMMITTED) return review(committed, "commit transition was rejected");
        return result(IncubationItemActionResult.Status.COMMITTED, committed, "committed");
    }

    private IncubationItemActionResult refunded(IncubationItemActionTransaction transaction) {
        return result(IncubationItemActionResult.Status.REFUNDED, transaction, "refunded");
    }

    private IncubationItemActionResult review(IncubationItemActionTransaction transaction, String detail) throws IOException {
        return result(IncubationItemActionResult.Status.OPERATOR_REVIEW,
                actions.requireOperatorReview(transaction.actionToken()), detail);
    }

    private static IncubationItemActionResult result(
            IncubationItemActionResult.Status status,
            IncubationItemActionTransaction transaction,
            String detail) {
        return new IncubationItemActionResult(status, transaction, detail);
    }

    private void assertMainThread() {
        if (!inventory.isMainThread()) throw new IllegalStateException("incubation item action must run on the main thread");
    }
}
