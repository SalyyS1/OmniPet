package io.github.salyvn.omnipet.core.economy;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

import io.github.salyvn.omnipet.core.domain.PlayerState;
import io.github.salyvn.omnipet.core.persistence.PlayerStateRepository;
import io.github.salyvn.omnipet.core.persistence.StaleRevisionException;

public final class SlotUnlockService {
    private static final int TRANSACTION_LOCK_STRIPES = 64;

    private final PlayerStateRepository playerStates;
    private final SlotPurchaseSagaSupport support;
    private final SlotPurchaseRecovery recovery;
    private final ReentrantLock[] transactionLocks = new ReentrantLock[TRANSACTION_LOCK_STRIPES];

    public SlotUnlockService(
            PlayerStateRepository playerStates,
            PurchaseJournal journal,
            Map<EconomyProvider, EconomyPort> ports) {
        this.playerStates = Objects.requireNonNull(playerStates, "player state repository");
        this.support = new SlotPurchaseSagaSupport(journal, ports);
        this.recovery = new SlotPurchaseRecovery(playerStates, support);
        for (int index = 0; index < transactionLocks.length; index++) {
            transactionLocks[index] = new ReentrantLock();
        }
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
        Objects.requireNonNull(transactionId, "transaction id");
        Objects.requireNonNull(quote, "purchase quote");
        ReentrantLock lock = transactionLock(transactionId);
        lock.lock();
        try {
            return purchaseLocked(transactionId, quote);
        } finally {
            lock.unlock();
        }
    }

    private SlotPurchaseResult purchaseLocked(UUID transactionId, SlotPurchaseQuote quote) throws IOException {
        Optional<SlotPurchaseTransaction> known = support.find(transactionId);
        if (known.isPresent()) {
            if (!known.get().matches(quote)) return result(SlotPurchaseResult.Status.INVALID_TRANSACTION, known.get(), "transaction payload differs");
            SlotPurchaseResult resolved = recovery.resolveKnown(known.get());
            if (resolved != null) return resolved;
        }

        AtomicReference<SlotPurchaseTransaction> transaction = new AtomicReference<>();
        AtomicReference<EconomyOperationResult> withdrawal = new AtomicReference<>();
        try {
            playerStates.withLocked(quote.playerId(), quote.expectedRevision(), current ->
                    executeLocked(transactionId, quote, current, transaction, withdrawal));
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
        ReentrantLock lock = transactionLock(transactionId);
        lock.lock();
        try {
            return recovery.recover(transactionId, this::purchase);
        } finally {
            lock.unlock();
        }
    }

    private PlayerState executeLocked(
            UUID transactionId,
            SlotPurchaseQuote quote,
            PlayerState current,
            AtomicReference<SlotPurchaseTransaction> transaction,
            AtomicReference<EconomyOperationResult> withdrawal) {
        if (SlotPurchaseSagaSupport.hasTransactionEntitlement(current, transactionId)) {
            SlotPurchaseTransaction completed = support.createUnchecked(SlotPurchaseTransaction.prepared(transactionId, quote))
                    .withState(SlotPurchaseSagaState.COMPLETED, null, null, "entitlement already persisted");
            support.saveUnchecked(completed);
            throw SlotPurchaseSagaSupport.abort(SlotPurchaseResult.Status.COMPLETED, completed, "entitlement already persisted");
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

        SlotPurchaseTransaction prepared = support.createUnchecked(SlotPurchaseTransaction.prepared(transactionId, quote));
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

    private ReentrantLock transactionLock(UUID transactionId) {
        int stripe = Math.floorMod(transactionId.hashCode(), transactionLocks.length);
        return transactionLocks[stripe];
    }

    private static SlotPurchaseResult result(
            SlotPurchaseResult.Status status,
            SlotPurchaseTransaction transaction,
            String detail) {
        return SlotPurchaseSagaSupport.result(status, transaction, detail);
    }
}
