package io.github.salyvn.omnipet.core.economy;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.UnaryOperator;

import io.github.salyvn.omnipet.core.domain.PlayerState;
import io.github.salyvn.omnipet.core.persistence.PlayerStateRepository;

final class EconomyTestFixtures {
    private EconomyTestFixtures() {}

    static final class InMemoryPurchaseJournal implements PurchaseJournal {
        private final Map<UUID, SlotPurchaseTransaction> transactions = new HashMap<>();

        @Override
        public synchronized Optional<SlotPurchaseTransaction> find(UUID transactionId) {
            return Optional.ofNullable(transactions.get(transactionId));
        }

        @Override
        public synchronized SlotPurchaseTransaction create(SlotPurchaseTransaction transaction) {
            return transactions.computeIfAbsent(transaction.transactionId(), ignored -> transaction);
        }

        @Override
        public synchronized void save(SlotPurchaseTransaction transaction) {
            transactions.put(transaction.transactionId(), transaction);
        }

        @Override
        public synchronized PurchaseJournalScanResult scan(Set<SlotPurchaseSagaState> states, int limit) {
            var matches = transactions.values().stream()
                    .filter(transaction -> states.contains(transaction.state()))
                    .sorted(java.util.Comparator.comparing(transaction -> transaction.transactionId().toString()))
                    .limit(limit)
                    .toList();
            long total = transactions.values().stream().filter(transaction -> states.contains(transaction.state())).count();
            return new PurchaseJournalScanResult(matches, java.util.List.of(), total > matches.size());
        }
    }

    static final class FailObservedWithdrawalOnceJournal implements PurchaseJournal {
        private final InMemoryPurchaseJournal delegate = new InMemoryPurchaseJournal();
        private boolean failed;

        @Override
        public Optional<SlotPurchaseTransaction> find(UUID transactionId) {
            return delegate.find(transactionId);
        }

        @Override
        public SlotPurchaseTransaction create(SlotPurchaseTransaction transaction) {
            return delegate.create(transaction);
        }

        @Override
        public void save(SlotPurchaseTransaction transaction) throws IOException {
            if (!failed
                    && transaction.state() == SlotPurchaseSagaState.EXTERNAL_PENDING
                    && transaction.withdrawal() != null
                    && transaction.withdrawal().provenSuccess()) {
                failed = true;
                throw new IOException("simulated observed-withdrawal journal failure");
            }
            delegate.save(transaction);
        }

        @Override
        public PurchaseJournalScanResult scan(Set<SlotPurchaseSagaState> states, int limit) {
            return delegate.scan(states, limit);
        }
    }

    static final class StubEconomyPort implements EconomyPort {
        private final EconomyProvider provider;
        private final EconomyOperationResult withdrawalResult;
        private final EconomyOperationResult refundResult;
        private final AtomicInteger withdrawals = new AtomicInteger();
        private final AtomicInteger refunds = new AtomicInteger();

        StubEconomyPort(
                EconomyProvider provider,
                EconomyOperationResult withdrawalResult,
                EconomyOperationResult refundResult) {
            this.provider = provider;
            this.withdrawalResult = withdrawalResult;
            this.refundResult = refundResult;
        }

        @Override
        public EconomyProvider provider() {
            return provider;
        }

        @Override
        public EconomyOperationResult withdraw(EconomyRequest request) {
            withdrawals.incrementAndGet();
            return withdrawalResult;
        }

        @Override
        public EconomyOperationResult refund(EconomyRequest request) {
            refunds.incrementAndGet();
            return refundResult;
        }

        int withdrawalCount() {
            return withdrawals.get();
        }

        int refundCount() {
            return refunds.get();
        }
    }

    static final class FailingAfterMutationRepository implements PlayerStateRepository {
        private final PlayerState state;

        FailingAfterMutationRepository(UUID playerId) {
            state = PlayerState.empty(playerId);
        }

        @Override
        public PlayerState snapshot(UUID playerId) {
            return state;
        }

        @Override
        public PlayerState withLocked(UUID playerId, long expectedRevision, UnaryOperator<PlayerState> mutation)
                throws IOException {
            if (expectedRevision != state.revision()) throw new IllegalStateException("stale test revision");
            mutation.apply(state);
            throw new IOException("simulated persistence failure");
        }
    }
}
