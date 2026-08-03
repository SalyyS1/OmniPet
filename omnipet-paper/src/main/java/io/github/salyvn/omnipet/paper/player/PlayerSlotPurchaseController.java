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

import io.github.salyvn.omnipet.core.economy.SlotPurchaseQuote;
import io.github.salyvn.omnipet.core.economy.SlotPurchaseResult;
import io.github.salyvn.omnipet.core.economy.SlotUnlockRule;
import io.github.salyvn.omnipet.core.economy.SlotUnlockService;
import io.github.salyvn.omnipet.core.economy.ExternalEntitlementResult;
import io.github.salyvn.omnipet.core.persistence.PlayerStateRepository;
import io.github.salyvn.omnipet.paper.config.Phase4PaperConfig;
import io.github.salyvn.omnipet.paper.economy.PaperEconomyProviderRegistry;
import io.github.salyvn.omnipet.paper.entitlement.SlotEntitlementSynchronizer;
import io.github.salyvn.omnipet.paper.feedback.Feedback;
import io.github.salyvn.omnipet.paper.feedback.FeedbackEvent;
import io.github.salyvn.omnipet.paper.gui.player.SlotBalanceDisplay;
import io.github.salyvn.omnipet.paper.gui.player.SlotPurchaseInventoryHolder;
import io.github.salyvn.omnipet.paper.gui.player.SlotPurchaseMenuRenderer;
import io.github.salyvn.omnipet.paper.gui.player.SlotPurchaseOrigin;
import io.github.salyvn.omnipet.paper.permission.PaperStorageLimitsResolver;
import io.github.salyvn.omnipet.paper.task.PerPlayerTaskQueue;
import io.github.salyvn.omnipet.paper.task.PlayerRequestTracker;
import io.github.salyvn.omnipet.paper.text.Displays;
import io.github.salyvn.omnipet.paper.text.MessageKey;
import io.github.salyvn.omnipet.paper.text.Messages;

public final class PlayerSlotPurchaseController {
    private static final String VIEW_TASK = "slot:view";

    private final JavaPlugin plugin;
    private final PlayerStateRepository playerStates;
    private final SlotUnlockService purchases;
    private final PaperEconomyProviderRegistry providers;
    private final SlotEntitlementSynchronizer entitlements;
    private final SlotPurchaseMenuRenderer renderer = new SlotPurchaseMenuRenderer();
    private final PerPlayerTaskQueue taskQueue;
    private final PlayerRequestTracker requests = new PlayerRequestTracker();
    private final Set<UUID> mutations = ConcurrentHashMap.newKeySet();
    private volatile PaperStorageLimitsResolver limitsResolver;
    private volatile boolean shuttingDown;

    public PlayerSlotPurchaseController(
            JavaPlugin plugin,
            PlayerStateRepository playerStates,
            SlotUnlockService purchases,
            PaperEconomyProviderRegistry providers,
            SlotEntitlementSynchronizer entitlements,
            PaperStorageLimitsResolver limitsResolver,
            PerPlayerTaskQueue taskQueue) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.playerStates = Objects.requireNonNull(playerStates, "player state repository");
        this.purchases = Objects.requireNonNull(purchases, "slot unlock service");
        this.providers = Objects.requireNonNull(providers, "economy provider registry");
        this.entitlements = Objects.requireNonNull(entitlements, "slot entitlement synchronizer");
        this.limitsResolver = Objects.requireNonNull(limitsResolver, "storage limits resolver");
        this.taskQueue = Objects.requireNonNull(taskQueue, "player task queue");
    }

    public void updateLimitsResolver(PaperStorageLimitsResolver next) {
        limitsResolver = Objects.requireNonNull(next, "storage limits resolver");
    }

    public void open(Player player, int returnPage) {
        open(player, SlotPurchaseOrigin.vault(returnPage));
    }

    public void open(Player player, SlotPurchaseOrigin origin) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(origin, "slot purchase origin");
        if (shuttingDown) return;
        PaperStorageLimitsResolver resolver = limitsResolver;
        Phase4PaperConfig.ActiveSlots config = resolver.activeSlots();
        var observedLimits = resolver.resolve(player::hasPermission).limits();
        var eligibility = config.unlocks().entrySet().stream().collect(java.util.stream.Collectors.toMap(
                java.util.Map.Entry::getKey,
                entry -> entry.getValue().eligible(player::hasPermission)));
        UUID playerId = player.getUniqueId();
        long request = requests.begin(playerId);
        Inventory expectedTop = player.getOpenInventory().getTopInventory();
        try {
            boolean accepted = taskQueue.submitLatest(playerId, VIEW_TASK, () -> {
                if (shuttingDown || !requests.isCurrent(playerId, request)) return;
                try {
                var state = playerStates.snapshot(playerId);
                if (state.activeSlotCount() < config.base()) {
                    complete(player, request, expectedTop, () -> message(player, MessageKey.SLOT_BASELINE_PENDING));
                    return;
                }
                if (config.entitlement().policy().mode()
                                != io.github.salyvn.omnipet.core.storage.SlotEntitlementMode.OMNIPET
                        && observedLimits.observedExternalActiveSlotCount() != state.activeSlotCount()) {
                    complete(player, request, expectedTop, () -> message(player, MessageKey.SLOT_COUNTS_DIFFER));
                    return;
                }
                int slot = state.activeSlotCount() + 1;
                Phase4PaperConfig.SlotUnlock unlock = config.unlock(slot).orElse(null);
                if (slot > config.max() || unlock == null) {
                    complete(player, request, expectedTop, () -> message(player, MessageKey.SLOT_NO_UPGRADE));
                    return;
                }
                if (!eligibility.getOrDefault(slot, false)) {
                    complete(player, request, expectedTop, () -> player.sendMessage(Messages.line(
                            MessageKey.SLOT_NOT_ELIGIBLE, Messages.of("amount", slot))));
                    return;
                }
                if (!entitlements.available(config.entitlement())) {
                    complete(player, request, expectedTop, () -> player.sendMessage(Messages.line(
                            MessageKey.SLOT_ENTITLEMENT_UNAVAILABLE,
                            Messages.of("detail", entitlements.unavailableReason(config.entitlement())))));
                    return;
                }
                java.util.Map<io.github.salyvn.omnipet.core.economy.EconomyProvider, SlotBalanceDisplay> balances =
                        unlock.costs().keySet().stream().collect(java.util.stream.Collectors.toMap(
                                provider -> provider,
                                provider -> {
                                    var result = providers.balance(playerId, provider);
                                    return result.status()
                                                    == io.github.salyvn.omnipet.core.economy.EconomyBalanceResult.Status.AVAILABLE
                                            ? SlotBalanceDisplay.available(result.balance())
                                            : SlotBalanceDisplay.unavailable(result.detail());
                                }));
                complete(player, request, expectedTop, () -> player.openInventory(renderer.selection(
                        player,
                        state.revision(),
                        slot,
                        origin,
                        UUID.randomUUID(),
                        unlock,
                        provider -> providers.find(provider).isPresent(),
                        providers::diagnostic,
                        balances::get)));
                } catch (IOException | RuntimeException failure) {
                    complete(player, request, expectedTop,
                            () -> fail(player, "slot options could not be loaded", failure));
                }
            });
            if (!accepted && !shuttingDown) {
                fail(player, "slot options could not be scheduled", new IllegalStateException("queue closed"));
            }
        } catch (RuntimeException failure) {
            if (!shuttingDown) fail(player, "slot options could not be scheduled", failure);
        }
    }

    public void click(
            Player player,
            SlotPurchaseInventoryHolder holder,
            SlotPurchaseInventoryHolder.Action action) {
        if (shuttingDown) return;
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!isCurrent(player, holder)) return;
            switch (action.type()) {
                case CANCEL -> player.performCommand(holder.origin().returnCommand());
                case SELECT -> select(player, holder, action);
                case CONFIRM -> confirm(player, holder, action);
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
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof SlotPurchaseInventoryHolder) {
                player.closeInventory();
            }
        }
        mutations.clear();
    }

    private void select(Player player, SlotPurchaseInventoryHolder holder, SlotPurchaseInventoryHolder.Action action) {
        if (providers.find(action.amount().provider()).isEmpty()) {
            player.sendMessage(Messages.line(MessageKey.SLOT_PROVIDER_UNAVAILABLE,
                    Messages.of("detail", providers.diagnostic(action.amount().provider()))));
            return;
        }
        player.openInventory(renderer.confirmation(player, holder, action.amount()));
    }

    private void confirm(Player player, SlotPurchaseInventoryHolder holder, SlotPurchaseInventoryHolder.Action action) {
        UUID playerId = player.getUniqueId();
        Phase4PaperConfig.ActiveSlots config = limitsResolver.activeSlots();
        Phase4PaperConfig.SlotUnlock current = config.unlock(holder.slot()).orElse(null);
        if (current == null || !action.amount().equals(current.costs().get(action.amount().provider()))) {
            message(player, MessageKey.SLOT_PRICE_CHANGED);
            return;
        }
        if (!current.eligible(player::hasPermission) || !entitlements.available(config.entitlement())) {
            message(player, MessageKey.SLOT_REQUIREMENTS_GONE);
            return;
        }
        if (!mutations.add(playerId)) {
            message(player, MessageKey.SLOT_PURCHASE_IN_FLIGHT);
            Feedback.blocked(player, FeedbackEvent.SLOT_PURCHASE_IN_FLIGHT);
            return;
        }
        Inventory expectedTop = holder.getInventory();
        long request = requests.begin(playerId);
        try {
            boolean accepted = taskQueue.submit(playerId, () -> purchaseAsync(
                    player, holder, action, config, request, expectedTop));
            if (!accepted) mutations.remove(playerId);
        } catch (RuntimeException failure) {
            mutations.remove(playerId);
            fail(player, "slot purchase could not be scheduled", failure);
        }
    }

    private void purchaseAsync(
            Player player,
            SlotPurchaseInventoryHolder holder,
            SlotPurchaseInventoryHolder.Action action,
            Phase4PaperConfig.ActiveSlots config,
            long request,
            Inventory expectedTop) {
        try {
            SlotPurchaseResult result = purchases.purchase(
                    holder.transactionId(),
                    new SlotPurchaseQuote(
                            holder.viewerId(),
                            holder.expectedRevision(),
                            new SlotUnlockRule(holder.slot(), action.amount())),
                    config.entitlement().policy().mode()
                            != io.github.salyvn.omnipet.core.storage.SlotEntitlementMode.OMNIPET);
            if (result.status() == SlotPurchaseResult.Status.ENTITLEMENT_SYNC_PENDING) {
                result = purchases.synchronizeExternalEntitlement(
                        holder.transactionId(),
                        transaction -> {
                            var sync = entitlements.grant(
                                    transaction.playerId(),
                                    transaction.slot(),
                                    config.entitlement());
                            return sync.succeeded()
                                    ? ExternalEntitlementResult.completed(sync.detail())
                                    : ExternalEntitlementResult.pending(sync.detail());
                        });
            }
            completeMutation(player, holder, result, request, expectedTop);
        } catch (IOException | RuntimeException failure) {
            finishMutation(player, holder.viewerId(), request, expectedTop,
                    () -> fail(player, "slot purchase failed", failure));
        }
    }

    private void completeMutation(
            Player player,
            SlotPurchaseInventoryHolder holder,
            SlotPurchaseResult result,
            long request,
            Inventory expectedTop) {
        finishMutation(player, holder.viewerId(), request, expectedTop, () -> {
            if (result.succeeded()) {
                player.sendMessage(Messages.line(MessageKey.SLOT_UNLOCKED, Messages.of("amount", holder.slot())));
                Feedback.success(player, FeedbackEvent.SLOT_UNLOCKED);
                player.performCommand(holder.origin().returnCommand());
                return;
            }
            if (result.status() == SlotPurchaseResult.Status.ENTITLEMENT_SYNC_PENDING) {
                player.sendMessage(Messages.line(MessageKey.SLOT_SYNC_PENDING,
                        Messages.of("detail", result.detail())));
                plugin.getLogger().warning("Slot " + holder.slot() + " for " + holder.viewerId()
                        + " is awaiting external entitlement sync: " + result.detail());
                return;
            }
            player.sendMessage(Messages.line(MessageKey.SLOT_PURCHASE_REJECTED,
                    Messages.of("status", words(result.status())),
                    Messages.of("detail", result.detail())));
            Feedback.failure(player, FeedbackEvent.SLOT_PURCHASE_REJECTED);
            if (result.status() == SlotPurchaseResult.Status.STALE_QUOTE) open(player, holder.origin());
        });
    }

    private void finishMutation(
            Player player,
            UUID playerId,
            long request,
            Inventory expectedTop,
            Runnable action) {
        if (shuttingDown || !plugin.isEnabled()) {
            mutations.remove(playerId);
            return;
        }
        try {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                mutations.remove(playerId);
                if (!shuttingDown && requests.isCurrent(playerId, request)
                        && player.isOnline()
                        && player.getOpenInventory().getTopInventory() == expectedTop) action.run();
            });
        } catch (RuntimeException failure) {
            mutations.remove(playerId);
            if (!shuttingDown) throw failure;
        }
    }

    private void complete(Player player, long request, Inventory expectedTop, Runnable action) {
        if (shuttingDown || !plugin.isEnabled()) return;
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!shuttingDown && requests.isCurrent(player.getUniqueId(), request)
                    && player.isOnline()
                    && player.getOpenInventory().getTopInventory() == expectedTop) action.run();
        });
    }

    private boolean isCurrent(Player player, SlotPurchaseInventoryHolder holder) {
        return !shuttingDown && player.isOnline() && holder.viewerId().equals(player.getUniqueId())
                && player.getOpenInventory().getTopInventory().getHolder() == holder;
    }

    private void fail(Player player, String message, Throwable failure) {
        player.sendMessage(Messages.line(MessageKey.SLOT_FAILURE, Messages.of("detail", message)));
        plugin.getLogger().warning(message + " for " + player.getUniqueId() + ": " + failure.getMessage());
    }

    private static void message(Player player, MessageKey key) {
        player.sendMessage(Messages.line(key));
    }

    private static String words(Enum<?> value) {
        return Displays.words(value);
    }
}
