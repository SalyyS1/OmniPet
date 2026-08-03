package io.github.salyvn.omnipet.paper.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/**
 * The hub's one-read rule and its shutdown contract, asserted at the source level like the other
 * controller guards in this package.
 */
class PlayerHubControllerContractTest {
    private static final Path SOURCE = Path.of(
            "src/main/java/io/github/salyvn/omnipet/paper/player/PlayerHubController.java");

    @Test
    void theHubReadIsCoalescedUnderItsOwnNamespacedKey() throws IOException {
        String source = Files.readString(SOURCE);

        assertTrue(source.contains("private static final String HUB_VIEW_TASK = \"hub:view\";"),
                "hub reads must coalesce under their own key");
        assertTrue(source.contains("submitLatest(playerId, HUB_VIEW_TASK"),
                "the hub must use the coalescing submission");
        // Coalescing must not be allowed to swallow a pending vault or slot read.
        assertFalse(source.contains("\"vault:view\""), "hub must not touch the vault key");
        assertFalse(source.contains("\"slot:view\""), "hub must not touch the slot key");
    }

    @Test
    void theHubReadsExactlyOnePlayerStatePerOpen() throws IOException {
        String source = Files.readString(SOURCE);

        int reads = count(source, "hatches.snapshot(");
        assertEquals(1, reads, "the hub must derive both halves of its view from one snapshot read");
        assertTrue(source.contains("HubView.from("), "the hub must use the one-read derivation");
    }

    @Test
    void theHubJoinsTheShutdownCloseSequence() throws IOException {
        String plugin = Files.readString(Path.of(
                "src/main/java/io/github/salyvn/omnipet/paper/OmniPetPlugin.java"));

        assertTrue(plugin.contains("hubController.close()"), "hub must close during shutdown");
        assertTrue(plugin.contains("new HubMenuListener(hubController)"),
                "hub listener must be registered");
        assertTrue(plugin.contains("command.bindHub("), "the hub must be bound to the command");
    }

    @Test
    void tileClicksDelegateInsteadOfReimplementing() throws IOException {
        String source = Files.readString(SOURCE);

        assertTrue(source.contains("playerPets.openVault(player, 1)"));
        assertTrue(source.contains("hatchController.open(player)"));
        // The slot tile must carry its hub origin, or cancelling the purchase relocates the player to
        // the vault instead of returning them to the hub they opened it from.
        assertTrue(source.contains("slotPurchases.open(player, SlotPurchaseOrigin.hub())"));
        assertFalse(source.contains("slotPurchases.open(player, 1)"),
                "the page-based open silently returns hub visitors to the vault");
        assertTrue(source.contains("player.performCommand(\"pet help\")"));
        // No mutation path lives in the hub. The one storage reference is the limits type import.
        assertFalse(source.contains("storage.snapshot("), "the hub must not read storage itself");
        assertFalse(source.contains("storage.reconcile"), "the hub must not mutate storage");
        assertTrue(source.contains("import io.github.salyvn.omnipet.core.storage.PetStorageLimits;"),
                "the only storage touch is the limits type");
    }

    @Test
    void theStudioTileIsRecheckedAtClickTimeEvenIfVisibleAtRenderTime() throws IOException {
        String source = Files.readString(SOURCE);

        assertTrue(source.contains("player.hasPermission(AdminPetCommandParser.MANAGE_PET_PERMISSION)"),
                "the Studio tile must be permission-gated");
        int checks = count(source, "MANAGE_PET_PERMISSION");
        assertTrue(checks >= 2, "the permission must be checked at render time and at click time");
    }

    private static int count(String source, String needle) {
        int count = 0;
        int index = 0;
        while ((index = source.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }

    static {
        assert IOException.class != null;
    }
}
