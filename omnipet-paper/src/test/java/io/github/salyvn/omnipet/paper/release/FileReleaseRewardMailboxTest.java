package io.github.salyvn.omnipet.paper.release;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileReleaseRewardMailboxTest {
    @TempDir
    Path temporary;

    @Test
    void createRetryAndRestartAreIdempotent() throws Exception {
        UUID transactionId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        ReleaseMailboxEntry expected = entry(transactionId, playerId, Material.DIAMOND, 7);
        FileReleaseRewardMailbox mailbox = new FileReleaseRewardMailbox(temporary);

        ReleaseMailboxEntry first = mailbox.create(expected);
        ReleaseMailboxEntry retry = mailbox.create(expected);
        ReleaseMailboxEntry restarted = new FileReleaseRewardMailbox(temporary).find(transactionId).orElseThrow();

        assertEquals(expected, first);
        assertEquals(expected, retry);
        assertEquals(expected, restarted);
        assertThrows(java.io.IOException.class, () -> mailbox.create(
                entry(transactionId, UUID.randomUUID(), Material.DIAMOND, 7)));
    }

    @Test
    void pendingListIsBoundedAndExcludesDeliveredEntries() throws Exception {
        FileReleaseRewardMailbox mailbox = new FileReleaseRewardMailbox(temporary);
        ReleaseMailboxEntry pending = entry(UUID.randomUUID(), UUID.randomUUID(), Material.IRON_INGOT, 4);
        ReleaseMailboxEntry delivered = entry(UUID.randomUUID(), UUID.randomUUID(), Material.GOLD_INGOT, 2);
        mailbox.create(pending);
        mailbox.create(delivered);
        mailbox.update(delivered.transactionId(), ReleaseMailboxEntry.State.INVENTORY_DELIVERED, "done");

        List<ReleaseMailboxEntry> listed = mailbox.list(1);

        assertEquals(List.of(pending), listed);
        assertThrows(IllegalArgumentException.class, () -> mailbox.list(51));
    }

    private static ReleaseMailboxEntry entry(UUID transactionId, UUID playerId, Material material, long amount) {
        return new ReleaseMailboxEntry(
                transactionId, playerId, ReleaseMailboxEntry.State.MAILBOX_PENDING,
                List.of(new PaperMaterialReward(material, amount)), "accepted");
    }
}
