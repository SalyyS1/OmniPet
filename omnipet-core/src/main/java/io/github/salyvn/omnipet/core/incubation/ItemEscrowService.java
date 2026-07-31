package io.github.salyvn.omnipet.core.incubation;

import java.io.IOException;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class ItemEscrowService {
    private final EggEscrowJournal journal;

    public ItemEscrowService(EggEscrowJournal journal) {
        this.journal = Objects.requireNonNull(journal, "egg escrow journal");
    }

    public ItemEscrowResult prepare(EggEscrowTransaction transaction) throws IOException {
        if (transaction == null) throw new IllegalArgumentException("egg escrow transaction is required");
        if (transaction.stage() != EggEscrowStage.PREPARED) {
            throw new IllegalArgumentException("new egg escrow transaction must be PREPARED");
        }
        EggEscrowCreateResult result = journal.create(transaction);
        ItemEscrowResult.Status status = result.status() == EggEscrowCreateResult.Status.CREATED
                ? ItemEscrowResult.Status.CREATED
                : ItemEscrowResult.Status.ALREADY_EXISTS;
        return new ItemEscrowResult(status, result.transaction());
    }

    public ItemEscrowResult markItemRemoved(UUID transactionId) throws IOException {
        return transition(transactionId, EggEscrowStage.ITEM_REMOVED, ItemEscrowResult.Status.ITEM_REMOVED,
                EggEscrowStage.PREPARED);
    }

    public ItemEscrowResult commit(UUID transactionId) throws IOException {
        return transition(transactionId, EggEscrowStage.COMMITTED, ItemEscrowResult.Status.COMMITTED,
                EggEscrowStage.ITEM_REMOVED);
    }

    public ItemEscrowResult cancel(UUID transactionId) throws IOException {
        return transition(transactionId, EggEscrowStage.CANCELLED, ItemEscrowResult.Status.CANCELLED,
                EggEscrowStage.PREPARED);
    }

    public ItemEscrowResult markRefundPending(UUID transactionId) throws IOException {
        return transition(transactionId, EggEscrowStage.REFUND_PENDING, ItemEscrowResult.Status.REFUND_PENDING,
                EggEscrowStage.ITEM_REMOVED);
    }

    public ItemEscrowResult markRefunded(UUID transactionId) throws IOException {
        return transition(transactionId, EggEscrowStage.REFUNDED, ItemEscrowResult.Status.REFUNDED,
                EggEscrowStage.REFUND_PENDING);
    }

    public ItemEscrowResult fail(UUID transactionId) throws IOException {
        return transition(transactionId, EggEscrowStage.FAILED, ItemEscrowResult.Status.FAILED,
                EggEscrowStage.PREPARED, EggEscrowStage.ITEM_REMOVED);
    }

    private ItemEscrowResult transition(
            UUID transactionId,
            EggEscrowStage target,
            ItemEscrowResult.Status status,
            EggEscrowStage... allowed) throws IOException {
        EggEscrowTransitionResult result = journal.transition(transactionId, Set.of(allowed), target);
        ItemEscrowResult.Status mapped = result.status() == EggEscrowTransitionResult.Status.REJECTED
                ? ItemEscrowResult.Status.INVALID_TRANSITION
                : status;
        return new ItemEscrowResult(mapped, result.transaction());
    }
}
