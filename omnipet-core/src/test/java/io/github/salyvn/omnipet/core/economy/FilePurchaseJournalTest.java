package io.github.salyvn.omnipet.core.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.salyvn.omnipet.core.persistence.AtomicFileStore;

class FilePurchaseJournalTest {
    @TempDir
    Path temporary;

    @Test
    void persistsTransactionsAtomicallyAcrossRestart() throws Exception {
        Path root = temporary.resolve("purchases");
        UUID transactionId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        SlotPurchaseQuote quote = new SlotPurchaseQuote(
                playerId,
                4,
                new SlotUnlockRule(2, EconomyAmount.vault(new BigDecimal("25.50"))));
        SlotPurchaseTransaction prepared = SlotPurchaseTransaction.prepared(transactionId, quote);
        FilePurchaseJournal journal = new FilePurchaseJournal(root);

        assertEquals(prepared, journal.create(prepared));
        assertEquals(prepared, journal.create(prepared));
        SlotPurchaseTransaction completed = prepared.withState(
                SlotPurchaseSagaState.COMPLETED,
                EconomyOperationResult.succeeded("withdrawal-proof"),
                null,
                "completed");
        journal.save(completed);

        FilePurchaseJournal restarted = new FilePurchaseJournal(root);
        assertEquals(completed, restarted.find(transactionId).orElseThrow());
        Path file = root.resolve(transactionId + ".yml");
        assertTrue(Files.exists(AtomicFileStore.backupPath(file)));
    }

    @Test
    void completedPurchaseRemainsIdempotentAfterServiceRestart() throws Exception {
        UUID playerId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        var repository = new io.github.salyvn.omnipet.core.persistence.FilePlayerStateRepository(temporary.resolve("players"));
        Path journalRoot = temporary.resolve("restart-purchases");
        EconomyTestFixtures.StubEconomyPort port = new EconomyTestFixtures.StubEconomyPort(
                EconomyProvider.VAULT,
                EconomyOperationResult.succeeded("withdrawal-proof"),
                EconomyOperationResult.succeeded("refund-proof"));
        SlotUnlockService firstService = new SlotUnlockService(
                repository,
                new FilePurchaseJournal(journalRoot),
                Map.of(EconomyProvider.VAULT, port));
        SlotPurchaseQuote quote = firstService.quote(
                playerId,
                new SlotUnlockRule(2, EconomyAmount.vault(BigDecimal.TEN))).quote();

        assertEquals(SlotPurchaseResult.Status.COMPLETED, firstService.purchase(transactionId, quote).status());
        SlotUnlockService restartedService = new SlotUnlockService(
                repository,
                new FilePurchaseJournal(journalRoot),
                Map.of(EconomyProvider.VAULT, port));
        assertEquals(SlotPurchaseResult.Status.COMPLETED, restartedService.purchase(transactionId, quote).status());
        assertEquals(1, port.withdrawalCount());
        assertEquals(1, repository.snapshot(playerId).slotEntitlements().size());
    }
}
