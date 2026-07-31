package io.github.salyvn.omnipet.core.economy;

import static io.github.salyvn.omnipet.core.economy.EconomyTestFixtures.FailingAfterMutationRepository;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.salyvn.omnipet.core.domain.PlayerState;
import io.github.salyvn.omnipet.core.persistence.FilePlayerStateRepository;
import io.github.salyvn.omnipet.core.persistence.PlayerStateRepository;

class SlotUnlockServiceTest {
    @TempDir
    Path temporary;

    @Test
    void doubleClickUsesOneWithdrawalAndGrantsOneEntitlement() throws Exception {
        UUID playerId = UUID.randomUUID();
        PlayerStateRepository repository = repository();
        EconomyTestFixtures.InMemoryPurchaseJournal journal = new EconomyTestFixtures.InMemoryPurchaseJournal();
        EconomyTestFixtures.StubEconomyPort port = port(EconomyOperationResult.succeeded("withdraw-1"));
        SlotUnlockService service = service(repository, journal, port);
        SlotPurchaseQuote quote = service.quote(playerId, vaultRule()).quote();
        UUID transactionId = UUID.randomUUID();

        SlotPurchaseResult first = service.purchase(transactionId, quote);
        SlotPurchaseResult second = service.purchase(transactionId, quote);
        PlayerState saved = repository.snapshot(playerId);

        assertEquals(SlotPurchaseResult.Status.COMPLETED, first.status());
        assertEquals(SlotPurchaseResult.Status.COMPLETED, second.status());
        assertEquals(1, port.withdrawalCount());
        assertEquals(2, saved.activeSlotCount());
        assertEquals(1, saved.slotEntitlements().size());
        assertEquals(transactionId.toString(), saved.slotEntitlements().getFirst().referenceId());
    }

    @Test
    void providerUnavailableDisablesQuoteAndCannotGrant() throws Exception {
        UUID playerId = UUID.randomUUID();
        PlayerStateRepository repository = repository();
        EconomyTestFixtures.InMemoryPurchaseJournal journal = new EconomyTestFixtures.InMemoryPurchaseJournal();
        SlotUnlockService service = new SlotUnlockService(repository, journal, Map.of());

        SlotQuoteResult quote = service.quote(playerId, vaultRule());
        SlotPurchaseResult purchase = service.purchase(UUID.randomUUID(), quote.quote());

        assertEquals(SlotQuoteResult.Status.PROVIDER_UNAVAILABLE, quote.status());
        assertEquals(SlotPurchaseResult.Status.PROVIDER_UNAVAILABLE, purchase.status());
        assertEquals(SlotPurchaseSagaState.FAILED, purchase.transaction().state());
        assertEquals(1, repository.snapshot(playerId).activeSlotCount());
    }

    @Test
    void provenWithdrawalFailureDoesNotPersistEntitlement() throws Exception {
        UUID playerId = UUID.randomUUID();
        PlayerStateRepository repository = repository();
        EconomyTestFixtures.InMemoryPurchaseJournal journal = new EconomyTestFixtures.InMemoryPurchaseJournal();
        EconomyTestFixtures.StubEconomyPort port = port(EconomyOperationResult.failed("insufficient funds"));
        SlotUnlockService service = service(repository, journal, port);

        SlotPurchaseResult result = service.purchase(UUID.randomUUID(), service.quote(playerId, vaultRule()).quote());

        assertEquals(SlotPurchaseResult.Status.WITHDRAWAL_FAILED, result.status());
        assertEquals(1, port.withdrawalCount());
        assertEquals(0, port.refundCount());
        assertTrue(repository.snapshot(playerId).slotEntitlements().isEmpty());
    }

    @Test
    void ambiguousWithdrawalIsReconciliationGatedAndNeverReplayed() throws Exception {
        UUID playerId = UUID.randomUUID();
        PlayerStateRepository repository = repository();
        EconomyTestFixtures.InMemoryPurchaseJournal journal = new EconomyTestFixtures.InMemoryPurchaseJournal();
        EconomyTestFixtures.StubEconomyPort port = port(EconomyOperationResult.unknown("provider timeout"));
        SlotUnlockService service = service(repository, journal, port);
        SlotPurchaseQuote quote = service.quote(playerId, vaultRule()).quote();
        UUID transactionId = UUID.randomUUID();

        SlotPurchaseResult first = service.purchase(transactionId, quote);
        SlotPurchaseResult second = service.purchase(transactionId, quote);
        SlotPurchaseResult recovered = service.recover(transactionId);

        assertEquals(SlotPurchaseResult.Status.UNKNOWN_REQUIRES_RECONCILIATION, first.status());
        assertEquals(SlotPurchaseResult.Status.UNKNOWN_REQUIRES_RECONCILIATION, second.status());
        assertEquals(SlotPurchaseResult.Status.UNKNOWN_REQUIRES_RECONCILIATION, recovered.status());
        assertEquals(SlotPurchaseSagaState.UNKNOWN_REQUIRES_RECONCILIATION, recovered.transaction().state());
        assertEquals(1, port.withdrawalCount());
        assertEquals(0, port.refundCount());
        assertTrue(repository.snapshot(playerId).slotEntitlements().isEmpty());
    }

    @Test
    void persistenceFailureAfterProvenWithdrawalRefundsAndLeavesNoGrant() throws Exception {
        UUID playerId = UUID.randomUUID();
        FailingAfterMutationRepository repository = new FailingAfterMutationRepository(playerId);
        EconomyTestFixtures.InMemoryPurchaseJournal journal = new EconomyTestFixtures.InMemoryPurchaseJournal();
        EconomyTestFixtures.StubEconomyPort port = new EconomyTestFixtures.StubEconomyPort(
                EconomyProvider.VAULT,
                EconomyOperationResult.succeeded("withdraw-2"),
                EconomyOperationResult.succeeded("refund-2"));
        SlotUnlockService service = service(repository, journal, port);
        SlotPurchaseQuote quote = service.quote(playerId, vaultRule()).quote();

        SlotPurchaseResult result = service.purchase(UUID.randomUUID(), quote);

        assertEquals(SlotPurchaseResult.Status.PERSISTENCE_FAILED_REFUNDED, result.status());
        assertEquals(SlotPurchaseSagaState.REFUNDED, result.transaction().state());
        assertEquals(1, port.withdrawalCount());
        assertEquals(1, port.refundCount());
        assertTrue(repository.snapshot(playerId).slotEntitlements().isEmpty());
    }

    @Test
    void refundPendingRecoveryNeverBlindlyReplaysRefund() throws Exception {
        UUID playerId = UUID.randomUUID();
        PlayerStateRepository repository = repository();
        EconomyTestFixtures.InMemoryPurchaseJournal journal = new EconomyTestFixtures.InMemoryPurchaseJournal();
        EconomyTestFixtures.StubEconomyPort port = port(EconomyOperationResult.succeeded("unused"));
        SlotPurchaseQuote quote = new SlotPurchaseQuote(playerId, 0, vaultRule());
        SlotPurchaseTransaction pending = SlotPurchaseTransaction.prepared(UUID.randomUUID(), quote).withState(
                SlotPurchaseSagaState.REFUND_PENDING,
                EconomyOperationResult.succeeded("withdrawal-proof"),
                null,
                "refund call pending");
        journal.create(pending);
        SlotUnlockService service = service(repository, journal, port);

        SlotPurchaseResult recovered = service.recover(pending.transactionId());

        assertEquals(SlotPurchaseResult.Status.UNKNOWN_REQUIRES_RECONCILIATION, recovered.status());
        assertEquals(SlotPurchaseSagaState.UNKNOWN_REQUIRES_RECONCILIATION, recovered.transaction().state());
        assertEquals(0, port.refundCount());
    }

    @Test
    void journalFailureAfterWithdrawalRetainsProofForSafeRefundRecovery() throws Exception {
        UUID playerId = UUID.randomUUID();
        PlayerStateRepository repository = repository();
        EconomyTestFixtures.FailObservedWithdrawalOnceJournal journal =
                new EconomyTestFixtures.FailObservedWithdrawalOnceJournal();
        EconomyTestFixtures.StubEconomyPort port = new EconomyTestFixtures.StubEconomyPort(
                EconomyProvider.VAULT,
                EconomyOperationResult.succeeded("withdrawal-proof"),
                EconomyOperationResult.failed("refund-failed"));
        SlotUnlockService service = service(repository, journal, port);
        UUID transactionId = UUID.randomUUID();

        SlotPurchaseResult result = service.purchase(transactionId, service.quote(playerId, vaultRule()).quote());
        SlotPurchaseTransaction recorded = journal.find(transactionId).orElseThrow();

        assertEquals(SlotPurchaseResult.Status.REFUND_FAILED_REQUIRES_RECOVERY, result.status());
        assertTrue(recorded.withdrawal().provenSuccess());
        assertEquals(SlotPurchaseSagaState.FAILED, recorded.state());
        assertEquals(1, port.withdrawalCount());
        assertEquals(1, port.refundCount());
    }

    @Test
    void nextSlotOnlyIsRevalidatedBeforeProviderCall() throws Exception {
        UUID playerId = UUID.randomUUID();
        PlayerStateRepository repository = repository();
        EconomyTestFixtures.InMemoryPurchaseJournal journal = new EconomyTestFixtures.InMemoryPurchaseJournal();
        EconomyTestFixtures.StubEconomyPort port = port(EconomyOperationResult.succeeded("unused"));
        SlotUnlockService service = service(repository, journal, port);
        SlotUnlockRule slotThree = new SlotUnlockRule(3, EconomyAmount.vault(BigDecimal.TEN));

        SlotQuoteResult quote = service.quote(playerId, slotThree);
        SlotPurchaseResult result = service.purchase(UUID.randomUUID(), quote.quote());

        assertEquals(SlotQuoteResult.Status.NOT_NEXT_SLOT, quote.status());
        assertEquals(SlotPurchaseResult.Status.NOT_NEXT_SLOT, result.status());
        assertEquals(0, port.withdrawalCount());
        assertFalse(result.succeeded());
    }

    private PlayerStateRepository repository() {
        return new FilePlayerStateRepository(temporary.resolve(UUID.randomUUID().toString()));
    }

    private static SlotUnlockRule vaultRule() {
        return new SlotUnlockRule(2, EconomyAmount.vault(new BigDecimal("25000.00")));
    }

    private static EconomyTestFixtures.StubEconomyPort port(EconomyOperationResult withdrawal) {
        return new EconomyTestFixtures.StubEconomyPort(
                EconomyProvider.VAULT,
                withdrawal,
                EconomyOperationResult.succeeded("refund"));
    }

    private static SlotUnlockService service(
            PlayerStateRepository repository,
            PurchaseJournal journal,
            EconomyTestFixtures.StubEconomyPort port) {
        return new SlotUnlockService(repository, journal, Map.of(port.provider(), port));
    }
}
