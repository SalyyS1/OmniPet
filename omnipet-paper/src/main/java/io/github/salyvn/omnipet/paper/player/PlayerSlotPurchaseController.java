package io.github.salyvn.omnipet.paper.player;

import java.io.IOException;
import java.time.Duration;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import io.github.salyvn.omnipet.core.economy.SlotPurchaseQuote;
import io.github.salyvn.omnipet.core.economy.SlotPurchaseResult;
import io.github.salyvn.omnipet.core.economy.SlotUnlockRule;
import io.github.salyvn.omnipet.core.economy.SlotUnlockService;
import io.github.salyvn.omnipet.core.economy.ExternalEntitlementResult;
import io.github.salyvn.omnipet.core.persistence.PlayerStateRepository;
import io.github.salyvn.omnipet.paper.config.Phase4PaperConfig;
import io.github.salyvn.omnipet.paper.economy.PaperEconomyProviderRegistry;
import io.github.salyvn.omnipet.paper.entitlement.SlotEntitlementSynchronizer;
import io.github.salyvn.omnipet.paper.gui.player.SlotPurchaseInventoryHolder;
import io.github.salyvn.omnipet.paper.gui.player.SlotPurchaseMenuRenderer;
import io.github.salyvn.omnipet.paper.permission.PaperStorageLimitsResolver;

public final class PlayerSlotPurchaseController {
    private static final String VIEW_TASK = "slot-purchase-view";

    private final JavaPlugin plugin;
    private final PlayerStateRepository playerStates;
    private final SlotUnlockService purchases;
    private final PaperEconomyProviderRegistry providers;
    private final SlotEntitlementSynchronizer entitlements;
    private final SlotPurchaseMenuRenderer renderer = new SlotPurchaseMenuRenderer();
    private final PlayerPetAsyncQueue queue;
    private final PlayerPetRequestTracker requests = new PlayerPetRequestTracker();
    private final Set<UUID> mutations = ConcurrentHashMap.newKeySet();
    private volatile PaperStorageLimitsResolver limitsResolver;
    private volatile boolean shuttingDown;

    public PlayerSlotPurchaseController(
            JavaPlugin plugin,
            PlayerStateRepository playerStates,
            SlotUnlockService purchases,
            PaperEconomyProviderRegistry providers,
            SlotEntitlementSynchronizer entitlements,
            PaperStorageLimitsResolver limitsResolver) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.playerStates = Objects.requireNonNull(playerStates, "player state repository");
        this.purchases = Objects.requireNonNull(purchases, "slot unlock service");
        this.providers = Objects.requireNonNull(providers, "economy provider registry");
        this.entitlements = Objects.requireNonNull(entitlements, "slot entitlement synchronizer");
        this.limitsResolver = Objects.requireNonNull(limitsResolver, "storage limits resolver");
        this.queue = new PlayerPetAsyncQueue(task ->
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, task));
    }

    public void updateLimitsResolver(PaperStorageLimitsResolver next) {
        limitsResolver = Objects.requireNonNull(next, "storage limits resolver");
    }

    public void open(Player player, int returnPage) {
        Objects.requireNonNull(player, "player");
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
            queue.submitLatest(playerId, VIEW_TASK, () -> {
                try {
                var state = playerStates.snapshot(playerId);
                if (state.activeSlotCount() < config.base()) {
                    complete(player, request, expectedTop, () -> message(
                            player,
                            "Active slot baseline reconciliation is still pending; reopen this menu shortly.",
                            NamedTextColor.YELLOW));
                    return;
                }
                if (config.entitlement().policy().mode()
                                != io.github.salyvn.omnipet.core.storage.SlotEntitlementMode.OMNIPET
                        && observedLimits.observedExternalActiveSlotCount() != state.activeSlotCount()) {
                    complete(player, request, expectedTop, () -> message(
                            player,
                            "OmniPet and LuckPerms slot counts differ; reconcile them before another purchase.",
                            NamedTextColor.RED));
                    return;
                }
                int slot = state.activeSlotCount() + 1;
                Phase4PaperConfig.SlotUnlock unlock = config.unlock(slot).orElse(null);
                if (slot > config.max() || unlock == null) {
                    complete(player, request, expectedTop, () -> message(
                            player, "No further active slot upgrade is configured.", NamedTextColor.YELLOW));
                    return;
                }
                if (!eligibility.getOrDefault(slot, false)) {
                    complete(player, request, expectedTop, () -> message(
                            player, "You do not meet the permission requirement for slot " + slot + ".",
                            NamedTextColor.RED));
                    return;
                }
                if (!entitlements.available(config.entitlement())) {
                    complete(player, request, expectedTop, () -> message(
                            player,
                            "Slot entitlement provider unavailable: "
                                    + entitlements.unavailableReason(config.entitlement()),
                            NamedTextColor.RED));
                    return;
                }
                java.util.Map<io.github.salyvn.omnipet.core.economy.EconomyProvider, String> balances =
                        unlock.costs().keySet().stream().collect(java.util.stream.Collectors.toMap(
                                provider -> provider,
                                provider -> {
                                    var result = providers.balance(playerId, provider);
                                    return result.status()
                                                    == io.github.salyvn.omnipet.core.economy.EconomyBalanceResult.Status.AVAILABLE
                                            ? "Balance: " + result.balance().toPlainString()
                                            : "Balance unavailable: " + result.detail();
                                }));
                complete(player, request, expectedTop, () -> player.openInventory(renderer.selection(
                        player,
                        state.revision(),
                        slot,
                        returnPage,
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
        } catch (RuntimeException failure) {
            fail(player, "slot options could not be scheduled", failure);
        }
    }

    public void click(
            Player player,
            SlotPurchaseInventoryHolder holder,
            SlotPurchaseInventoryHolder.Action action) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!isCurrent(player, holder)) return;
            switch (action.type()) {
                case CANCEL -> player.performCommand("pet " + holder.returnPage());
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
        queue.shutdown();
        try {
            if (!queue.awaitIdle(Duration.ofSeconds(10))) {
                plugin.getLogger().severe("Timed out waiting for accepted slot purchases during disable.");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            plugin.getLogger().severe("Interrupted while waiting for accepted slot purchases.");
        }
        mutations.clear();
    }

    private void select(Player player, SlotPurchaseInventoryHolder holder, SlotPurchaseInventoryHolder.Action action) {
        if (providers.find(action.amount().provider()).isEmpty()) {
            message(player, providers.diagnostic(action.amount().provider()), NamedTextColor.RED);
            return;
        }
        player.openInventory(renderer.confirmation(player, holder, action.amount()));
    }

    private void confirm(Player player, SlotPurchaseInventoryHolder holder, SlotPurchaseInventoryHolder.Action action) {
        UUID playerId = player.getUniqueId();
        Phase4PaperConfig.ActiveSlots config = limitsResolver.activeSlots();
        Phase4PaperConfig.SlotUnlock current = config.unlock(holder.slot()).orElse(null);
        if (current == null || !action.amount().equals(current.costs().get(action.amount().provider()))) {
            message(player, "Slot price changed; reopen the purchase menu.", NamedTextColor.YELLOW);
            return;
        }
        if (!current.eligible(player::hasPermission) || !entitlements.available(config.entitlement())) {
            message(player, "Slot purchase requirements are no longer available.", NamedTextColor.RED);
            return;
        }
        if (!mutations.add(playerId)) {
            message(player, "A slot purchase is already processing.", NamedTextColor.YELLOW);
            return;
        }
        Inventory expectedTop = holder.getInventory();
        long request = requests.begin(playerId);
        try {
            boolean accepted = queue.submit(playerId, () -> purchaseAsync(
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
                message(player, "Active slot " + holder.slot() + " unlocked.", NamedTextColor.GREEN);
                player.performCommand("pet " + holder.returnPage());
                return;
            }
            if (result.status() == SlotPurchaseResult.Status.ENTITLEMENT_SYNC_PENDING) {
                message(player, "Slot saved, but entitlement sync needs admin review: " + result.detail(),
                        NamedTextColor.YELLOW);
                plugin.getLogger().warning("Slot " + holder.slot() + " for " + holder.viewerId()
                        + " is awaiting external entitlement sync: " + result.detail());
                return;
            }
            message(player, "Slot purchase " + result.status().name().toLowerCase().replace('_', ' ')
                    + ": " + result.detail(), NamedTextColor.RED);
            if (result.status() == SlotPurchaseResult.Status.STALE_QUOTE) open(player, holder.returnPage());
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
        message(player, message + ".", NamedTextColor.RED);
        plugin.getLogger().warning(message + " for " + player.getUniqueId() + ": " + failure.getMessage());
    }

    private static void message(Player player, String message, NamedTextColor color) {
        player.sendMessage(Component.text("OmniPet: " + message, color));
    }
}
