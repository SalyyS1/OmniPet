package io.github.salyvn.omnipet.core.economy;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

import io.github.salyvn.omnipet.core.domain.PlayerState;
import io.github.salyvn.omnipet.core.persistence.PlayerStateRepository;

final class SlotPurchaseRecovery {
    private final PlayerStateRepository playerStates;
    private final SlotPurchaseSagaSupport support;

    SlotPurchaseRecovery(PlayerStateRepository playerStates, SlotPurchaseSagaSupport support) {
        this.playerStates = playerStates;
        this.support = support;
    }

    SlotPurchaseResult recover(UUID transactionId, PreparedPurchase preparedPurchase) throws IOException {
        Optional<SlotPurchaseTransaction> found = support.find(transactionId);
        if (found.isEmpty()) return result(SlotPurchaseResult.Status.TRANSACTION_NOT_FOUND, null, "transaction was not found");
        SlotPurchaseTransaction transaction = found.get();
        SlotPurchaseResult completed = completeIfEntitled(transaction, playerStates.snapshot(transaction.playerId()));
        if (completed != null) return completed;
        return switch (transaction.state()) {
            case PREPARED -> preparedPurchase.purchase(
                    transactionId,
                    SlotPurchaseSagaSupport.quoteFrom(transaction),
                    transaction.externalEntitlementRequired());
            case EXTERNAL_PENDING -> recoverExternalPending(transaction);
            case REFUND_PENDING -> recoverRefundPending(transaction);
            case ENTITLEMENT_PERSISTED, ENTITLEMENT_SYNC_PENDING, COMPLETED ->
                    markUnknown(transaction, "journal completion has no matching entitlement");
            case FAILED -> transaction.withdrawal() != null && transaction.withdrawal().provenSuccess()
                    ? refund(transaction, "retrying a proven failed refund")
                    : resultForState(transaction);
            default -> resultForState(transaction);
        };
    }

    SlotPurchaseResult resolveKnown(SlotPurchaseTransaction transaction) throws IOException {
        return resolveKnown(transaction, playerStates.snapshot(transaction.playerId()));
    }

    /**
     * Resolves a journaled transaction against a caller-supplied player state. Callers already
     * holding the player lock must use this overload: re-reading through the repository under the
     * same lock would acquire it twice, which the lock registry refuses.
     */
    SlotPurchaseResult resolveKnown(SlotPurchaseTransaction transaction, PlayerState currentState) throws IOException {
        SlotPurchaseResult completed = completeIfEntitled(transaction, currentState);
        if (completed != null) return completed;
        if (transaction.state() == SlotPurchaseSagaState.PREPARED) return null;
        if (transaction.state() == SlotPurchaseSagaState.ENTITLEMENT_PERSISTED
                || transaction.state() == SlotPurchaseSagaState.ENTITLEMENT_SYNC_PENDING
                || transaction.state() == SlotPurchaseSagaState.COMPLETED) {
            return markUnknown(transaction, "journal completion has no matching entitlement");
        }
        return resultForState(transaction);
    }

    SlotPurchaseResult resolveAfterStale(
            UUID transactionId,
            SlotPurchaseQuote quote) throws IOException {
        Optional<SlotPurchaseTransaction> found = support.find(transactionId);
        if (found.isEmpty()) return result(SlotPurchaseResult.Status.STALE_QUOTE, null, "quote revision is stale");
        if (!found.get().matches(quote)) {
            return result(SlotPurchaseResult.Status.INVALID_TRANSACTION, found.get(), "transaction payload differs");
        }
        SlotPurchaseResult resolved = resolveKnown(found.get());
        return resolved == null ? result(SlotPurchaseResult.Status.STALE_QUOTE, found.get(), "quote revision is stale") : resolved;
    }

    SlotPurchaseResult refundAfterPersistenceFailure(SlotPurchaseTransaction transaction, String detail) throws IOException {
        if (transaction == null) throw new IOException(detail + " before the transaction was journaled");
        try {
            SlotPurchaseResult completed = completeIfEntitled(transaction, playerStates.snapshot(transaction.playerId()));
            if (completed != null) return completed;
        } catch (IOException verificationFailure) {
            return markUnknown(transaction, "persistence result could not be verified; refund not safe");
        }
        return refund(transaction, detail);
    }

    SlotPurchaseResult completePersisted(SlotPurchaseTransaction transaction) throws IOException {
        SlotPurchaseTransaction persisted = transaction.withState(
                SlotPurchaseSagaState.ENTITLEMENT_PERSISTED,
                transaction.withdrawal(),
                transaction.refund(),
                "slot entitlement persisted");
        support.save(persisted);
        if (persisted.externalEntitlementRequired()) {
            SlotPurchaseTransaction pending = persisted.withState(
                    SlotPurchaseSagaState.ENTITLEMENT_SYNC_PENDING,
                    persisted.withdrawal(),
                    persisted.refund(),
                    "external entitlement synchronization pending");
            support.save(pending);
            return result(
                    SlotPurchaseResult.Status.ENTITLEMENT_SYNC_PENDING,
                    pending,
                    pending.detail());
        }
        SlotPurchaseTransaction completed = persisted.withState(
                SlotPurchaseSagaState.COMPLETED,
                persisted.withdrawal(),
                persisted.refund(),
                "slot purchase completed");
        support.save(completed);
        return result(SlotPurchaseResult.Status.COMPLETED, completed, completed.detail());
    }

    private SlotPurchaseResult recoverExternalPending(SlotPurchaseTransaction transaction) throws IOException {
        EconomyOperationResult withdrawal = transaction.withdrawal();
        if (withdrawal == null || withdrawal.ambiguous()) {
            return markUnknown(transaction, "withdrawal commit cannot be proven");
        }
        if (withdrawal.provenSuccess()) return refund(transaction, "recovering proven withdrawal without entitlement");
        SlotPurchaseTransaction failed = transaction.withState(SlotPurchaseSagaState.FAILED, withdrawal, null, withdrawal.evidence());
        support.save(failed);
        return resultForState(failed);
    }

    private SlotPurchaseResult refund(SlotPurchaseTransaction transaction, String detail) throws IOException {
        SlotPurchaseTransaction pending = transaction.withState(
                SlotPurchaseSagaState.REFUND_PENDING,
                transaction.withdrawal(),
                null,
                "refund call pending");
        support.save(pending);
        EconomyOperationResult refund = support.refund(pending);
        SlotPurchaseSagaState state = refund.provenSuccess()
                ? SlotPurchaseSagaState.REFUNDED
                : refund.ambiguous()
                        ? SlotPurchaseSagaState.UNKNOWN_REQUIRES_RECONCILIATION
                        : SlotPurchaseSagaState.REFUND_PENDING;
        SlotPurchaseTransaction updated = pending.withState(
                state,
                pending.withdrawal(),
                refund,
                SlotPurchaseSagaSupport.limitedDetail(detail + ": " + refund.evidence()));
        support.save(updated);
        if (refund.provenSuccess()) return result(SlotPurchaseResult.Status.PERSISTENCE_FAILED_REFUNDED, updated, updated.detail());
        if (refund.ambiguous()) return result(SlotPurchaseResult.Status.UNKNOWN_REQUIRES_RECONCILIATION, updated, updated.detail());
        return result(SlotPurchaseResult.Status.REFUND_FAILED_REQUIRES_RECOVERY, updated, updated.detail());
    }

    private SlotPurchaseResult completeIfEntitled(SlotPurchaseTransaction transaction, PlayerState state) throws IOException {
        if (!SlotPurchaseSagaSupport.hasTransactionEntitlement(state, transaction.transactionId())) return null;
        if (transaction.state() == SlotPurchaseSagaState.ENTITLEMENT_SYNC_PENDING
                || transaction.state() == SlotPurchaseSagaState.COMPLETED) {
            return resultForState(transaction);
        }
        return completePersisted(transaction);
    }

    private SlotPurchaseResult recoverRefundPending(SlotPurchaseTransaction transaction) throws IOException {
        EconomyOperationResult refund = transaction.refund();
        if (refund == null || refund.ambiguous()) {
            return markUnknown(transaction, "refund commit cannot be proven");
        }
        if (refund.provenSuccess()) {
            SlotPurchaseTransaction refunded = transaction.withState(
                    SlotPurchaseSagaState.REFUNDED,
                    transaction.withdrawal(),
                    refund,
                    refund.evidence());
            support.save(refunded);
            return result(SlotPurchaseResult.Status.PERSISTENCE_FAILED_REFUNDED, refunded, refunded.detail());
        }
        return refund(transaction, "retrying a proven incomplete refund");
    }

    private SlotPurchaseResult markUnknown(SlotPurchaseTransaction transaction, String detail) throws IOException {
        SlotPurchaseTransaction unknown = transaction.withState(
                SlotPurchaseSagaState.UNKNOWN_REQUIRES_RECONCILIATION,
                transaction.withdrawal(),
                transaction.refund(),
                detail);
        support.save(unknown);
        return result(SlotPurchaseResult.Status.UNKNOWN_REQUIRES_RECONCILIATION, unknown, detail);
    }

    private static SlotPurchaseResult resultForState(SlotPurchaseTransaction transaction) {
        SlotPurchaseResult.Status status = switch (transaction.state()) {
            case COMPLETED -> SlotPurchaseResult.Status.COMPLETED;
            case PREPARED, ENTITLEMENT_PERSISTED -> SlotPurchaseResult.Status.IN_PROGRESS;
            case ENTITLEMENT_SYNC_PENDING -> SlotPurchaseResult.Status.ENTITLEMENT_SYNC_PENDING;
            case EXTERNAL_PENDING -> transaction.withdrawal() == null || !transaction.withdrawal().ambiguous()
                    ? SlotPurchaseResult.Status.IN_PROGRESS
                    : SlotPurchaseResult.Status.UNKNOWN_REQUIRES_RECONCILIATION;
            case REFUND_PENDING -> transaction.refund() != null && !transaction.refund().ambiguous()
                    ? SlotPurchaseResult.Status.REFUND_FAILED_REQUIRES_RECOVERY
                    : SlotPurchaseResult.Status.UNKNOWN_REQUIRES_RECONCILIATION;
            case UNKNOWN_REQUIRES_RECONCILIATION -> SlotPurchaseResult.Status.UNKNOWN_REQUIRES_RECONCILIATION;
            case REFUNDED -> SlotPurchaseResult.Status.PERSISTENCE_FAILED_REFUNDED;
            case FAILED -> failureStatus(transaction);
        };
        return result(status, transaction, transaction.detail());
    }

    private static SlotPurchaseResult.Status failureStatus(SlotPurchaseTransaction transaction) {
        if (transaction.withdrawal() == null) return SlotPurchaseResult.Status.FAILED;
        if (transaction.withdrawal().provenSuccess()) return SlotPurchaseResult.Status.REFUND_FAILED_REQUIRES_RECOVERY;
        return transaction.withdrawal().status() == EconomyOperationResult.Status.UNAVAILABLE
                ? SlotPurchaseResult.Status.PROVIDER_UNAVAILABLE
                : SlotPurchaseResult.Status.WITHDRAWAL_FAILED;
    }

    private static SlotPurchaseResult result(
            SlotPurchaseResult.Status status,
            SlotPurchaseTransaction transaction,
            String detail) {
        return SlotPurchaseSagaSupport.result(status, transaction, detail);
    }

    @FunctionalInterface
    interface PreparedPurchase {
        SlotPurchaseResult purchase(
                UUID transactionId,
                SlotPurchaseQuote quote,
                boolean externalEntitlementRequired) throws IOException;
    }
}
