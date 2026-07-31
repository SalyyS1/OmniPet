package io.github.salyvn.omnipet.core.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.salyvn.omnipet.core.persistence.FilePlayerStateRepository;

class SlotPurchaseReconciliationServiceTest {
    @TempDir
    Path temporary;

    @Test
    void confirmedChargeGrantsExactlyOneAuditedEntitlement() throws Exception {
        UUID playerId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        var players = new FilePlayerStateRepository(temporary.resolve("players"));
        var journal = new EconomyTestFixtures.InMemoryPurchaseJournal();
        SlotPurchaseTransaction transaction = ambiguous(transactionId, playerId);
        journal.create(transaction);
        var service = new SlotPurchaseReconciliationService(players, journal);

        SlotPurchaseResult first = service.reconcile(
                transactionId,
                SlotReconciliationDecision.CHARGE_CONFIRMED,
                "Console");
        SlotPurchaseResult second = service.reconcile(
                transactionId,
                SlotReconciliationDecision.CHARGE_CONFIRMED,
                "Console");

        assertEquals(SlotPurchaseResult.Status.COMPLETED, first.status());
        assertEquals(SlotPurchaseResult.Status.INVALID_TRANSACTION, second.status());
        assertEquals(2, players.snapshot(playerId).activeSlotCount());
        assertEquals(1, players.snapshot(playerId).slotEntitlements().size());
        assertTrue(first.detail().contains("Console"));
    }

    @Test
    void confirmedNoChargeNeverGrantsTheSlot() throws Exception {
        UUID playerId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        var players = new FilePlayerStateRepository(temporary.resolve("no-charge-players"));
        var journal = new EconomyTestFixtures.InMemoryPurchaseJournal();
        journal.create(ambiguous(transactionId, playerId));
        var service = new SlotPurchaseReconciliationService(players, journal);

        SlotPurchaseResult result = service.reconcile(
                transactionId,
                SlotReconciliationDecision.NO_CHARGE_CONFIRMED,
                "SalyVn");

        assertEquals(SlotPurchaseResult.Status.WITHDRAWAL_FAILED, result.status());
        assertEquals(SlotPurchaseSagaState.FAILED, result.transaction().state());
        assertTrue(players.snapshot(playerId).slotEntitlements().isEmpty());
    }

    @Test
    void confirmedChargeWaitsForRequiredExternalEntitlementSync() throws Exception {
        UUID playerId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        var players = new FilePlayerStateRepository(temporary.resolve("external-charge-players"));
        var journal = new EconomyTestFixtures.InMemoryPurchaseJournal();
        SlotPurchaseQuote quote = new SlotPurchaseQuote(
                playerId,
                0,
                new SlotUnlockRule(2, EconomyAmount.vault(new BigDecimal("25"))));
        SlotPurchaseTransaction transaction = SlotPurchaseTransaction.prepared(
                transactionId,
                quote,
                true).withState(
                        SlotPurchaseSagaState.UNKNOWN_REQUIRES_RECONCILIATION,
                        EconomyOperationResult.unknown("provider timeout"),
                        null,
                        "manual review required");
        journal.create(transaction);

        SlotPurchaseResult result = new SlotPurchaseReconciliationService(players, journal).reconcile(
                transactionId,
                SlotReconciliationDecision.CHARGE_CONFIRMED,
                "Console");

        assertEquals(SlotPurchaseResult.Status.ENTITLEMENT_SYNC_PENDING, result.status());
        assertEquals(SlotPurchaseSagaState.ENTITLEMENT_SYNC_PENDING, result.transaction().state());
        assertEquals(1, players.snapshot(playerId).slotEntitlements().size());
    }

    @Test
    void pendingScanReturnsAmbiguousTransactions() throws Exception {
        var players = new FilePlayerStateRepository(temporary.resolve("scan-players"));
        var journal = new EconomyTestFixtures.InMemoryPurchaseJournal();
        SlotPurchaseTransaction transaction = ambiguous(UUID.randomUUID(), UUID.randomUUID());
        journal.create(transaction);

        PurchaseJournalScanResult scan = new SlotPurchaseReconciliationService(players, journal).pending(20);

        assertEquals(java.util.List.of(transaction), scan.transactions());
    }

    @Test
    void routineFailedWithdrawalsDoNotCrowdTheReconciliationQueue() throws Exception {
        var players = new FilePlayerStateRepository(temporary.resolve("filtered-scan-players"));
        var journal = new EconomyTestFixtures.InMemoryPurchaseJournal();
        SlotPurchaseTransaction ambiguous = ambiguous(UUID.randomUUID(), UUID.randomUUID());
        SlotPurchaseQuote failedQuote = new SlotPurchaseQuote(
                UUID.randomUUID(),
                0,
                new SlotUnlockRule(2, EconomyAmount.vault(new BigDecimal("25"))));
        SlotPurchaseTransaction routineFailure = SlotPurchaseTransaction.prepared(
                UUID.randomUUID(), failedQuote).withState(
                        SlotPurchaseSagaState.FAILED,
                        EconomyOperationResult.failed("insufficient funds"),
                        null,
                        "insufficient funds");
        journal.create(routineFailure);
        journal.create(ambiguous);

        PurchaseJournalScanResult scan = new SlotPurchaseReconciliationService(players, journal).pending(1);

        assertEquals(java.util.List.of(ambiguous), scan.transactions());
        assertEquals(false, scan.truncated());
    }

    @Test
    void livePurchaseAndAdminReconciliationShareOneTransactionLock() throws Exception {
        UUID playerId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        var players = new FilePlayerStateRepository(temporary.resolve("coordinated-players"));
        var journal = new EconomyTestFixtures.InMemoryPurchaseJournal();
        var enteredProvider = new CountDownLatch(1);
        var releaseProvider = new CountDownLatch(1);
        EconomyPort blockingPort = new EconomyPort() {
            @Override
            public EconomyProvider provider() {
                return EconomyProvider.VAULT;
            }

            @Override
            public EconomyOperationResult withdraw(EconomyRequest request) {
                enteredProvider.countDown();
                try {
                    if (!releaseProvider.await(5, TimeUnit.SECONDS)) {
                        return EconomyOperationResult.unknown("test provider timed out");
                    }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return EconomyOperationResult.unknown("test provider interrupted");
                }
                return EconomyOperationResult.succeeded("charged");
            }

            @Override
            public EconomyOperationResult refund(EconomyRequest request) {
                return EconomyOperationResult.succeeded("refunded");
            }
        };
        var purchases = new SlotUnlockService(
                players,
                journal,
                EconomyPortResolver.fixed(Map.of(EconomyProvider.VAULT, blockingPort)));
        var reconciliation = new SlotPurchaseReconciliationService(players, journal);
        SlotPurchaseQuote quote = purchases.quote(
                playerId,
                new SlotUnlockRule(2, EconomyAmount.vault(new BigDecimal("25")))).quote();

        try (var executor = Executors.newFixedThreadPool(2)) {
            var purchase = executor.submit(() -> purchases.purchase(transactionId, quote));
            assertTrue(enteredProvider.await(5, TimeUnit.SECONDS));
            var operator = executor.submit(() -> reconciliation.reconcile(
                    transactionId,
                    SlotReconciliationDecision.NO_CHARGE_CONFIRMED,
                    "Console"));
            try {
                operator.get(150, TimeUnit.MILLISECONDS);
                throw new AssertionError("operator reconciliation must wait for the live purchase");
            } catch (TimeoutException expected) {
                // The shared transaction coordinator keeps the operator behind the live provider call.
            }
            releaseProvider.countDown();

            assertEquals(SlotPurchaseResult.Status.COMPLETED, purchase.get(5, TimeUnit.SECONDS).status());
            assertEquals(
                    SlotPurchaseResult.Status.INVALID_TRANSACTION,
                    operator.get(5, TimeUnit.SECONDS).status());
        }
        assertEquals(SlotPurchaseSagaState.COMPLETED, journal.find(transactionId).orElseThrow().state());
        assertEquals(1, players.snapshot(playerId).slotEntitlements().size());
    }

    @Test
    void unavailableProviderEvidenceCannotBeRewrittenAsChargeOrRefund() throws Exception {
        UUID transactionId = UUID.randomUUID();
        var players = new FilePlayerStateRepository(temporary.resolve("unavailable-players"));
        var journal = new EconomyTestFixtures.InMemoryPurchaseJournal();
        SlotPurchaseQuote quote = new SlotPurchaseQuote(
                UUID.randomUUID(),
                0,
                new SlotUnlockRule(2, EconomyAmount.vault(new BigDecimal("25"))));
        SlotPurchaseTransaction transaction = SlotPurchaseTransaction.prepared(transactionId, quote).withState(
                SlotPurchaseSagaState.FAILED,
                EconomyOperationResult.unavailable("provider not loaded"),
                null,
                "provider not loaded");
        journal.create(transaction);
        var service = new SlotPurchaseReconciliationService(players, journal);

        assertEquals(
                SlotPurchaseResult.Status.INVALID_TRANSACTION,
                service.reconcile(transactionId, SlotReconciliationDecision.CHARGE_CONFIRMED, "Console").status());
        assertEquals(
                SlotPurchaseResult.Status.INVALID_TRANSACTION,
                service.reconcile(transactionId, SlotReconciliationDecision.REFUND_CONFIRMED, "Console").status());
    }

    private static SlotPurchaseTransaction ambiguous(UUID transactionId, UUID playerId) {
        SlotPurchaseQuote quote = new SlotPurchaseQuote(
                playerId,
                0,
                new SlotUnlockRule(2, EconomyAmount.vault(new BigDecimal("25"))));
        return SlotPurchaseTransaction.prepared(transactionId, quote).withState(
                SlotPurchaseSagaState.UNKNOWN_REQUIRES_RECONCILIATION,
                EconomyOperationResult.unknown("provider timeout"),
                null,
                "manual review required");
    }
}
