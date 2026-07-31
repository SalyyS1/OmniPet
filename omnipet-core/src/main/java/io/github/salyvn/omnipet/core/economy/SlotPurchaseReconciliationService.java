package io.github.salyvn.omnipet.core.economy;

import java.io.IOException;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import io.github.salyvn.omnipet.core.domain.PlayerState;
import io.github.salyvn.omnipet.core.persistence.PlayerStateRepository;
import io.github.salyvn.omnipet.core.persistence.StaleRevisionException;

/** Lists ambiguous transactions and applies only explicit, audited operator decisions. */
public final class SlotPurchaseReconciliationService {
    private static final Set<SlotPurchaseSagaState> ACTIONABLE_STATES = Set.of(
            SlotPurchaseSagaState.EXTERNAL_PENDING,
            SlotPurchaseSagaState.ENTITLEMENT_PERSISTED,
            SlotPurchaseSagaState.ENTITLEMENT_SYNC_PENDING,
            SlotPurchaseSagaState.REFUND_PENDING,
            SlotPurchaseSagaState.UNKNOWN_REQUIRES_RECONCILIATION);
    private final PlayerStateRepository playerStates;
    private final PurchaseJournal journal;
    private final PurchaseTransactionCoordinator transactions;

    public SlotPurchaseReconciliationService(
            PlayerStateRepository playerStates,
            PurchaseJournal journal) {
        this(playerStates, journal, PurchaseTransactionCoordinator.shared());
    }

    public SlotPurchaseReconciliationService(
            PlayerStateRepository playerStates,
            PurchaseJournal journal,
            PurchaseTransactionCoordinator transactions) {
        this.playerStates = Objects.requireNonNull(playerStates, "player state repository");
        this.journal = Objects.requireNonNull(journal, "purchase journal");
        this.transactions = Objects.requireNonNull(transactions, "purchase transaction coordinator");
    }

    public PurchaseJournalScanResult pending(int limit) throws IOException {
        return pending(limit, null);
    }

    public PurchaseJournalScanResult pending(int limit, String cursor) throws IOException {
        return journal.scan(ACTIONABLE_STATES, limit, cursor);
    }

    public SlotPurchaseResult reconcile(
            UUID transactionId,
            SlotReconciliationDecision decision,
            String actor) throws IOException {
        Objects.requireNonNull(transactionId, "transaction id");
        Objects.requireNonNull(decision, "reconciliation decision");
        String auditActor = requireActor(actor);
        try (var ignored = transactions.acquire(transactionId)) {
            SlotPurchaseTransaction transaction = journal.find(transactionId).orElse(null);
            if (transaction == null) {
                return new SlotPurchaseResult(
                        SlotPurchaseResult.Status.TRANSACTION_NOT_FOUND,
                        null,
                        "transaction was not found");
            }
            if (!ACTIONABLE_STATES.contains(transaction.state())) {
                return invalid(transaction, "transaction is not awaiting reconciliation");
            }
            if ((transaction.state() == SlotPurchaseSagaState.ENTITLEMENT_PERSISTED
                            || transaction.state() == SlotPurchaseSagaState.ENTITLEMENT_SYNC_PENDING)
                    && decision != SlotReconciliationDecision.ENTITLEMENT_SYNC_RETRY) {
                return invalid(transaction, "external entitlement synchronization requires the sync decision");
            }
            return switch (decision) {
                case CHARGE_CONFIRMED -> confirmCharge(transaction, auditActor);
                case NO_CHARGE_CONFIRMED -> confirmNoCharge(transaction, auditActor);
                case REFUND_CONFIRMED -> confirmRefund(transaction, auditActor);
                case ENTITLEMENT_SYNC_RETRY -> invalid(
                        transaction,
                        "external entitlement synchronization is handled by the platform adapter");
            };
        }
    }

    private SlotPurchaseResult confirmCharge(
            SlotPurchaseTransaction transaction,
            String actor) throws IOException {
        if (transaction.state() == SlotPurchaseSagaState.REFUND_PENDING
                || transaction.refund() != null) {
            return invalid(transaction, "refund outcome must be resolved before granting the slot");
        }
        if (transaction.withdrawal() != null
                && (transaction.withdrawal().status() == EconomyOperationResult.Status.PROVEN_FAILURE
                        || transaction.withdrawal().status() == EconomyOperationResult.Status.UNAVAILABLE)) {
            return invalid(transaction, "cannot override evidence that no withdrawal completed");
        }
        for (int attempt = 0; attempt < 2; attempt++) {
            PlayerState snapshot = playerStates.snapshot(transaction.playerId());
            if (SlotPurchaseSagaSupport.hasTransactionEntitlement(snapshot, transaction.transactionId())) {
                return complete(transaction, actor, "existing entitlement confirmed");
            }
            if (SlotPurchaseSagaSupport.hasSlotEntitlement(snapshot, transaction.slot())) {
                return invalid(transaction, "slot is already owned by a different transaction");
            }
            if (snapshot.activeSlotCount() + 1 != transaction.slot()) {
                return invalid(transaction, "transaction is no longer for the next active slot");
            }
            try {
                playerStates.withLocked(
                        transaction.playerId(),
                        snapshot.revision(),
                        current -> SlotPurchaseSagaSupport.grant(current, transaction));
                return complete(transaction, actor, "provider charge confirmed");
            } catch (StaleRevisionException stale) {
                if (attempt == 1) throw stale;
            }
        }
        throw new IOException("slot reconciliation did not converge");
    }

    private SlotPurchaseResult confirmNoCharge(
            SlotPurchaseTransaction transaction,
            String actor) throws IOException {
        PlayerState state = playerStates.snapshot(transaction.playerId());
        if (SlotPurchaseSagaSupport.hasTransactionEntitlement(state, transaction.transactionId())) {
            return invalid(transaction, "transaction entitlement already exists");
        }
        if (transaction.withdrawal() != null && transaction.withdrawal().provenSuccess()) {
            return invalid(transaction, "cannot mark a proven successful withdrawal as not charged");
        }
        if (transaction.refund() != null) {
            return invalid(transaction, "refund evidence already exists");
        }
        EconomyOperationResult withdrawal = EconomyOperationResult.failed(audit("no provider charge", actor));
        SlotPurchaseTransaction failed = transaction.withState(
                SlotPurchaseSagaState.FAILED,
                withdrawal,
                transaction.refund(),
                withdrawal.evidence());
        journal.save(failed);
        return new SlotPurchaseResult(SlotPurchaseResult.Status.WITHDRAWAL_FAILED, failed, failed.detail());
    }

    private SlotPurchaseResult confirmRefund(
            SlotPurchaseTransaction transaction,
            String actor) throws IOException {
        PlayerState state = playerStates.snapshot(transaction.playerId());
        if (SlotPurchaseSagaSupport.hasTransactionEntitlement(state, transaction.transactionId())) {
            return invalid(transaction, "cannot confirm a refund while the entitlement exists");
        }
        if (transaction.withdrawal() != null
                && (transaction.withdrawal().status() == EconomyOperationResult.Status.PROVEN_FAILURE
                        || transaction.withdrawal().status() == EconomyOperationResult.Status.UNAVAILABLE)) {
            return invalid(transaction, "cannot refund when no withdrawal completed");
        }
        EconomyOperationResult refund = EconomyOperationResult.succeeded(audit("provider refund confirmed", actor));
        SlotPurchaseTransaction refunded = transaction.withState(
                SlotPurchaseSagaState.REFUNDED,
                transaction.withdrawal(),
                refund,
                refund.evidence());
        journal.save(refunded);
        return new SlotPurchaseResult(
                SlotPurchaseResult.Status.PERSISTENCE_FAILED_REFUNDED,
                refunded,
                refunded.detail());
    }

    private SlotPurchaseResult complete(
            SlotPurchaseTransaction transaction,
            String actor,
            String reason) throws IOException {
        EconomyOperationResult withdrawal = EconomyOperationResult.succeeded(audit(reason, actor));
        SlotPurchaseSagaState nextState = transaction.externalEntitlementRequired()
                ? SlotPurchaseSagaState.ENTITLEMENT_SYNC_PENDING
                : SlotPurchaseSagaState.COMPLETED;
        SlotPurchaseTransaction completed = transaction.withState(
                nextState,
                withdrawal,
                transaction.refund(),
                withdrawal.evidence());
        journal.save(completed);
        return new SlotPurchaseResult(
                transaction.externalEntitlementRequired()
                        ? SlotPurchaseResult.Status.ENTITLEMENT_SYNC_PENDING
                        : SlotPurchaseResult.Status.COMPLETED,
                completed,
                completed.detail());
    }

    private static SlotPurchaseResult invalid(SlotPurchaseTransaction transaction, String detail) {
        return new SlotPurchaseResult(SlotPurchaseResult.Status.INVALID_TRANSACTION, transaction, detail);
    }

    private static String requireActor(String actor) {
        if (actor == null || actor.isBlank() || actor.length() > 64 || actor.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("reconciliation actor must be printable text up to 64 characters");
        }
        return actor;
    }

    private static String audit(String reason, String actor) {
        return reason + " by " + actor;
    }
}
