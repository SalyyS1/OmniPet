package io.github.salyvn.omnipet.paper.release;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.salyvn.omnipet.core.domain.PetInstance;
import io.github.salyvn.omnipet.core.persistence.FilePlayerStateRepository;
import io.github.salyvn.omnipet.core.release.ReleaseRewardBundle;
import io.github.salyvn.omnipet.core.release.ReleaseOutboxDeliveryService;
import io.github.salyvn.omnipet.core.release.ReleaseService;

class ReleaseAdminControllerTest {
    @TempDir
    Path temporary;

    @Test
    void listsBoundedMailboxRowsAndReconcilesExactIdentity() throws Exception {
        FileReleaseRewardMailbox mailbox = new FileReleaseRewardMailbox(temporary.resolve("mailbox"));
        UUID playerId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        mailbox.create(new ReleaseMailboxEntry(
                transactionId, playerId, ReleaseMailboxEntry.State.UNKNOWN_REQUIRES_RECONCILIATION,
                List.of(new PaperMaterialReward(Material.DIAMOND, 2)), "unknown"));
        ReleaseAdminController controller = controller(mailbox);

        ReleaseAdminResult listed = controller.execute(new ReleaseAdminCommand.ListPending(1));
        ReleaseAdminResult reconciled = controller.execute(new ReleaseAdminCommand.Reconcile(
                playerId, transactionId, ReleaseAdminCommand.Decision.MAILBOX_PENDING));

        assertEquals(ReleaseAdminResult.Status.COMPLETED, listed.status());
        assertEquals(2, listed.messages().size());
        assertTrue(listed.messages().get(1).contains(transactionId.toString()));
        assertEquals(ReleaseAdminResult.Status.COMPLETED, reconciled.status());
        assertEquals(ReleaseMailboxEntry.State.MAILBOX_PENDING, mailbox.find(transactionId).orElseThrow().state());
    }

    @Test
    void reconciliationRejectsCopiedPlayerIdentity() throws Exception {
        FileReleaseRewardMailbox mailbox = new FileReleaseRewardMailbox(temporary.resolve("identity"));
        UUID transactionId = UUID.randomUUID();
        mailbox.create(new ReleaseMailboxEntry(
                transactionId, UUID.randomUUID(), ReleaseMailboxEntry.State.MAILBOX_PENDING,
                List.of(new PaperMaterialReward(Material.IRON_INGOT, 1)), "pending"));

        ReleaseAdminResult result = controller(mailbox).execute(new ReleaseAdminCommand.Reconcile(
                UUID.randomUUID(), transactionId, ReleaseAdminCommand.Decision.INVENTORY_DELIVERED));

        assertEquals(ReleaseAdminResult.Status.FAILED, result.status());
        assertTrue(result.messages().getFirst().contains("identity mismatch"));
    }

    @Test
    void listsEmbeddedPlayerOutboxWhenNoMailboxFileExists() throws Exception {
        FileReleaseRewardMailbox mailbox = new FileReleaseRewardMailbox(temporary.resolve("empty-mailbox"));
        FilePlayerStateRepository players = new FilePlayerStateRepository(temporary.resolve("embedded-players"));
        UUID playerId = UUID.randomUUID();
        UUID petId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        players.withLocked(playerId, 0, state -> state.withStorage(
                List.of(new PetInstance(petId, "ember_fox", 1, Map.of(), Map.of())),
                10, 1, List.of(), List.of()));
        ReleaseService release = new ReleaseService(players, ignored -> new ReleaseRewardBundle(
                List.of(new ReleaseRewardBundle.InternalReward("pet_dust", 3, Map.of())),
                List.of()));
        release.release(release.preview(transactionId, playerId, petId).preview());

        ReleaseAdminResult result = controller(mailbox, players).execute(
                new ReleaseAdminCommand.ListPending(10));

        assertEquals(ReleaseAdminResult.Status.COMPLETED, result.status());
        assertEquals(2, result.messages().size());
        assertTrue(result.messages().getFirst().contains("0 release mailbox"));
        assertTrue(result.messages().get(1).contains("core " + transactionId));
        assertTrue(result.messages().get(1).contains("internal=PENDING"));
    }

    private ReleaseAdminController controller(ReleaseRewardMailbox mailbox) {
        return controller(mailbox, null);
    }

    private ReleaseAdminController controller(
            ReleaseRewardMailbox mailbox,
            FilePlayerStateRepository players) {
        ReleaseSyncExecutor direct = new ReleaseSyncExecutor() {
            @Override
            public <T> T call(Callable<T> operation) throws Exception {
                return operation.call();
            }
        };
        PaperReleaseInternalRewardPort internal = new PaperReleaseInternalRewardPort(
                mailbox, new PaperReleaseMaterialMapper(), direct,
                (player, transaction, rewards) -> new ReleaseInventoryClaimResult(
                        ReleaseInventoryClaimResult.Status.CAPACITY_FULL, "full"));
        FilePlayerStateRepository repository = players == null
                ? new FilePlayerStateRepository(temporary.resolve("players"))
                : players;
        ReleaseOutboxDeliveryService delivery = new ReleaseOutboxDeliveryService(repository);
        return players == null
                ? new ReleaseAdminController(mailbox, internal, delivery)
                : new ReleaseAdminController(mailbox, internal, null, delivery, repository);
    }
}
