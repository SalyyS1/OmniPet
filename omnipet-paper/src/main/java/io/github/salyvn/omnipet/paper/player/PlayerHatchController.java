package io.github.salyvn.omnipet.paper.player;

import java.io.IOException;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;

import net.kyori.adventure.text.Component;

import io.github.salyvn.omnipet.core.incubation.EggEscrowStage;
import io.github.salyvn.omnipet.core.incubation.EggInventoryHand;
import io.github.salyvn.omnipet.core.incubation.HatchResult;
import io.github.salyvn.omnipet.core.storage.PetStorageLimits;
import io.github.salyvn.omnipet.paper.gui.hatch.HatchInventoryHolder;
import io.github.salyvn.omnipet.paper.gui.hatch.HatchMenuRenderer;
import io.github.salyvn.omnipet.paper.incubation.PaperIncubationCoordinator;
import io.github.salyvn.omnipet.paper.incubation.PaperIncubationServices;
import io.github.salyvn.omnipet.paper.incubation.action.IncubationActionItemController;
import io.github.salyvn.omnipet.paper.permission.PaperStorageLimitsResolver;
import io.github.salyvn.omnipet.paper.task.PerPlayerTaskQueue;
import io.github.salyvn.omnipet.paper.task.PlayerRequestTracker;
import io.github.salyvn.omnipet.paper.text.MessageKey;
import io.github.salyvn.omnipet.paper.text.Messages;

public final class PlayerHatchController {
    private static final String VIEW_TASK = "hatch:view";

    private final JavaPlugin plugin;
    private final PaperIncubationServices services;
    private final PaperIncubationCoordinator coordinator;
    private final PerPlayerTaskQueue tasks;
    private final HatchMenuRenderer renderer = new HatchMenuRenderer();
    private final PlayerRequestTracker requests = new PlayerRequestTracker();
    private final PlayerRequestTracker mutationRequests = new PlayerRequestTracker();
    private final Set<UUID> mutations = ConcurrentHashMap.newKeySet();
    private volatile PaperStorageLimitsResolver limitsResolver;
    private volatile IncubationActionItemController actionItems;
    private volatile boolean shuttingDown;

    public PlayerHatchController(
            JavaPlugin plugin,
            PaperIncubationServices services,
            PaperIncubationCoordinator coordinator,
            PaperStorageLimitsResolver limitsResolver,
            PerPlayerTaskQueue tasks) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.services = Objects.requireNonNull(services, "incubation services");
        this.coordinator = Objects.requireNonNull(coordinator, "incubation coordinator");
        this.limitsResolver = Objects.requireNonNull(limitsResolver, "storage limits resolver");
        this.tasks = Objects.requireNonNull(tasks, "player task queue");
    }

    public void updateLimitsResolver(PaperStorageLimitsResolver next) {
        limitsResolver = Objects.requireNonNull(next, "storage limits resolver");
    }

    public void setActionItems(IncubationActionItemController next) {
        actionItems = Objects.requireNonNull(next, "incubation action item controller");
    }

    public void open(Player player) {
        Objects.requireNonNull(player, "player");
        if (shuttingDown) return;
        UUID playerId = player.getUniqueId();
        long request = requests.begin(playerId);
        Inventory expectedTop = player.getOpenInventory().getTopInventory();
        try {
            boolean accepted = tasks.submitLatest(playerId, VIEW_TASK, () -> {
                if (shuttingDown || !requests.isCurrent(playerId, request)) return;
                try {
                    var state = services.hatches().snapshot(playerId);
                    complete(playerId, request, expectedTop,
                            current -> current.openInventory(renderer.render(current, state)));
                } catch (IOException | RuntimeException failure) {
                    failAsync(playerId, request, expectedTop, "hatch state could not be loaded", failure);
                }
            });
            if (!accepted && !shuttingDown) message(player, MessageKey.HATCH_VIEW_NOT_SCHEDULED);
        } catch (RuntimeException failure) {
            message(player, MessageKey.HATCH_VIEW_NOT_SCHEDULED);
            plugin.getLogger().warning("Hatch view queue failed for " + playerId + ": " + failure.getMessage());
        }
    }

    public void command(Player player, String action) {
        if (action == null || action.isBlank()) {
            open(player);
            return;
        }
        switch (action.toLowerCase(java.util.Locale.ROOT)) {
            case "main" -> start(player, EggInventoryHand.MAIN_HAND);
            case "off", "offhand" -> start(player, EggInventoryHand.OFF_HAND);
            case "claim" -> claimCurrent(player);
            case "refresh" -> open(player);
            case "use-main" -> redeemItem(player, EggInventoryHand.MAIN_HAND);
            case "use-off", "use-offhand" -> redeemItem(player, EggInventoryHand.OFF_HAND);
            default -> message(player, MessageKey.HATCH_USAGE);
        }
    }

    private void redeemItem(Player player, EggInventoryHand hand) {
        IncubationActionItemController controller = actionItems;
        if (controller == null) {
            message(player, MessageKey.HATCH_ACTION_ITEMS_UNAVAILABLE);
            return;
        }
        controller.redeem(player, hand);
        open(player);
    }

    public void click(
            Player player,
            HatchInventoryHolder holder,
            HatchInventoryHolder.Action action) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(holder, "hatch holder");
        Objects.requireNonNull(action, "hatch action");
        runMain(holder.viewerId(), current -> {
            if (!isCurrent(current, holder)) return;
            switch (action.type()) {
                case START_MAIN -> start(current, EggInventoryHand.MAIN_HAND);
                case START_OFF_HAND -> start(current, EggInventoryHand.OFF_HAND);
                case CLAIM -> claim(current, holder);
                case REFRESH -> open(current);
                case HUB -> {
                    release(current.getUniqueId());
                    current.performCommand("pet");
                }
            }
        });
    }

    public void refresh(UUID playerId) {
        runMain(playerId, player -> {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof HatchInventoryHolder) open(player);
        });
    }

    public void release(UUID playerId) {
        requests.invalidate(playerId);
        mutationRequests.invalidate(playerId);
        mutations.remove(playerId);
    }

    public void close() {
        shuttingDown = true;
        requests.clear();
        mutationRequests.clear();
        mutations.clear();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof HatchInventoryHolder) {
                player.closeInventory();
            }
        }
    }

    private void start(Player player, EggInventoryHand hand) {
        UUID playerId = player.getUniqueId();
        Inventory expectedTop = player.getOpenInventory().getTopInventory();
        if (!mutations.add(playerId)) {
            message(player, MessageKey.HATCH_ACTION_IN_FLIGHT);
            return;
        }
        long actionRequest = mutationRequests.begin(playerId);
        try {
            boolean accepted = coordinator.start(player, hand);
            message(player, accepted ? MessageKey.HATCH_START_QUEUED : MessageKey.HATCH_START_NOT_QUEUED);
            if (!accepted) {
                mutations.remove(playerId);
                mutationRequests.invalidate(playerId);
                return;
            }
            plugin.getServer().getScheduler().runTaskLater(
                    plugin, () -> finishStart(playerId, actionRequest, expectedTop), 10L);
        } catch (RuntimeException failure) {
            mutations.remove(playerId);
            mutationRequests.invalidate(playerId);
            message(player, MessageKey.HATCH_START_FAILED);
            plugin.getLogger().warning("Egg start failed for " + playerId + ": " + failure.getMessage());
        }
    }

    private void claimCurrent(Player player) {
        UUID playerId = player.getUniqueId();
        PetStorageLimits limits;
        try {
            limits = limitsResolver.resolve(player::hasPermission).limits();
        } catch (RuntimeException failure) {
            message(player, MessageKey.HATCH_LIMITS_UNRESOLVED);
            return;
        }
        if (!mutations.add(playerId)) return;
        long actionRequest = mutationRequests.begin(playerId);
        Inventory expectedTop = player.getOpenInventory().getTopInventory();
        try {
            boolean accepted = tasks.submit(playerId, () -> {
                try {
                    var state = services.hatches().snapshot(playerId);
                    if (state.incubation() == null) {
                        finishClaim(playerId, actionRequest, expectedTop, null,
                                Messages.line(MessageKey.HATCH_NO_INCUBATION));
                        return;
                    }
                    claimAsync(
                            playerId,
                            actionRequest,
                            expectedTop,
                            state.revision(),
                            state.incubation().id(),
                            limits);
                } catch (IOException | RuntimeException failure) {
                    finishClaim(playerId, actionRequest, expectedTop, null, claimFailure(failure));
                }
            });
            if (!accepted) {
                mutations.remove(playerId);
                mutationRequests.invalidate(playerId);
            }
        } catch (RuntimeException failure) {
            mutations.remove(playerId);
            mutationRequests.invalidate(playerId);
            message(player, MessageKey.HATCH_CLAIM_NOT_SCHEDULED);
        }
    }

    private void claim(Player player, HatchInventoryHolder holder) {
        UUID playerId = holder.viewerId();
        if (holder.incubationId() == null || !mutations.add(playerId)) return;
        long actionRequest = mutationRequests.begin(playerId);
        Inventory expectedTop = holder.getInventory();
        PetStorageLimits limits;
        try {
            limits = limitsResolver.resolve(player::hasPermission).limits();
        } catch (RuntimeException failure) {
            mutations.remove(playerId);
            mutationRequests.invalidate(playerId);
            message(player, MessageKey.HATCH_LIMITS_UNRESOLVED);
            return;
        }
        try {
            boolean accepted = tasks.submit(playerId, () ->
                    claimAsync(
                            playerId,
                            actionRequest,
                            expectedTop,
                            holder.expectedRevision(),
                            holder.incubationId(),
                            limits));
            if (!accepted) {
                mutations.remove(playerId);
                mutationRequests.invalidate(playerId);
            }
        } catch (RuntimeException failure) {
            mutations.remove(playerId);
            mutationRequests.invalidate(playerId);
            message(player, MessageKey.HATCH_CLAIM_NOT_SCHEDULED);
        }
    }

    private void claimAsync(
            UUID playerId,
            long actionRequest,
            Inventory expectedTop,
            long revision,
            UUID incubationId,
            PetStorageLimits limits) {
        try {
            var escrow = services.eggEscrowJournal().find(incubationId).orElse(null);
            if (escrow == null || escrow.stage() != EggEscrowStage.COMMITTED) {
                finishClaim(playerId, actionRequest, expectedTop, null,
                        Messages.line(MessageKey.HATCH_CLAIM_LOCKED));
                return;
            }
            HatchResult result = services.hatches().claim(playerId, revision, incubationId, limits);
            finishClaim(playerId, actionRequest, expectedTop, result, null);
        } catch (IOException | RuntimeException failure) {
            finishClaim(playerId, actionRequest, expectedTop, null, claimFailure(failure));
        }
    }

    private void finishStart(UUID playerId, long actionRequest, Inventory expectedTop) {
        runMain(playerId, actionRequest, player -> {
            mutations.remove(playerId);
            mutationRequests.invalidate(playerId);
            if (player.getOpenInventory().getTopInventory() == expectedTop) open(player);
        });
    }

    private void finishClaim(
            UUID playerId,
            long actionRequest,
            Inventory expectedTop,
            HatchResult result,
            Component failure) {
        runMain(playerId, actionRequest, player -> {
            mutations.remove(playerId);
            mutationRequests.invalidate(playerId);
            if (player.getOpenInventory().getTopInventory() != expectedTop) return;
            if (failure != null) {
                player.sendMessage(failure);
            } else if (result.status() == HatchResult.Status.CLAIMED) {
                message(player, MessageKey.HATCH_CLAIMED);
            } else {
                player.sendMessage(Messages.line(
                        result.succeeded()
                                ? MessageKey.HATCH_CLAIM_RESULT
                                : MessageKey.HATCH_CLAIM_RESULT_REJECTED,
                        Messages.of("status", words(result.status()))));
            }
            open(player);
        });
    }

    private void complete(
            UUID playerId,
            long request,
            Inventory expectedTop,
            java.util.function.Consumer<Player> action) {
        runMain(playerId, player -> {
            if (requests.isCurrent(playerId, request)
                    && player.getOpenInventory().getTopInventory() == expectedTop) action.accept(player);
        });
    }

    private void failAsync(
            UUID playerId,
            long request,
            Inventory expectedTop,
            String message,
            Throwable failure) {
        complete(playerId, request, expectedTop, player -> {
            message(player, MessageKey.HATCH_STATE_LOAD_FAILED);
            plugin.getLogger().warning(message + " for " + playerId + ": " + failure.getMessage());
        });
    }

    private void runMain(UUID playerId, java.util.function.Consumer<Player> action) {
        if (shuttingDown || !plugin.isEnabled()) return;
        try {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                Player player = Bukkit.getPlayer(playerId);
                if (!shuttingDown && player != null && player.isOnline()) action.accept(player);
            });
        } catch (RuntimeException failure) {
            if (!shuttingDown) plugin.getLogger().warning("Hatch main-thread dispatch failed: "
                    + failure.getMessage());
        }
    }

    private void runMain(
            UUID playerId,
            long actionRequest,
            java.util.function.Consumer<Player> action) {
        if (shuttingDown || !plugin.isEnabled()) return;
        try {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                Player player = Bukkit.getPlayer(playerId);
                if (!shuttingDown && mutationRequests.isCurrent(playerId, actionRequest)
                        && player != null && player.isOnline()) action.accept(player);
            });
        } catch (RuntimeException failure) {
            if (!shuttingDown) plugin.getLogger().warning("Hatch main-thread dispatch failed: "
                    + failure.getMessage());
        }
    }

    private boolean isCurrent(Player player, HatchInventoryHolder holder) {
        return !shuttingDown && holder.viewerId().equals(player.getUniqueId())
                && player.getOpenInventory().getTopInventory().getHolder() == holder;
    }

    private static void message(Player player, MessageKey key) {
        player.sendMessage(Messages.line(key));
    }

    /** Claim failures carry the underlying cause so a player can quote it to an operator. */
    private static Component claimFailure(Throwable failure) {
        String detail = failure.getMessage();
        return Messages.line(MessageKey.HATCH_CLAIM_FAILED, Messages.of("detail",
                detail == null || detail.isBlank() ? failure.getClass().getSimpleName() : detail));
    }

    private static String words(Enum<?> value) {
        return value.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
    }
}
