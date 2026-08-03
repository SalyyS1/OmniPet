package io.github.salyvn.omnipet.paper.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.github.salyvn.omnipet.paper.gui.player.SlotPurchaseInventoryHolder;
import io.github.salyvn.omnipet.paper.gui.player.SlotPurchaseOrigin;

/**
 * The slot purchase flow returns the player to wherever they opened it from.
 *
 * <p>Before the origin existed, all three return points replayed {@code "pet " + returnPage}, so a
 * purchase opened from the hub sent the player to vault page 1 on cancel, on success, and on a stale
 * quote. The navigation assertions are made at source level, matching the sibling controller guards
 * in this package: the return points run inside Bukkit scheduler callbacks that a unit test cannot
 * drive without a server.
 */
class SlotPurchaseOriginTest {
    private static final Path PURCHASE_CONTROLLER = Path.of(
            "src/main/java/io/github/salyvn/omnipet/paper/player/PlayerSlotPurchaseController.java");
    private static final Path HUB_CONTROLLER = Path.of(
            "src/main/java/io/github/salyvn/omnipet/paper/player/PlayerHubController.java");

    @Test
    void hubOriginReturnsToTheHubAndVaultOriginReturnsToItsExactPage() {
        assertEquals("pet", SlotPurchaseOrigin.hub().returnCommand());
        assertEquals("pet 1", SlotPurchaseOrigin.vault(1).returnCommand());
        assertEquals("pet 4", SlotPurchaseOrigin.vault(4).returnCommand());
    }

    @Test
    void theOneBasedPageInvariantSurvivesTheOriginRefactor() {
        assertThrows(IllegalArgumentException.class, () -> SlotPurchaseOrigin.vault(0));
        assertThrows(IllegalArgumentException.class, () -> SlotPurchaseOrigin.vault(-1));
        // The hub placeholder satisfies the same invariant rather than relaxing it with a sentinel.
        assertEquals(1, SlotPurchaseOrigin.hub().vaultPage());
    }

    @Test
    void theHolderCarriesOriginRatherThanARawReturnPage() {
        Map<Integer, SlotPurchaseInventoryHolder.Action> actions = new HashMap<>();
        UUID transactionId = UUID.randomUUID();

        var fromHub = new SlotPurchaseInventoryHolder(
                UUID.randomUUID(), 7, 2, SlotPurchaseOrigin.hub(), transactionId,
                SlotPurchaseInventoryHolder.Stage.SELECT_PROVIDER, actions);
        var fromVault = new SlotPurchaseInventoryHolder(
                UUID.randomUUID(), 7, 2, SlotPurchaseOrigin.vault(3), transactionId,
                SlotPurchaseInventoryHolder.Stage.SELECT_PROVIDER, actions);

        assertEquals("pet", fromHub.origin().returnCommand());
        assertEquals("pet 3", fromVault.origin().returnCommand());
        // The money-handling identity survives the navigation change untouched.
        assertEquals(transactionId, fromHub.transactionId());
        assertEquals(2, fromHub.slot());
        assertEquals(7, fromHub.expectedRevision());
    }

    @Test
    void allThreeReturnPointsDispatchOnOrigin() throws IOException {
        String source = Files.readString(PURCHASE_CONTROLLER);

        // Cancel and success replay the origin's command; a stale quote reopens carrying the origin.
        assertEquals(2, count(source, "performCommand(holder.origin().returnCommand())"),
                "cancel and success must both dispatch on origin");
        assertTrue(source.contains("STALE_QUOTE) open(player, holder.origin())"),
                "the stale-quote reopen must preserve the origin");
        assertFalse(source.contains("holder.returnPage()"),
                "no return point may reconstruct navigation from a raw page");
    }

    @Test
    void theConfirmationScreenCarriesTheOriginForward() throws IOException {
        String renderer = Files.readString(Path.of(
                "src/main/java/io/github/salyvn/omnipet/paper/gui/player/SlotPurchaseMenuRenderer.java"));

        assertTrue(renderer.contains("previous.origin()"),
                "the confirm screen must inherit the selection screen's origin");
    }

    @Test
    void theHubDropsASecondConcurrentOpenAndReleasesTheGuardOnEveryFailurePath() throws IOException {
        String source = Files.readString(HUB_CONTROLLER);

        assertTrue(source.contains("ConcurrentHashMap.newKeySet()"),
                "the hub must guard concurrent opens the way its siblings do");
        assertTrue(source.contains("if (!opens.add(playerId)) return;"),
                "a second open while one is pending must be dropped");
        // A guard that is not released on failure locks the hub for the rest of the session. The
        // release sits in a finally inside the async body, not in runMain, because runMain drops its
        // task during shutdown or when the plugin is disabled.
        assertTrue(source.contains("} finally {"), "the guard must be released on every exit path");
        assertTrue(source.contains("if (!accepted) {"), "a rejected submission must release the guard");
        assertEquals(5, count(source, "opens.remove(playerId)") + count(source, "opens.clear()"),
                "every failure path plus release and shutdown must clear the guard");
    }

    @Test
    void theHubKeepsItsStaleCompletionFilterAlongsideTheNewGuard() throws IOException {
        String source = Files.readString(HUB_CONTROLLER);

        // The two serve different purposes: the tracker filters stale completions, the set rejects
        // concurrent opens. Replacing one with the other loses a guarantee.
        assertTrue(source.contains("PlayerRequestTracker requests"), "the stale filter must remain");
        assertTrue(source.contains("requests.isCurrent(playerId, request)"),
                "completions must still be filtered by request token");
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
}
