package io.github.salyvn.omnipet.paper.management;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;

import net.kyori.adventure.text.format.NamedTextColor;

import io.github.salyvn.omnipet.core.persistence.RegistrySnapshotRepository;
import io.github.salyvn.omnipet.core.progression.ProgressionConfig;
import io.github.salyvn.omnipet.core.progression.ProgressionMutationContext;
import io.github.salyvn.omnipet.paper.gui.player.PetManagementInventoryHolder;
import io.github.salyvn.omnipet.paper.gui.player.PetManagementMenuRenderer;

/** Main-thread GUI shell over the revision-safe management application controller. */
public final class PetManagementMenuController {
    private final JavaPlugin plugin;
    private final PaperPetManagementController management;
    private final RegistrySnapshotRepository registry;
    private final Consumer<Player> storageRefresh;
    private final PetManagementMenuSupport.ViewLifecycle lifecycle =
            new PetManagementMenuSupport.ViewLifecycle();
    private final PetManagementMenuSupport.OutcomeRouter outcomes;
    private volatile ProgressionConfig progression;
    private volatile boolean closed;

    public PetManagementMenuController(
            JavaPlugin plugin,
            PaperPetManagementController management,
            RegistrySnapshotRepository registry,
            ProgressionConfig progression,
            Consumer<Player> storageRefresh) {
        this.plugin = Objects.requireNonNull(plugin, "management plugin");
        this.management = Objects.requireNonNull(management, "management application controller");
        this.registry = Objects.requireNonNull(registry, "pet registry");
        this.progression = Objects.requireNonNull(progression, "progression config");
        this.storageRefresh = Objects.requireNonNull(storageRefresh, "storage refresh callback");
        outcomes = new PetManagementMenuSupport.OutcomeRouter(
                management, new PetManagementMenuRenderer(), storageRefresh, lifecycle,
                this::dispatchMain, this::available);
    }

    public void updateProgression(ProgressionConfig next) {
        progression = Objects.requireNonNull(next, "progression config");
    }

    public void open(Player player, UUID petId) {
        if (!available(player)) return;
        PetManagementMenuSupport.ViewLifecycle.Request request = lifecycle.beginOpen(player.getUniqueId());
        if (request == null) {
            PetManagementMenuSupport.message(
                    player, "Another management request is already processing.", NamedTextColor.YELLOW);
            return;
        }
        try {
            submit(player, request, management.open(player.getUniqueId(), player.getUniqueId(), petId));
        } catch (RuntimeException failure) {
            lifecycle.complete(request);
            PetManagementMenuSupport.message(
                    player, "Management open failed: " + PetManagementMenuSupport.detail(failure) + ".",
                    NamedTextColor.RED);
        }
    }

    public void click(
            Player player,
            PetManagementInventoryHolder holder,
            PetManagementInventoryHolder.Action action) {
        if (action == null || !current(player, holder)) return;
        if (action.type() == PetManagementInventoryHolder.Type.BACK
                || action.type() == PetManagementInventoryHolder.Type.HUB) {
            close(holder, holder.getInventory());
            storageRefresh.accept(player);
            // BACK returns to the vault page the player came from; HUB goes to the hub. Both run
            // through the command so the destination controller owns its own coalesced read.
            player.performCommand(action.type() == PetManagementInventoryHolder.Type.HUB
                    ? "pet" : "pet vault");
            return;
        }
        PetManagementMenuSupport.ViewLifecycle.Request request = lifecycle.beginAction(holder);
        if (request == null) {
            PetManagementMenuSupport.message(
                    player, "Another management request is already processing.", NamedTextColor.YELLOW);
            return;
        }
        try {
            CompletionStage<PetManagementOutcome> stage = actionStage(
                    holder, action, lifecycle.view(holder));
            submit(player, request, stage);
        } catch (RuntimeException failure) {
            lifecycle.complete(request);
            PetManagementMenuSupport.message(
                    player, "Management action failed: " + PetManagementMenuSupport.detail(failure) + ".",
                    NamedTextColor.RED);
        }
    }

    public void close(PetManagementInventoryHolder holder, Inventory inventory) {
        PetManagementMenuSupport.ViewLifecycle.ActiveView retired = lifecycle.retire(holder, inventory);
        if (retired != null) management.close(retired.holder().session());
    }

    public void closeAll() {
        closed = true;
        lifecycle.shutdown().forEach(view -> management.close(view.holder().session()));
        plugin.getServer().getOnlinePlayers().forEach(player -> {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof PetManagementInventoryHolder) {
                player.closeInventory();
            }
        });
    }

    private CompletionStage<PetManagementOutcome> actionStage(
            PetManagementInventoryHolder holder,
            PetManagementInventoryHolder.Action action,
            PetManagementViewModel view) {
        return switch (action.type()) {
            case FAVORITE -> management.favorite(holder.session(), action.flag());
            case LOCK -> management.lock(holder.session(), action.flag());
            case MOVE -> management.move(holder.session(), action.targetIndex());
            case ADD_EXPERIENCE -> management.addExperience(holder.session(), context(view));
            case BREAKTHROUGH -> management.breakthrough(holder.session(), context(view));
            case PREVIEW_RELEASE -> management.previewRelease(holder.session());
            case CONFIRM_RELEASE -> management.confirmRelease(holder.session(), holder.releasePreview());
            case CANCEL_RELEASE, REFRESH -> management.open(holder.viewerId(), holder.ownerId(), holder.petId());
            case BACK -> throw new IllegalStateException("back is handled before async dispatch");
            case HUB -> throw new IllegalStateException("hub is handled before async dispatch");
        };
    }

    private void submit(
            Player player,
            PetManagementMenuSupport.ViewLifecycle.Request request,
            CompletionStage<PetManagementOutcome> stage) {
        Objects.requireNonNull(stage, "management result stage").whenComplete((outcome, failure) ->
                dispatchMain(request, () -> {
                    if (!available(player) || !lifecycle.accepts(request)) {
                        lifecycle.complete(request);
                        return;
                    }
                    try {
                        if (failure == null) {
                            outcomes.handle(player, request, outcome);
                        } else {
                            PetManagementMenuSupport.message(
                                    player,
                                    "Management action failed: " + PetManagementMenuSupport.detail(failure) + ".",
                                    NamedTextColor.RED);
                        }
                    } finally {
                        lifecycle.complete(request);
                    }
                }));
    }

    private ProgressionMutationContext context(PetManagementViewModel view) {
        return PetManagementMenuSupport.progressionContext(view, progression, registry);
    }

    private boolean current(Player player, PetManagementInventoryHolder holder) {
        return available(player)
                && lifecycle.current(holder)
                && holder.owns(player.getOpenInventory().getTopInventory());
    }

    private void dispatchMain(PetManagementMenuSupport.ViewLifecycle.Request request, Runnable task) {
        if (closed || !plugin.isEnabled()) {
            if (request != null) lifecycle.complete(request);
            return;
        }
        try {
            plugin.getServer().getScheduler().runTask(plugin, task);
        } catch (RuntimeException rejected) {
            // The scheduler refuses work during shutdown. Releasing the lifecycle request is the whole
            // point: without it a disable mid-open would leave the player unable to reopen the screen.
            if (request != null) lifecycle.complete(request);
        }
    }

    private boolean available(Player player) {
        return !closed && plugin.isEnabled() && player.isOnline();
    }
}
