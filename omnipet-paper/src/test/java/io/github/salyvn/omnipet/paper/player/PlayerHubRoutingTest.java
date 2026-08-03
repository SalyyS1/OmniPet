package io.github.salyvn.omnipet.paper.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.salyvn.omnipet.paper.gui.hub.HubInventoryHolder;

/**
 * Every hub tile has a destination, and each one delegates rather than reimplementing.
 *
 * <p>The hub is the entry point players reach by typing {@code /pet} with no arguments, so a tile that
 * routes to the wrong screen — as the slot tile did — is the most visible kind of bug in the plugin.
 */
class PlayerHubRoutingTest {
    private static final Path SOURCE = Path.of(
            "src/main/java/io/github/salyvn/omnipet/paper/player/PlayerHubController.java");

    @Test
    void everyTileActionHasAnExplicitRoute() throws IOException {
        String source = Files.readString(SOURCE);

        // A missing branch would compile but silently do nothing on click, since the switch is over an
        // enum inside a lambda rather than an exhaustive expression.
        for (HubInventoryHolder.Action action : HubInventoryHolder.Action.values()) {
            assertTrue(source.contains("case " + action.name() + " ->"),
                    action + " has no route in PlayerHubController.click");
        }
    }

    @Test
    void theRouteTableIsExactlyWhatTheTilesPromise() throws IOException {
        String source = Files.readString(SOURCE);

        assertTrue(source.contains("case VAULT -> playerPets.openVault(player, 1)"));
        assertTrue(source.contains("case HATCH -> hatchController.open(player)"));
        assertTrue(source.contains("case SLOTS -> slotPurchases.open(player, SlotPurchaseOrigin.hub())"));
        assertTrue(source.contains("case HELP -> player.performCommand(\"pet help\")"));
        assertTrue(source.contains("case STUDIO -> openStudio(player)"));
    }

    @Test
    void theSlotTileCarriesItsHubOriginSoCancellingReturnsToTheHub() throws IOException {
        String source = Files.readString(SOURCE);

        assertTrue(source.contains("SlotPurchaseOrigin.hub()"));
        assertFalse(source.contains("slotPurchases.open(player, 1)"),
                "the page-based open sends a hub visitor to the vault on cancel");
    }

    @Test
    void theHubOnlyReadsAndNeverMutates() throws IOException {
        String source = Files.readString(SOURCE);

        // Tiles delegate; nothing here writes player state. The one storage reference is a type import.
        assertFalse(source.contains("storage.snapshot("));
        assertFalse(source.contains("storage.reconcile"));
        assertFalse(source.contains(".activate("));
        assertFalse(source.contains(".deactivate("));
    }

    @Test
    void theStudioTileIsTheOnlyPermissionGatedRoute() throws IOException {
        String source = Files.readString(SOURCE);

        // Checked at click time as well as render time, since a permission can be revoked while the
        // hub sits open.
        assertTrue(source.contains("openStudio(player)"));
        long checks = Arrays.stream(source.split("\n"))
                .filter(line -> line.contains("MANAGE_PET_PERMISSION"))
                .count();
        assertTrue(checks >= 2, "permission must be checked at render time and again at click time");
    }

    @Test
    void clickIsGuardedByViewerIdentityAndTheOpenInventory() throws IOException {
        String source = Files.readString(SOURCE);

        // Without both, a stale holder from a closed menu could still route a click.
        assertTrue(source.contains("holder.viewerId().equals(player.getUniqueId())"));
        assertTrue(source.contains("player.getOpenInventory().getTopInventory().getHolder() != holder"));
        assertTrue(source.contains("!player.isOnline()"));
    }

    @Test
    void everyTileDestinationIsReachableFromTheHubActionEnum() {
        // Pins the tile set itself: adding an action without a route fails the first test, and removing
        // one without removing its tile fails here.
        assertEquals(
                List.of("VAULT", "HATCH", "SLOTS", "HELP", "STUDIO"),
                Arrays.stream(HubInventoryHolder.Action.values()).map(Enum::name).toList());
    }
}
