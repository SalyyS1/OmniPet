package io.github.salyvn.omnipet.paper.player;

import java.io.IOException;
import java.util.Objects;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;

import io.github.salyvn.omnipet.core.incubation.RepositoryHatchService;
import io.github.salyvn.omnipet.core.storage.PetStorageLimits;
import io.github.salyvn.omnipet.paper.command.AdminPetCommandParser;
import io.github.salyvn.omnipet.paper.gui.hub.HubInventoryHolder;
import io.github.salyvn.omnipet.paper.gui.hub.HubMenuRenderer;
import io.github.salyvn.omnipet.paper.gui.hub.HubView;
import io.github.salyvn.omnipet.paper.permission.PaperStorageLimitsResolver;
import io.github.salyvn.omnipet.paper.task.PerPlayerTaskQueue;
import io.github.salyvn.omnipet.paper.task.PlayerRequestTracker;
import io.github.salyvn.omnipet.paper.text.MessageKey;
import io.github.salyvn.omnipet.paper.text.Messages;

/**
 * Opens the hub and routes its tiles to the existing controllers.
 *
 * <p>One coalesced read per open under {@code hub:view}. {@code PerPlayerTaskQueue} coalescing removes
 * only pending tasks whose key matches, and keys are namespaced per feature, so a hub read cannot
 * swallow a pending {@code vault:view} or {@code slot:view} read.
 *
 * <p>Tiles delegate rather than reimplement: nothing here mutates player state.
 */
public final class PlayerHubController {
    private static final String HUB_VIEW_TASK = "hub:view";

    private final JavaPlugin plugin;
    private final RepositoryHatchService hatches;
    private final HubMenuRenderer renderer = new HubMenuRenderer();
    private final PerPlayerTaskQueue taskQueue;
    private final PlayerRequestTracker requests = new PlayerRequestTracker();
    private final PlayerPetController playerPets;
    private final PlayerHatchController hatchController;
    private final PlayerSlotPurchaseController slotPurchases;
    private final HubStudioTarget studio;
    private volatile PaperStorageLimitsResolver limitsResolver;
    private volatile boolean shuttingDown;

    public PlayerHubController(
            JavaPlugin plugin,
            RepositoryHatchService hatches,
            PaperStorageLimitsResolver limitsResolver,
            PerPlayerTaskQueue taskQueue,
            PlayerPetController playerPets,
            PlayerHatchController hatchController,
            PlayerSlotPurchaseController slotPurchases,
            HubStudioTarget studio) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.hatches = Objects.requireNonNull(hatches, "hatch repository service");
        this.limitsResolver = Objects.requireNonNull(limitsResolver, "storage limits resolver");
        this.taskQueue = Objects.requireNonNull(taskQueue, "player task queue");
        this.playerPets = Objects.requireNonNull(playerPets, "vault controller");
        this.hatchController = Objects.requireNonNull(hatchController, "hatch controller");
        this.slotPurchases = Objects.requireNonNull(slotPurchases, "slot purchase controller");
        this.studio = studio;
    }

    public void updateLimitsResolver(PaperStorageLimitsResolver next) {
        limitsResolver = Objects.requireNonNull(next, "storage limits resolver");
    }

    public void open(Player player) {
        Objects.requireNonNull(player, "player");
        if (shuttingDown) return;
        UUID playerId = player.getUniqueId();
        PetStorageLimits limits;
        try {
            limits = limitsResolver.resolve(player::hasPermission).limits();
        } catch (RuntimeException failure) {
            fail(player, "hub could not be loaded", failure);
            return;
        }
        boolean studioVisible = studio != null
                && player.hasPermission(AdminPetCommandParser.MANAGE_PET_PERMISSION);
        long request = requests.begin(playerId);
        Inventory expectedTop = player.getOpenInventory().getTopInventory();
        try {
            boolean accepted = taskQueue.submitLatest(playerId, HUB_VIEW_TASK, () -> {
                if (shuttingDown || !requests.isCurrent(playerId, request)) return;
                try {
                    HubView view = HubView.from(hatches.snapshot(playerId), limits);
                    runMain(() -> {
                        if (!isCurrent(player, playerId, request, expectedTop)) return;
                        player.openInventory(renderer.render(view, studioVisible));
                    });
                } catch (IOException | RuntimeException failure) {
                    runMain(() -> {
                        if (isCurrent(player, playerId, request, expectedTop)) {
                            fail(player, "hub could not be loaded", failure);
                        }
                    });
                }
            });
            if (!accepted && !shuttingDown) {
                fail(player, "hub could not be scheduled", new IllegalStateException("queue closed"));
            }
        } catch (RuntimeException failure) {
            if (!shuttingDown) fail(player, "hub could not be scheduled", failure);
        }
    }

    public void click(Player player, HubInventoryHolder holder, HubInventoryHolder.Action action) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(holder, "hub holder");
        Objects.requireNonNull(action, "hub action");
        if (shuttingDown) return;
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || !holder.viewerId().equals(player.getUniqueId())) return;
            if (player.getOpenInventory().getTopInventory().getHolder() != holder) return;
            switch (action) {
                case VAULT -> playerPets.openVault(player, 1);
                case HATCH -> hatchController.open(player);
                case SLOTS -> slotPurchases.open(player, 1);
                case HELP -> player.performCommand("pet help");
                case STUDIO -> openStudio(player);
            }
        });
    }

    public void release(UUID playerId) {
        requests.invalidate(playerId);
    }

    public void close() {
        shuttingDown = true;
        requests.clear();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof HubInventoryHolder) {
                player.closeInventory();
            }
        }
    }

    private void openStudio(Player player) {
        // Re-checked at click time as well as render time: a permission can be revoked while the
        // hub is open.
        if (studio == null || !player.hasPermission(AdminPetCommandParser.MANAGE_PET_PERMISSION)) return;
        playerPets.release(player.getUniqueId());
        studio.openBrowse(player);
    }

    private boolean isCurrent(Player player, UUID playerId, long request, Inventory expectedTop) {
        return !shuttingDown
                && plugin.isEnabled()
                && player.isOnline()
                && playerId.equals(player.getUniqueId())
                && requests.isCurrent(playerId, request)
                && player.getOpenInventory().getTopInventory() == expectedTop;
    }

    private void runMain(Runnable task) {
        if (shuttingDown || !plugin.isEnabled()) return;
        try {
            plugin.getServer().getScheduler().runTask(plugin, task);
        } catch (RuntimeException failure) {
            if (!shuttingDown && plugin.isEnabled()) throw failure;
        }
    }

    private void fail(Player player, String message, Throwable failure) {
        player.sendMessage(Messages.line(MessageKey.HUB_FAILURE, Messages.of("detail", message)));
        plugin.getLogger().warning(message + " for " + player.getUniqueId() + ": " + failure.getMessage());
    }

    /** The Studio entry point, kept as a seam so the hub is testable without the Studio. */
    @FunctionalInterface
    public interface HubStudioTarget {
        void openBrowse(Player player);
    }
}
