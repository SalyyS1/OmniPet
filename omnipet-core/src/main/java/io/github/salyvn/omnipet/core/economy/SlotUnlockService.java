package io.github.salyvn.omnipet.core.economy;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import io.github.salyvn.omnipet.core.domain.PlayerState;
import io.github.salyvn.omnipet.core.persistence.PlayerStateRepository;
import io.github.salyvn.omnipet.core.persistence.StaleRevisionException;

public final class SlotUnlockService {
    private final PlayerStateRepository playerStates;
    private final SlotPurchaseSagaSupport support;
    private final SlotPurchaseRecovery recovery;
    private final PurchaseTransactionCoordinator transactions;

    public SlotUnlockService(
            PlayerStateRepository playerStates,
            PurchaseJournal journal,
            Map<EconomyProvider, EconomyPort> ports) {
        this(playerStates, journal, EconomyPortResolver.fixed(ports));
    }

    public SlotUnlockService(
            PlayerStateRepository playerStates,
            PurchaseJournal journal,
            EconomyPortResolver ports) {
        this(playerStates, journal, ports, PurchaseTransactionCoordinator.shared());
    }

    public SlotUnlockService(
            PlayerStateRepository playerStates,
            PurchaseJournal journal,
            EconomyPortResolver ports,
            PurchaseTransactionCoordinator transactions) {
        this.playerStates = Objects.requireNonNull(playerStates, "player state repository");
        this.support = new SlotPurchaseSagaSupport(journal, ports);
        this.recovery = new SlotPurchaseRecovery(playerStates, support);
        this.transactions = Objects.requireNonNull(transactions, "purchase transaction coordinator");
    }

    public SlotQuoteResult quote(UUID playerId, SlotUnlockRule rule) throws IOException {
        Objects.requireNonNull(playerId, "player id");
        Objects.requireNonNull(rule, "slot unlock rule");
        PlayerState state = playerStates.snapshot(playerId);
        SlotPurchaseQuote quote = new SlotPurchaseQuote(playerId, state.revision(), rule);
        if (SlotPurchaseSagaSupport.hasSlotEntitlement(state, rule.slot())) {
            return new SlotQuoteResult(SlotQuoteResult.Status.ALREADY_ENTITLED, quote, "slot already has an entitlement");
        }
        if (rule.slot() != state.activeSlotCount() + 1) {
            return new SlotQuoteResult(SlotQuoteResult.Status.NOT_NEXT_SLOT, quote, "only the next active slot can be unlocked");
        }
        if (!support.hasProvider(rule.amount().provider())) {
            return new SlotQuoteResult(SlotQuoteResult.Status.PROVIDER_UNAVAILABLE, quote, "configured economy provider is unavailable");
        }
        return new SlotQuoteResult(SlotQuoteResult.Status.AVAILABLE, quote, "");
    }

    public SlotPurchaseResult purchase(UUID transactionId, SlotPurchaseQuote quote) throws IOException {
        return purchase(transactionId, quote, false);
    }

    public SlotPurchaseResult purchase(
            UUID transactionId,
            SlotPurchaseQuote quote,
            boolean externalEntitlementRequired) throws IOException {
        Objects.requireNonNull(transactionId, "transaction id");
        Objects.requireNonNull(quote, "purchase quote");
        try (var ignored = transactions.acquire(transactionId)) {
            return purchaseLocked(transactionId, quote, externalEntitlementRequired);
        }
    }

    private SlotPurchaseResult purchaseLocked(
            UUID transactionId,
            SlotPurchaseQuote quote,
            boolean externalEntitlementRequired) throws IOException {
        Optional<SlotPurchaseTransaction> known = support.find(transactionId);
        if (known.isPresent()) {
            if (!known.get().matches(quote)) {
                return result(SlotPurchaseResult.Status.INVALID_TRANSACTION, known.get(), "transaction payload differs");
            }
            SlotPurchaseResult resolved = recovery.resolveKnown(known.get());
            if (resolved != null) return resolved;
        }

        AtomicReference<SlotPurchaseTransaction> transaction = new AtomicReference<>();
        AtomicReference<EconomyOperationResult> withdrawal = new AtomicReference<>();
        try {
            playerStates.withLocked(quote.playerId(), quote.expectedRevision(), current ->
                    executeLocked(
                            transactionId,
                            quote,
                            externalEntitlementRequired,
                            current,
                            transaction,
                            withdrawal));
        } catch (SlotPurchaseSagaSupport.PurchaseAbort aborted) {
            return aborted.result;
        } catch (StaleRevisionException stale) {
            return recovery.resolveAfterStale(transactionId, quote);
        } catch (SlotPurchaseSagaSupport.JournalAccessException access) {
            if (wasWithdrawn(withdrawal)) {
                return recovery.refundAfterPersistenceFailure(transaction.get(), "journal write failed after withdrawal");
            }
            throw access.failure;
        } catch (IOException failure) {
            if (wasWithdrawn(withdrawal)) {
                return recovery.refundAfterPersistenceFailure(transaction.get(), "player state persistence failed");
            }
            throw failure;
        } catch (RuntimeException failure) {
            if (wasWithdrawn(withdrawal)) {
                return recovery.refundAfterPersistenceFailure(transaction.get(), "slot entitlement mutation failed");
            }
            throw failure;
        }
        return recovery.completePersisted(transaction.get());
    }

    public SlotPurchaseResult recover(UUID transactionId) throws IOException {
        Objects.requireNonNull(transactionId, "transaction id");
        try (var ignored = transactions.acquire(transactionId)) {
            return recovery.recover(transactionId, this::purchase);
        }
    }

    public SlotPurchaseResult synchronizeExternalEntitlement(
            UUID transactionId,
            Function<SlotPurchaseTransaction, ExternalEntitlementResult> operation) throws IOException {
        Objects.requireNonNull(transactionId, "transaction id");
        Objects.requireNonNull(operation, "external entitlement operation");
        try (var ignored = transactions.acquire(transactionId)) {
            SlotPurchaseTransaction transaction = support.find(transactionId).orElse(null);
            if (transaction == null) {
                return result(SlotPurchaseResult.Status.TRANSACTION_NOT_FOUND, null, "transaction was not found");
            }
            if (transaction.state() == SlotPurchaseSagaState.COMPLETED) {
                return result(SlotPurchaseResult.Status.COMPLETED, transaction, transaction.detail());
            }
            if (transaction.state() != SlotPurchaseSagaState.ENTITLEMENT_PERSISTED
                    && transaction.state() != SlotPurchaseSagaState.ENTITLEMENT_SYNC_PENDING) {
                return result(
                        SlotPurchaseResult.Status.INVALID_TRANSACTION,
                        transaction,
                        "transaction is not awaiting external entitlement synchronization");
            }
            PlayerState state = playerStates.snapshot(transaction.playerId());
            if (!SlotPurchaseSagaSupport.hasTransactionEntitlement(state, transaction.transactionId())) {
                SlotPurchaseTransaction unknown = transaction.withState(
                        SlotPurchaseSagaState.UNKNOWN_REQUIRES_RECONCILIATION,
                        transaction.withdrawal(),
                        transaction.refund(),
                        "external entitlement pending without the persisted OmniPet entitlement");
                support.save(unknown);
                return result(
                        SlotPurchaseResult.Status.UNKNOWN_REQUIRES_RECONCILIATION,
                        unknown,
                        unknown.detail());
            }
            if (!transaction.externalEntitlementRequired()) {
                SlotPurchaseTransaction completed = transaction.withState(
                        SlotPurchaseSagaState.COMPLETED,
                        transaction.withdrawal(),
                        transaction.refund(),
                        "OmniPet entitlement completed without an external provider");
                support.save(completed);
                return result(SlotPurchaseResult.Status.COMPLETED, completed, completed.detail());
            }
            if (transaction.state() == SlotPurchaseSagaState.ENTITLEMENT_PERSISTED) {
                transaction = transaction.withState(
                        SlotPurchaseSagaState.ENTITLEMENT_SYNC_PENDING,
                        transaction.withdrawal(),
                        transaction.refund(),
                        "external entitlement synchronization pending");
                support.save(transaction);
            }
            ExternalEntitlementResult external;
            try {
                external = operation.apply(transaction);
                if (external == null) external = ExternalEntitlementResult.pending("provider returned no result");
            } catch (RuntimeException failure) {
                String detail = failure.getMessage() == null
                        ? "external entitlement operation threw"
                        : "external entitlement operation threw: " + failure.getMessage();
                external = ExternalEntitlementResult.pending(SlotPurchaseSagaSupport.limitedDetail(detail));
            }
            SlotPurchaseSagaState nextState = external.succeeded()
                    ? SlotPurchaseSagaState.COMPLETED
                    : SlotPurchaseSagaState.ENTITLEMENT_SYNC_PENDING;
            SlotPurchaseTransaction updated = transaction.withState(
                    nextState,
                    transaction.withdrawal(),
                    transaction.refund(),
                    SlotPurchaseSagaSupport.limitedDetail(external.detail()));
            support.save(updated);
            return result(
                    external.succeeded()
                            ? SlotPurchaseResult.Status.COMPLETED
                            : SlotPurchaseResult.Status.ENTITLEMENT_SYNC_PENDING,
                    updated,
                    updated.detail());
        }
    }

    private PlayerState executeLocked(
            UUID transactionId,
            SlotPurchaseQuote quote,
            boolean externalEntitlementRequired,
            PlayerState current,
            AtomicReference<SlotPurchaseTransaction> transaction,
            AtomicReference<EconomyOperationResult> withdrawal) {
        if (SlotPurchaseSagaSupport.hasTransactionEntitlement(current, transactionId)) {
            SlotPurchaseTransaction existing = support.createUnchecked(
                    SlotPurchaseTransaction.prepared(transactionId, quote, externalEntitlementRequired));
            try {
                throw SlotPurchaseSagaSupport.abort(recovery.completePersisted(existing));
            } catch (IOException failure) {
                throw new SlotPurchaseSagaSupport.JournalAccessException(failure);
            }
        }
        if (quote.rule().slot() != current.activeSlotCount() + 1) {
            throw SlotPurchaseSagaSupport.abort(
                    SlotPurchaseResult.Status.NOT_NEXT_SLOT,
                    null,
                    "only the next active slot can be unlocked");
        }
        if (SlotPurchaseSagaSupport.hasSlotEntitlement(current, quote.rule().slot())) {
            throw SlotPurchaseSagaSupport.abort(SlotPurchaseResult.Status.ALREADY_ENTITLED, null, "slot already has an entitlement");
        }

        SlotPurchaseTransaction prepared = support.createUnchecked(
                SlotPurchaseTransaction.prepared(transactionId, quote, externalEntitlementRequired));
        transaction.set(prepared);
        if (!prepared.matches(quote)) {
            throw SlotPurchaseSagaSupport.abort(SlotPurchaseResult.Status.INVALID_TRANSACTION, prepared, "transaction payload differs");
        }
        if (prepared.state() != SlotPurchaseSagaState.PREPARED) {
            SlotPurchaseResult resolved;
            try {
                resolved = recovery.resolveKnown(prepared);
            } catch (IOException failure) {
                throw new SlotPurchaseSagaSupport.JournalAccessException(failure);
            }
            throw SlotPurchaseSagaSupport.abort(resolved);
        }

        SlotPurchaseTransaction pending = prepared.withState(SlotPurchaseSagaState.EXTERNAL_PENDING, null, null, "withdrawal call pending");
        support.saveUnchecked(pending);
        transaction.set(pending);
        EconomyOperationResult external = support.withdraw(pending);
        withdrawal.set(external);
        SlotPurchaseTransaction observed = pending.withState(
                SlotPurchaseSagaState.EXTERNAL_PENDING, external, null, external.evidence());
        transaction.set(observed);
        support.saveUnchecked(observed);
        return switch (external.status()) {
            case PROVEN_SUCCESS -> SlotPurchaseSagaSupport.grant(current, observed);
            case PROVEN_FAILURE -> abortExternal(observed, external, SlotPurchaseResult.Status.WITHDRAWAL_FAILED, transaction);
            case UNAVAILABLE -> abortExternal(observed, external, SlotPurchaseResult.Status.PROVIDER_UNAVAILABLE, transaction);
            case UNKNOWN_COMMIT -> abortExternal(
                    observed,
                    external,
                    SlotPurchaseResult.Status.UNKNOWN_REQUIRES_RECONCILIATION,
                    transaction);
        };
    }

    private PlayerState abortExternal(
            SlotPurchaseTransaction transaction,
            EconomyOperationResult external,
            SlotPurchaseResult.Status status,
            AtomicReference<SlotPurchaseTransaction> transactionRef) {
        SlotPurchaseSagaState state = external.ambiguous()
                ? SlotPurchaseSagaState.UNKNOWN_REQUIRES_RECONCILIATION
                : SlotPurchaseSagaState.FAILED;
        SlotPurchaseTransaction failed = transaction.withState(state, external, null, external.evidence());
        support.saveUnchecked(failed);
        transactionRef.set(failed);
        throw SlotPurchaseSagaSupport.abort(status, failed, external.evidence());
    }

    private static boolean wasWithdrawn(AtomicReference<EconomyOperationResult> withdrawal) {
        return withdrawal.get() != null && withdrawal.get().provenSuccess();
    }

    private static SlotPurchaseResult result(
            SlotPurchaseResult.Status status,
            SlotPurchaseTransaction transaction,
            String detail) {
        return SlotPurchaseSagaSupport.result(status, transaction, detail);
    }
}
