package io.github.salyvn.omnipet.core.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

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

    @Test
    void externalEntitlementSyncRemainsDurableAcrossRestart() throws Exception {
        UUID playerId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        var repository = new io.github.salyvn.omnipet.core.persistence.FilePlayerStateRepository(
                temporary.resolve("external-sync-players"));
        Path journalRoot = temporary.resolve("external-sync-purchases");
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

        SlotPurchaseResult purchased = firstService.purchase(transactionId, quote, true);

        assertEquals(SlotPurchaseResult.Status.ENTITLEMENT_SYNC_PENDING, purchased.status());
        assertEquals(1, port.withdrawalCount());
        new FilePurchaseJournal(journalRoot).save(purchased.transaction().withState(
                SlotPurchaseSagaState.ENTITLEMENT_PERSISTED,
                purchased.transaction().withdrawal(),
                purchased.transaction().refund(),
                "simulated crash before the pending checkpoint"));

        SlotUnlockService restarted = new SlotUnlockService(
                repository,
                new FilePurchaseJournal(journalRoot),
                Map.of(EconomyProvider.VAULT, port));
        assertEquals(
                SlotPurchaseResult.Status.ENTITLEMENT_SYNC_PENDING,
                restarted.purchase(transactionId, quote, true).status());
        assertEquals(
                SlotPurchaseResult.Status.ENTITLEMENT_SYNC_PENDING,
                restarted.synchronizeExternalEntitlement(
                        transactionId,
                        ignored -> ExternalEntitlementResult.pending("LuckPerms unavailable")).status());

        SlotUnlockService secondRestart = new SlotUnlockService(
                repository,
                new FilePurchaseJournal(journalRoot),
                Map.of(EconomyProvider.VAULT, port));
        SlotPurchaseResult completed = secondRestart.synchronizeExternalEntitlement(
                transactionId,
                ignored -> ExternalEntitlementResult.completed("LuckPerms node granted"));

        assertEquals(SlotPurchaseResult.Status.COMPLETED, completed.status());
        assertEquals(SlotPurchaseSagaState.COMPLETED, completed.transaction().state());
        assertEquals(1, port.withdrawalCount());
        assertEquals(1, repository.snapshot(playerId).slotEntitlements().size());
    }

    @Test
    void versionOneFailedRefundIsNormalizedIntoTheRecoveryQueue() throws Exception {
        Path root = temporary.resolve("legacy-refund-purchases");
        Files.createDirectories(root);
        UUID transactionId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        Files.writeString(root.resolve(transactionId + ".yml"), """
                schemaVersion: 1
                transactionId: "%s"
                playerId: "%s"
                expectedRevision: 0
                slot: 2
                provider: VAULT
                amount: "25"
                state: FAILED
                withdrawal: { status: PROVEN_SUCCESS, evidence: "charged" }
                refund: { status: PROVEN_FAILURE, evidence: "refund rejected" }
                detail: "legacy failed refund"
                """.formatted(transactionId, playerId));
        FilePurchaseJournal journal = new FilePurchaseJournal(root);

        SlotPurchaseTransaction migrated = journal.find(transactionId).orElseThrow();
        PurchaseJournalScanResult pending = new SlotPurchaseReconciliationService(
                new io.github.salyvn.omnipet.core.persistence.FilePlayerStateRepository(
                        temporary.resolve("legacy-refund-players")),
                journal).pending(20);

        assertEquals(SlotPurchaseSagaState.REFUND_PENDING, migrated.state());
        assertEquals(java.util.List.of(migrated), pending.transactions());
    }

    @Test
    void versionOneCompletionsRequireIdempotentEntitlementVerification() throws Exception {
        assertLegacyCompletionRequiresVerification(SlotPurchaseSagaState.ENTITLEMENT_PERSISTED, "persisted");
        assertLegacyCompletionRequiresVerification(SlotPurchaseSagaState.COMPLETED, "completed");
    }

    @Test
    void entitlementSyncCannotCompleteWhenTheLocalEntitlementIsMissing() throws Exception {
        UUID transactionId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        var repository = new io.github.salyvn.omnipet.core.persistence.FilePlayerStateRepository(
                temporary.resolve("missing-local-players"));
        FilePurchaseJournal journal = new FilePurchaseJournal(temporary.resolve("missing-local-purchases"));
        SlotPurchaseQuote quote = new SlotPurchaseQuote(
                playerId,
                0,
                new SlotUnlockRule(2, EconomyAmount.vault(BigDecimal.TEN)));
        journal.create(SlotPurchaseTransaction.prepared(transactionId, quote).withState(
                SlotPurchaseSagaState.ENTITLEMENT_PERSISTED,
                EconomyOperationResult.succeeded("charged"),
                null,
                "local entitlement was rolled back"));
        AtomicInteger calls = new AtomicInteger();

        SlotPurchaseResult result = new SlotUnlockService(repository, journal, Map.of())
                .synchronizeExternalEntitlement(transactionId, ignored -> {
                    calls.incrementAndGet();
                    return ExternalEntitlementResult.completed("should not run");
                });

        assertEquals(SlotPurchaseResult.Status.UNKNOWN_REQUIRES_RECONCILIATION, result.status());
        assertEquals(SlotPurchaseSagaState.UNKNOWN_REQUIRES_RECONCILIATION, result.transaction().state());
        assertEquals(0, calls.get());
    }

    @Test
    void scansPendingEntriesWithoutOneCorruptFileHidingTheRest() throws Exception {
        Path root = temporary.resolve("scan-purchases");
        FilePurchaseJournal journal = new FilePurchaseJournal(root);
        SlotPurchaseQuote quote = new SlotPurchaseQuote(
                UUID.randomUUID(),
                0,
                new SlotUnlockRule(2, EconomyAmount.vault(BigDecimal.TEN)));
        SlotPurchaseTransaction pending = SlotPurchaseTransaction.prepared(UUID.randomUUID(), quote).withState(
                SlotPurchaseSagaState.UNKNOWN_REQUIRES_RECONCILIATION,
                EconomyOperationResult.unknown("timeout"),
                null,
                "manual review required");
        journal.create(pending);
        Files.writeString(root.resolve(UUID.randomUUID() + ".yml"), "not: [valid");
        Files.writeString(root.resolve("x".repeat(129) + ".yml"), "not: [valid");

        PurchaseJournalScanResult result = journal.scan(
                Set.of(SlotPurchaseSagaState.UNKNOWN_REQUIRES_RECONCILIATION),
                20);

        assertEquals(java.util.List.of(pending), result.transactions());
        assertEquals(2, result.issues().size());
        assertTrue(result.issues().stream().allMatch(issue -> issue.entryName().length() <= 128));
        assertTrue(!result.truncated());
    }

    @Test
    void scanStopsAtTheConfiguredEntryBudget() throws Exception {
        Path root = temporary.resolve("bounded-scan-purchases");
        Files.createDirectories(root);
        for (int index = 0; index < 5; index++) {
            Files.writeString(root.resolve("corrupt-" + index + ".yml"), "not: [valid");
        }

        PurchaseJournalScanResult result = new PurchaseJournalScanner(
                root,
                new PurchaseJournalYamlCodec(),
                3).scan(Set.of(SlotPurchaseSagaState.UNKNOWN_REQUIRES_RECONCILIATION), 20);

        assertEquals(3, result.issues().size());
        assertTrue(result.truncated());
    }

    @Test
    void cursorEventuallyReachesLaterActionableRowsWithoutCountingBackups() throws Exception {
        Path root = temporary.resolve("paged-scan-purchases");
        FilePurchaseJournal journal = new FilePurchaseJournal(root);
        SlotPurchaseTransaction first = transaction(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                SlotPurchaseSagaState.COMPLETED);
        SlotPurchaseTransaction second = transaction(
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                SlotPurchaseSagaState.COMPLETED);
        SlotPurchaseTransaction pending = transaction(
                UUID.fromString("f0000000-0000-0000-0000-000000000001"),
                SlotPurchaseSagaState.UNKNOWN_REQUIRES_RECONCILIATION);
        for (SlotPurchaseTransaction transaction : java.util.List.of(first, second, pending)) {
            journal.create(transaction.withState(
                    SlotPurchaseSagaState.PREPARED,
                    null,
                    null,
                    ""));
            journal.save(transaction);
            assertTrue(Files.exists(AtomicFileStore.backupPath(root.resolve(transaction.transactionId() + ".yml"))));
        }

        PurchaseJournalScanResult backupBounded = new PurchaseJournalScanner(
                root,
                new PurchaseJournalYamlCodec(),
                3).scan(Set.of(SlotPurchaseSagaState.UNKNOWN_REQUIRES_RECONCILIATION), 1);
        assertEquals(java.util.List.of(pending), backupBounded.transactions());
        assertEquals(null, backupBounded.nextCursor());

        PurchaseJournalScanner scanner = new PurchaseJournalScanner(root, new PurchaseJournalYamlCodec(), 2);
        String cursor = null;
        Set<String> visitedCursors = new HashSet<>();
        SlotPurchaseTransaction discovered = null;
        for (int page = 0; page < 20 && discovered == null; page++) {
            PurchaseJournalScanResult result = scanner.scan(
                    Set.of(SlotPurchaseSagaState.UNKNOWN_REQUIRES_RECONCILIATION),
                    1,
                    cursor);
            if (!result.transactions().isEmpty()) discovered = result.transactions().getFirst();
            cursor = result.nextCursor();
            if (cursor != null) assertTrue(visitedCursors.add(cursor), "cursor must make forward progress");
        }

        assertEquals(pending, discovered);
    }

    @Test
    void cursorContinuesAfterTheLastExaminedUuidWithinOneBucket() throws Exception {
        Path root = temporary.resolve("same-bucket-purchases");
        FilePurchaseJournal journal = new FilePurchaseJournal(root);
        SlotPurchaseTransaction first = transaction(
                UUID.fromString("10000000-0000-0000-0000-000000000001"),
                SlotPurchaseSagaState.UNKNOWN_REQUIRES_RECONCILIATION);
        SlotPurchaseTransaction second = transaction(
                UUID.fromString("20000000-0000-0000-0000-000000000001"),
                SlotPurchaseSagaState.UNKNOWN_REQUIRES_RECONCILIATION);
        journal.create(second);
        journal.create(first);

        PurchaseJournalScanResult firstPage = journal.scan(
                Set.of(SlotPurchaseSagaState.UNKNOWN_REQUIRES_RECONCILIATION),
                1);
        PurchaseJournalScanResult secondPage = journal.scan(
                Set.of(SlotPurchaseSagaState.UNKNOWN_REQUIRES_RECONCILIATION),
                1,
                firstPage.nextCursor());

        assertEquals(java.util.List.of(first), firstPage.transactions());
        assertEquals(java.util.List.of(second), secondPage.transactions());
        assertTrue(!firstPage.nextCursor().isBlank());
        assertEquals(null, secondPage.nextCursor());
    }

    @Test
    void rejectsOversizedEntriesBeforeScanOrDirectFindCanLoadThem() throws Exception {
        Path root = temporary.resolve("oversized-purchases");
        Files.createDirectories(root);
        UUID transactionId = UUID.randomUUID();
        Path entry = root.resolve(transactionId + ".yml");
        Files.write(entry, new byte[PurchaseJournalFileReader.MAX_ENTRY_BYTES + 1]);
        FilePurchaseJournal journal = new FilePurchaseJournal(root);

        IOException direct = assertThrows(IOException.class, () -> journal.find(transactionId));
        PurchaseJournalScanResult scan = journal.scan(
                Set.of(SlotPurchaseSagaState.UNKNOWN_REQUIRES_RECONCILIATION),
                20);

        assertTrue(direct.getMessage().contains("exceeds " + PurchaseJournalFileReader.MAX_ENTRY_BYTES));
        assertEquals(1, scan.issues().size());
        assertTrue(scan.issues().getFirst().detail().contains("exceeds " + PurchaseJournalFileReader.MAX_ENTRY_BYTES));
    }

    @Test
    void capsReportedIssuesAndExposesTheOmittedCount() throws Exception {
        Path root = temporary.resolve("issue-cap-purchases");
        Files.createDirectories(root);
        for (int index = 0; index < 25; index++) {
            String id = "00000000-0000-0000-0000-" + String.format("%012d", index);
            Files.writeString(root.resolve(id + ".yml"), "not: [valid");
        }

        PurchaseJournalScanResult result = new FilePurchaseJournal(root).scan(
                Set.of(SlotPurchaseSagaState.UNKNOWN_REQUIRES_RECONCILIATION),
                20);

        assertEquals(20, result.issues().size());
        assertEquals(5, result.omittedIssueCount());
        assertFalse(result.issuesTruncated());
        assertTrue(result.truncated());
    }

    @Test
    void rejectsMalformedOrMismatchedCursors() {
        PurchaseJournalScanner scanner = new PurchaseJournalScanner(
                temporary.resolve("cursor-validation-purchases"),
                new PurchaseJournalYamlCodec());

        assertThrows(IllegalArgumentException.class, () -> scanner.scan(
                Set.of(SlotPurchaseSagaState.UNKNOWN_REQUIRES_RECONCILIATION),
                20,
                "not-a-cursor"));
        assertThrows(IllegalArgumentException.class, () -> scanner.scan(
                Set.of(SlotPurchaseSagaState.UNKNOWN_REQUIRES_RECONCILIATION),
                20,
                "v1.a.b0000000000000000000000000000000"));
    }

    private void assertLegacyCompletionRequiresVerification(
            SlotPurchaseSagaState legacyState,
            String suffix) throws Exception {
        UUID playerId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        var repository = new io.github.salyvn.omnipet.core.persistence.FilePlayerStateRepository(
                temporary.resolve("legacy-sync-players-" + suffix));
        Path journalRoot = temporary.resolve("legacy-sync-purchases-" + suffix);
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

        assertEquals(
                SlotPurchaseResult.Status.ENTITLEMENT_SYNC_PENDING,
                firstService.purchase(transactionId, quote, true).status());
        Path file = journalRoot.resolve(transactionId + ".yml");
        Files.writeString(file, """
                schemaVersion: 1
                transactionId: "%s"
                playerId: "%s"
                expectedRevision: %d
                slot: %d
                provider: VAULT
                amount: "10"
                state: %s
                withdrawal: { status: PROVEN_SUCCESS, evidence: "withdrawal-proof" }
                detail: "legacy completion"
                """.formatted(
                        transactionId,
                        playerId,
                        quote.expectedRevision(),
                        quote.rule().slot(),
                        legacyState));

        FilePurchaseJournal restartedJournal = new FilePurchaseJournal(journalRoot);
        SlotUnlockService restarted = new SlotUnlockService(
                repository,
                restartedJournal,
                Map.of(EconomyProvider.VAULT, port));
        SlotPurchaseResult resumed = restarted.purchase(transactionId, quote, false);

        assertEquals(SlotPurchaseResult.Status.ENTITLEMENT_SYNC_PENDING, resumed.status());
        assertTrue(resumed.transaction().externalEntitlementRequired());
        assertTrue(resumed.detail().contains("legacy schema v1"));
        assertEquals(1, port.withdrawalCount());
        assertEquals(
                java.util.List.of(resumed.transaction()),
                new SlotPurchaseReconciliationService(repository, restartedJournal).pending(20).transactions());

        SlotPurchaseResult completed = restarted.synchronizeExternalEntitlement(
                transactionId,
                ignored -> ExternalEntitlementResult.completed("entitlement verified"));

        assertEquals(SlotPurchaseResult.Status.COMPLETED, completed.status());
        assertEquals(1, port.withdrawalCount());
        assertTrue(Files.readString(file).contains("schemaVersion: 2"));
        assertTrue(Files.exists(AtomicFileStore.backupPath(file)));
    }

    private static SlotPurchaseTransaction transaction(UUID transactionId, SlotPurchaseSagaState state) {
        SlotPurchaseQuote quote = new SlotPurchaseQuote(
                UUID.randomUUID(),
                0,
                new SlotUnlockRule(2, EconomyAmount.vault(BigDecimal.TEN)));
        return SlotPurchaseTransaction.prepared(transactionId, quote).withState(
                state,
                state == SlotPurchaseSagaState.COMPLETED
                        ? EconomyOperationResult.succeeded("charged")
                        : EconomyOperationResult.unknown("timeout"),
                null,
                state == SlotPurchaseSagaState.COMPLETED ? "completed" : "manual review required");
    }
}
