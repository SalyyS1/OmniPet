package io.github.salyvn.omnipet.paper.release;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.salyvn.omnipet.core.release.InternalRewardDeliveryPort;
import io.github.salyvn.omnipet.core.release.ReleaseRewardBundle;

class PaperReleaseInternalRewardPortTest {
    @TempDir
    Path temporary;

    @Test
    void fullInventoryFallsBackToDurableMailboxAndCoreCanAcknowledge() throws Exception {
        FileReleaseRewardMailbox mailbox = new FileReleaseRewardMailbox(temporary);
        AtomicInteger calls = new AtomicInteger();
        PaperReleaseInternalRewardPort port = port(mailbox, (player, transaction, rewards) -> {
            calls.incrementAndGet();
            return new ReleaseInventoryClaimResult(
                    ReleaseInventoryClaimResult.Status.CAPACITY_FULL, "inventory full");
        });
        UUID playerId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();

        InternalRewardDeliveryPort.Outcome outcome = port.deliver(playerId, transactionId, rewards());
        ReleaseMailboxEntry persisted = mailbox.find(transactionId).orElseThrow();

        assertEquals(InternalRewardDeliveryPort.Status.DELIVERED, outcome.status());
        assertEquals(ReleaseMailboxEntry.State.MAILBOX_PENDING, persisted.state());
        assertEquals(1, calls.get());
    }

    @Test
    void deliveredTransactionDoesNotMutateInventoryTwice() throws Exception {
        FileReleaseRewardMailbox mailbox = new FileReleaseRewardMailbox(temporary);
        AtomicInteger calls = new AtomicInteger();
        PaperReleaseInternalRewardPort port = port(mailbox, (player, transaction, rewards) -> {
            calls.incrementAndGet();
            return new ReleaseInventoryClaimResult(ReleaseInventoryClaimResult.Status.DELIVERED, "delivered");
        });
        UUID playerId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();

        InternalRewardDeliveryPort.Outcome first = port.deliver(playerId, transactionId, rewards());
        InternalRewardDeliveryPort.Outcome retry = port.deliver(playerId, transactionId, rewards());

        assertEquals(InternalRewardDeliveryPort.Status.DELIVERED, first.status());
        assertEquals(InternalRewardDeliveryPort.Status.DELIVERED, retry.status());
        assertEquals(1, calls.get());
        assertEquals(ReleaseMailboxEntry.State.INVENTORY_DELIVERED,
                mailbox.find(transactionId).orElseThrow().state());
    }

    @Test
    void interruptedClaimBecomesUnknownAndIsNeverBlindlyRetried() throws Exception {
        FileReleaseRewardMailbox mailbox = new FileReleaseRewardMailbox(temporary);
        AtomicInteger calls = new AtomicInteger();
        PaperReleaseInternalRewardPort port = port(mailbox, (player, transaction, rewards) -> {
            calls.incrementAndGet();
            throw new IllegalStateException("scheduler timeout");
        });
        UUID playerId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();

        InternalRewardDeliveryPort.Outcome first = port.deliver(playerId, transactionId, rewards());
        InternalRewardDeliveryPort.Outcome retry = port.deliver(playerId, transactionId, rewards());

        assertEquals(InternalRewardDeliveryPort.Status.FAILED, first.status());
        assertEquals(InternalRewardDeliveryPort.Status.FAILED, retry.status());
        assertEquals(1, calls.get());
        assertEquals(ReleaseMailboxEntry.State.UNKNOWN_REQUIRES_RECONCILIATION,
                mailbox.find(transactionId).orElseThrow().state());
    }

    @Test
    void mapperAggregatesMaterialsAndRejectsUnsupportedRewards() {
        PaperReleaseMaterialMapper mapper = new PaperReleaseMaterialMapper();

        List<PaperMaterialReward> mapped = mapper.map(List.of(
                new ReleaseRewardBundle.InternalReward("material", 2, Map.of("type", "DIAMOND")),
                new ReleaseRewardBundle.InternalReward("material", 3, Map.of("material", "diamond"))));

        assertEquals(List.of(new PaperMaterialReward(org.bukkit.Material.DIAMOND, 5)), mapped);
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> mapper.map(List.of(
                new ReleaseRewardBundle.InternalReward("custom_item", 1, Map.of()))));
    }

    private static PaperReleaseInternalRewardPort port(
            ReleaseRewardMailbox mailbox,
            ReleaseInventoryGateway gateway) {
        ReleaseSyncExecutor direct = new ReleaseSyncExecutor() {
            @Override
            public <T> T call(Callable<T> operation) throws Exception {
                return operation.call();
            }
        };
        return new PaperReleaseInternalRewardPort(
                mailbox, new PaperReleaseMaterialMapper(), direct, gateway);
    }

    private static List<ReleaseRewardBundle.InternalReward> rewards() {
        return List.of(new ReleaseRewardBundle.InternalReward(
                "material", 3, Map.of("type", "DIAMOND")));
    }
}
