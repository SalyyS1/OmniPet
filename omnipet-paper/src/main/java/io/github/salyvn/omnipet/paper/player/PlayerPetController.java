package io.github.salyvn.omnipet.paper.player;

import java.io.IOException;
import java.time.Duration;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import io.github.salyvn.omnipet.core.persistence.StaleRevisionException;
import io.github.salyvn.omnipet.core.storage.PetStorageLimits;
import io.github.salyvn.omnipet.core.storage.PetStorageResult;
import io.github.salyvn.omnipet.core.storage.RepositoryPetStorageService;
import io.github.salyvn.omnipet.paper.gui.player.PlayerPetInventoryHolder;
import io.github.salyvn.omnipet.paper.gui.player.PlayerPetMenuRenderer;
import io.github.salyvn.omnipet.paper.permission.PaperStorageLimitsResolver;

public final class PlayerPetController {
    private static final String VAULT_VIEW_TASK = "vault-view";
    private static final String LIMIT_RECONCILE_TASK = "limit-reconcile";

    private final JavaPlugin plugin;
    private final RepositoryPetStorageService storage;
    private final PlayerPetMenuRenderer renderer;
    private final PlayerPetAsyncQueue asyncQueue;
    private final PlayerPetRequestTracker requests;
    private final Set<UUID> mutationsInFlight;
    private volatile PaperStorageLimitsResolver limitsResolver;
    private volatile boolean shuttingDown;

    public PlayerPetController(
            JavaPlugin plugin,
            RepositoryPetStorageService storage,
            PaperStorageLimitsResolver limitsResolver) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.storage = Objects.requireNonNull(storage, "storage");
        this.limitsResolver = Objects.requireNonNull(limitsResolver, "storage limits resolver");
        this.renderer = new PlayerPetMenuRenderer();
        this.asyncQueue = new PlayerPetAsyncQueue(task ->
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, task));
        this.requests = new PlayerPetRequestTracker();
        this.mutationsInFlight = ConcurrentHashMap.newKeySet();
    }

    public void updateLimitsResolver(PaperStorageLimitsResolver next) {
        limitsResolver = Objects.requireNonNull(next, "storage limits resolver");
    }

    public void openVault(Player player, int page) {
        Objects.requireNonNull(player, "player");
        UUID playerId = player.getUniqueId();
        PetStorageLimits limits;
        try {
            limits = resolveLimits(player);
        } catch (RuntimeException failure) {
            failClosed(player, "vault could not be loaded", failure);
            return;
        }
        long request = requests.begin(playerId);
        Inventory expectedTop = player.getOpenInventory().getTopInventory();
        try {
            asyncQueue.submitLatest(playerId, VAULT_VIEW_TASK, () -> {
                if (shuttingDown || !requests.isCurrent(playerId, request)) return;
                try {
                    var snapshot = storage.snapshot(playerId, limits);
                    completeUi(player, playerId, request, expectedTop, false, () ->
                            player.openInventory(renderer.render(player, snapshot, page)));
                } catch (IOException | RuntimeException failure) {
                    failAsync(player, playerId, request, "vault could not be loaded", failure);
                }
            });
        } catch (RuntimeException failure) {
            if (!shuttingDown) failClosed(player, "vault request could not be scheduled", failure);
        }
    }

    public void click(
            Player player,
            PlayerPetInventoryHolder holder,
            PlayerPetInventoryHolder.Action action) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(holder, "holder");
        Objects.requireNonNull(action, "action");
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!isCurrentVault(player, holder)) return;
            switch (action.type()) {
                case PREVIOUS -> openVault(player, holder.page() - 1);
                case NEXT -> openVault(player, holder.page() + 1);
                case PET -> toggle(player, holder, action);
                case PURCHASE_SLOT -> player.performCommand("pet slot " + holder.page());
            }
        });
    }

    public void reconcile(Player player) {
        Objects.requireNonNull(player, "player");
        UUID playerId = player.getUniqueId();
        PetStorageLimits limits;
        try {
            limits = resolveLimits(player);
        } catch (RuntimeException failure) {
            reportFailure(player, "storage reconciliation failed", failure);
            return;
        }
        try {
            asyncQueue.submitLatest(playerId, LIMIT_RECONCILE_TASK,
                    () -> reconcileAsync(player, playerId, limits));
        } catch (RuntimeException failure) {
            if (!shuttingDown) reportFailure(player, "storage reconciliation could not be scheduled", failure);
        }
    }

    public void release(UUID playerId) {
        requests.invalidate(playerId);
    }

    public void closeAll() {
        shuttingDown = true;
        requests.clear();
        asyncQueue.shutdown();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof PlayerPetInventoryHolder) {
                player.closeInventory();
            }
        }
        try {
            if (!asyncQueue.awaitIdle(Duration.ofSeconds(10))) {
                plugin.getLogger().severe("Timed out waiting for active player storage writes to finish during disable.");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            plugin.getLogger().severe("Interrupted while waiting for active player storage writes during disable.");
        }
        mutationsInFlight.clear();
    }

    private void toggle(Player player, PlayerPetInventoryHolder holder, PlayerPetInventoryHolder.Action action) {
        UUID playerId = player.getUniqueId();
        if (!mutationsInFlight.add(playerId)) {
            player.sendMessage(Component.text("OmniPet: a pet change is already processing.", NamedTextColor.YELLOW));
            return;
        }
        PetStorageLimits limits;
        try {
            limits = resolveLimits(player);
        } catch (RuntimeException failure) {
            mutationsInFlight.remove(playerId);
            failClosed(player, "pet state could not be changed", failure);
            return;
        }
        Inventory expectedTop = holder.getInventory();
        long request = requests.begin(playerId);
        boolean accepted;
        try {
            accepted = asyncQueue.submit(playerId, () -> {
                try {
                    PetStorageResult result = action.active()
                            ? storage.deactivate(playerId, holder.expectedRevision(), action.petId(), limits)
                            : storage.activate(playerId, holder.expectedRevision(), action.petId(), limits);
                    completeUi(player, playerId, request, expectedTop, true,
                            () -> showMutationResult(player, holder.page(), result));
                } catch (StaleRevisionException stale) {
                    completeUi(player, playerId, request, expectedTop, true, () -> {
                        player.sendMessage(Component.text(
                                "OmniPet vault changed; refreshed the page.", NamedTextColor.YELLOW));
                        openVault(player, holder.page());
                    });
                } catch (IOException | RuntimeException failure) {
                    failAsync(player, playerId, request, true, "pet state could not be changed", failure);
                }
            });
        } catch (RuntimeException failure) {
            mutationsInFlight.remove(playerId);
            failClosed(player, "pet change could not be scheduled", failure);
            return;
        }
        if (!accepted) mutationsInFlight.remove(playerId);
    }

    private PetStorageLimits resolveLimits(Player player) {
        return limitsResolver.resolve(player::hasPermission).limits();
    }

    private void reconcileAsync(Player player, UUID playerId, PetStorageLimits limits) {
        try {
            PetStorageResult result = null;
            for (int attempt = 0; attempt < 2; attempt++) {
                if (shuttingDown) return;
                var snapshot = storage.snapshot(playerId, limits);
                if (shuttingDown) return;
                try {
                    result = storage.reconcileLimits(playerId, snapshot.revision(), limits);
                    break;
                } catch (StaleRevisionException stale) {
                    if (attempt == 1) throw stale;
                }
            }
            PetStorageResult completed = result;
            runMain(() -> {
                if (!isAvailable(player, playerId) || completed == null) return;
                if (!completed.recalledPetIds().isEmpty()) {
                    player.sendMessage(Component.text(
                            "OmniPet recalled " + completed.recalledPetIds().size()
                                    + " overflow active pet(s).",
                            NamedTextColor.YELLOW));
                }
            });
        } catch (IOException | RuntimeException failure) {
            runMain(() -> {
                if (isAvailable(player, playerId)) {
                    reportFailure(player, "storage reconciliation failed", failure);
                }
            });
        }
    }

    private void showMutationResult(Player player, int page, PetStorageResult result) {
        if (!result.succeeded()) {
            player.sendMessage(Component.text(
                    "OmniPet: " + result.status().name().toLowerCase().replace('_', ' '),
                    NamedTextColor.RED));
        }
        player.openInventory(renderer.render(player, result.snapshot(), page));
    }

    private void completeUi(
            Player player,
            UUID playerId,
            long request,
            Inventory expectedTop,
            boolean releaseMutation,
            Runnable completion) {
        runMain(() -> {
            if (releaseMutation) mutationsInFlight.remove(playerId);
            if (!requests.isCurrent(playerId, request)
                    || !isAvailable(player, playerId)
                    || player.getOpenInventory().getTopInventory() != expectedTop) return;
            try {
                completion.run();
            } catch (RuntimeException failure) {
                failClosed(player, "vault view could not be updated", failure);
            }
        });
    }

    private void failAsync(Player player, UUID playerId, long request, String message, Throwable failure) {
        failAsync(player, playerId, request, false, message, failure);
    }

    private void failAsync(
            Player player,
            UUID playerId,
            long request,
            boolean releaseMutation,
            String message,
            Throwable failure) {
        runMain(() -> {
            if (releaseMutation) mutationsInFlight.remove(playerId);
            if (requests.isCurrent(playerId, request) && isAvailable(player, playerId)) {
                failClosed(player, message, failure);
            }
        });
    }

    private void runMain(Runnable task) {
        if (shuttingDown || !plugin.isEnabled()) return;
        try {
            plugin.getServer().getScheduler().runTask(plugin, task);
        } catch (RuntimeException failure) {
            if (!shuttingDown && plugin.isEnabled()) throw failure;
        }
    }

    private boolean isCurrentVault(Player player, PlayerPetInventoryHolder holder) {
        return isAvailable(player, holder.viewerId())
                && player.getOpenInventory().getTopInventory().getHolder() == holder;
    }

    private boolean isAvailable(Player player, UUID playerId) {
        return !shuttingDown && plugin.isEnabled() && player.isOnline() && playerId.equals(player.getUniqueId());
    }

    private void failClosed(Player player, String message, Throwable failure) {
        player.closeInventory();
        reportFailure(player, message, failure);
    }

    private void reportFailure(Player player, String message, Throwable failure) {
        player.sendMessage(Component.text("OmniPet: " + message + ".", NamedTextColor.RED));
        plugin.getLogger().warning(message + " for " + player.getUniqueId() + ": " + failure.getMessage());
    }
}
