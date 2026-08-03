package io.github.salyvn.omnipet.paper.player;

import java.io.IOException;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;

import io.github.salyvn.omnipet.core.persistence.StaleRevisionException;
import io.github.salyvn.omnipet.core.storage.PetStorageLimits;
import io.github.salyvn.omnipet.core.storage.PetStorageResult;
import io.github.salyvn.omnipet.core.storage.PetStorageSnapshot;
import io.github.salyvn.omnipet.core.storage.RepositoryPetStorageService;
import io.github.salyvn.omnipet.paper.gui.player.PlayerPetInventoryHolder;
import io.github.salyvn.omnipet.paper.feedback.Feedback;
import io.github.salyvn.omnipet.paper.feedback.FeedbackEvent;
import io.github.salyvn.omnipet.paper.gui.player.PlayerPetMenuRenderer;
import io.github.salyvn.omnipet.paper.gui.player.VaultViewState;
import io.github.salyvn.omnipet.paper.permission.PaperStorageLimitsResolver;
import io.github.salyvn.omnipet.paper.task.PerPlayerTaskQueue;
import io.github.salyvn.omnipet.paper.task.PlayerRequestTracker;
import io.github.salyvn.omnipet.paper.text.Displays;
import io.github.salyvn.omnipet.paper.text.MessageKey;
import io.github.salyvn.omnipet.paper.text.Messages;

public final class PlayerPetController {
    private static final String VAULT_VIEW_TASK = "vault:view";

    private final JavaPlugin plugin;
    private final RepositoryPetStorageService storage;
    private final PlayerPetMenuRenderer renderer;
    private final PerPlayerTaskQueue taskQueue;
    private final PlayerRequestTracker requests;
    private final Set<UUID> mutationsInFlight;
    private final Consumer<PetStorageSnapshot> runtimeSnapshots;
    private volatile PaperStorageLimitsResolver limitsResolver;
    private volatile boolean shuttingDown;

    public PlayerPetController(
            JavaPlugin plugin,
            RepositoryPetStorageService storage,
            PaperStorageLimitsResolver limitsResolver,
            PerPlayerTaskQueue taskQueue) {
        this(plugin, storage, limitsResolver, taskQueue, ignored -> {});
    }

    public PlayerPetController(
            JavaPlugin plugin,
            RepositoryPetStorageService storage,
            PaperStorageLimitsResolver limitsResolver,
            PerPlayerTaskQueue taskQueue,
            Consumer<PetStorageSnapshot> runtimeSnapshots) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.storage = Objects.requireNonNull(storage, "storage");
        this.limitsResolver = Objects.requireNonNull(limitsResolver, "storage limits resolver");
        this.renderer = new PlayerPetMenuRenderer();
        this.taskQueue = Objects.requireNonNull(taskQueue, "player task queue");
        this.requests = new PlayerRequestTracker();
        this.mutationsInFlight = ConcurrentHashMap.newKeySet();
        this.runtimeSnapshots = Objects.requireNonNull(runtimeSnapshots, "runtime snapshot listener");
    }

    public void updateLimitsResolver(PaperStorageLimitsResolver next) {
        limitsResolver = Objects.requireNonNull(next, "storage limits resolver");
    }

    public void openVault(Player player, int page) {
        openVault(player, VaultViewState.page(page));
    }

    public void openVault(Player player, VaultViewState view) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(view, "vault view state");
        if (shuttingDown) return;
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
            boolean accepted = taskQueue.submitLatest(playerId, VAULT_VIEW_TASK, () -> {
                if (shuttingDown || !requests.isCurrent(playerId, request)) return;
                try {
                    var snapshot = storage.snapshot(playerId, limits);
                    publishSnapshot(snapshot);
                    completeUi(player, playerId, request, expectedTop, false, () ->
                            player.openInventory(renderer.render(player, snapshot, view)));
                } catch (IOException | RuntimeException failure) {
                    failAsync(player, playerId, request, "vault could not be loaded", failure);
                }
            });
            if (!accepted && !shuttingDown) {
                failClosed(player, "vault request could not be scheduled", new IllegalStateException("queue closed"));
            }
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
        if (shuttingDown) return;
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!isCurrentVault(player, holder)) return;
            switch (action.type()) {
                case PREVIOUS -> openVault(player, holder.view().withPage(holder.page() - 1));
                case NEXT -> openVault(player, holder.view().withPage(holder.page() + 1));
                case SORT -> cycleView(player, holder.view().cycleSort());
                case FILTER -> cycleView(player, holder.view().cycleFilter());
                case PET -> toggle(player, holder, action);
                case PURCHASE_SLOT -> player.performCommand("pet slot " + holder.page());
                case HUB -> {
                    release(player.getUniqueId());
                    player.performCommand("pet");
                }
            }
        });
    }

    public void reconcile(Player player) {
        Objects.requireNonNull(player, "player");
        if (shuttingDown) return;
        UUID playerId = player.getUniqueId();
        PetStorageLimits limits;
        try {
            limits = resolveLimits(player);
        } catch (RuntimeException failure) {
            reportFailure(player, "storage reconciliation failed", failure);
            return;
        }
        try {
            boolean accepted = taskQueue.submit(playerId, () -> reconcileAsync(player, playerId, limits));
            if (!accepted && !shuttingDown) {
                reportFailure(player, "storage reconciliation could not be scheduled",
                        new IllegalStateException("queue closed"));
            }
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
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof PlayerPetInventoryHolder) {
                player.closeInventory();
            }
        }
        mutationsInFlight.clear();
    }

    private void cycleView(Player player, VaultViewState next) {
        Feedback.progress(player, FeedbackEvent.VAULT_VIEW_CHANGED);
        openVault(player, next);
    }

    private void toggle(Player player, PlayerPetInventoryHolder holder, PlayerPetInventoryHolder.Action action) {
        UUID playerId = player.getUniqueId();
        if (!mutationsInFlight.add(playerId)) {
            player.sendMessage(Messages.line(MessageKey.VAULT_CHANGE_IN_FLIGHT));
            Feedback.blocked(player, FeedbackEvent.PET_REQUEST_IN_FLIGHT);
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
            accepted = taskQueue.submit(playerId, () -> {
                try {
                    PetStorageResult result = action.active()
                            ? storage.deactivate(playerId, holder.expectedRevision(), action.petId(), limits)
                            : storage.activate(playerId, holder.expectedRevision(), action.petId(), limits);
                    publishSnapshot(result.snapshot());
                    completeUi(player, playerId, request, expectedTop, true,
                            () -> showMutationResult(player, holder.view(), result, action.active()));
                } catch (StaleRevisionException stale) {
                    completeUi(player, playerId, request, expectedTop, true, () -> {
                        player.sendMessage(Messages.line(MessageKey.VAULT_REFRESHED));
                        openVault(player, holder.view());
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
                var snapshot = storage.snapshot(playerId, limits);
                try {
                    result = storage.reconcileLimits(playerId, snapshot.revision(), limits);
                    break;
                } catch (StaleRevisionException stale) {
                    if (attempt == 1) throw stale;
                }
            }
            PetStorageResult completed = result;
            if (completed != null) publishSnapshot(completed.snapshot());
            runMain(() -> {
                if (!isAvailable(player, playerId) || completed == null) return;
                if (!completed.recalledPetIds().isEmpty()) {
                    player.sendMessage(Messages.line(MessageKey.VAULT_OVERFLOW_RECALLED,
                            Messages.of("amount", completed.recalledPetIds().size())));
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

    private void showMutationResult(
            Player player, VaultViewState view, PetStorageResult result, boolean wasActive) {
        if (!result.succeeded()) {
            player.sendMessage(Messages.line(MessageKey.VAULT_MUTATION_REJECTED,
                    Messages.of("status", words(result.status()))));
            Feedback.failure(player, FeedbackEvent.PET_TOGGLE_REJECTED);
        } else {
            // Activating and recalling previously differed only by a silent re-render.
            Feedback.success(player, wasActive ? FeedbackEvent.PET_RECALLED : FeedbackEvent.PET_ACTIVATED);
        }
        // Re-rendered at the player's own sort and filter: activating a pet must not silently
        // reset the view they arranged.
        player.openInventory(renderer.render(player, result.snapshot(), view));
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
        player.sendMessage(Messages.line(MessageKey.VAULT_FAILURE, Messages.of("detail", message)));
        plugin.getLogger().warning(message + " for " + player.getUniqueId() + ": " + failure.getMessage());
    }

    private static String words(Enum<?> value) {
        return Displays.words(value);
    }

    private void publishSnapshot(PetStorageSnapshot snapshot) {
        try {
            runtimeSnapshots.accept(snapshot);
        } catch (RuntimeException | LinkageError failure) {
            plugin.getLogger().warning("OmniPet runtime snapshot refresh failed for "
                    + snapshot.playerId() + ": " + failure.getMessage());
        }
    }
}
