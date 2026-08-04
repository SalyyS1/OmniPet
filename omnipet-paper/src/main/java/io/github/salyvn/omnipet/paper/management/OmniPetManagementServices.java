package io.github.salyvn.omnipet.paper.management;

import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import io.github.salyvn.omnipet.core.management.RepositoryPetManagementService;
import io.github.salyvn.omnipet.core.persistence.PlayerStateRepository;
import io.github.salyvn.omnipet.core.persistence.RegistrySnapshotRepository;
import io.github.salyvn.omnipet.core.progression.CultivationItemActionFileJournal;
import io.github.salyvn.omnipet.core.progression.CultivationItemActionService;
import io.github.salyvn.omnipet.core.progression.RepositoryProgressionService;
import io.github.salyvn.omnipet.core.release.ExternalReleaseResult;
import io.github.salyvn.omnipet.core.release.InternalOutboxResult;
import io.github.salyvn.omnipet.core.release.ReleaseOutboxDeliveryService;
import io.github.salyvn.omnipet.core.release.ReleaseService;
import io.github.salyvn.omnipet.core.release.SnapshotReleaseRewardPolicy;
import io.github.salyvn.omnipet.paper.config.OmniPetConfig;
import io.github.salyvn.omnipet.paper.economy.PaperEconomyProviderRegistry;
import io.github.salyvn.omnipet.paper.gui.player.PetManagementMenuListener;
import io.github.salyvn.omnipet.paper.player.PlayerPetController;
import io.github.salyvn.omnipet.paper.release.BukkitReleaseInventoryGateway;
import io.github.salyvn.omnipet.paper.release.FileReleaseRewardMailbox;
import io.github.salyvn.omnipet.paper.release.PaperReleaseAdminCommandTarget;
import io.github.salyvn.omnipet.paper.release.PaperReleaseExternalRewardPort;
import io.github.salyvn.omnipet.paper.release.PaperReleaseInternalRewardPort;
import io.github.salyvn.omnipet.paper.release.PaperReleaseMaterialMapper;
import io.github.salyvn.omnipet.paper.release.PaperReleaseSyncExecutor;
import io.github.salyvn.omnipet.paper.release.ReleaseAdminController;

/** Production composition root for cultivation, management GUI, and release delivery. */
public final class OmniPetManagementServices {
    private final PaperPetConsumableInventory consumables;
    private final PetCultivationItemController cultivationItems;
    private final PaperPetManagementController management;
    private final PetManagementMenuController menu;
    private final PetManagementMenuListener listener;
    private final PaperCultivationRecoveryController cultivationRecovery;
    private final PaperCultivationAdminCommandTarget cultivationAdmin;
    private final PaperReleaseAdminCommandTarget releaseAdmin;
    /**
     * The live config, read by the item-appearance supplier handed to the consumable factory.
     *
     * <p>A reference rather than a rebuild, so {@code /pet admin reload} restyles the next item without
     * reconstructing services that hold durable journals.
     */
    private final java.util.concurrent.atomic.AtomicReference<OmniPetConfig> live;

    private OmniPetManagementServices(
            PaperPetConsumableInventory consumables,
            PetCultivationItemController cultivationItems,
            PaperPetManagementController management,
            PetManagementMenuController menu,
            PaperCultivationRecoveryController cultivationRecovery,
            PaperCultivationAdminCommandTarget cultivationAdmin,
            PaperReleaseAdminCommandTarget releaseAdmin,
            java.util.concurrent.atomic.AtomicReference<OmniPetConfig> live) {
        this.live = live;
        this.consumables = consumables;
        this.cultivationItems = cultivationItems;
        this.management = management;
        this.menu = menu;
        this.listener = new PetManagementMenuListener(menu);
        this.cultivationRecovery = cultivationRecovery;
        this.cultivationAdmin = cultivationAdmin;
        this.releaseAdmin = releaseAdmin;
    }

    public static OmniPetManagementServices open(
            JavaPlugin plugin,
            Path dataRoot,
            PlayerStateRepository players,
            RegistrySnapshotRepository registry,
            OmniPetConfig config,
            PaperEconomyProviderRegistry economyProviders,
            PlayerPetController storageRefresh) {
        Objects.requireNonNull(plugin, "management plugin");
        Objects.requireNonNull(dataRoot, "management data root");
        Objects.requireNonNull(players, "management player repository");
        Objects.requireNonNull(registry, "management registry");
        Objects.requireNonNull(config, "management config");
        Objects.requireNonNull(economyProviders, "management economy providers");
        Objects.requireNonNull(storageRefresh, "management storage refresh");

        Executor asynchronous = task -> plugin.getServer().getScheduler()
                .runTaskAsynchronously(plugin, task);
        CultivationItemActionService cultivationActions = new CultivationItemActionService(
                new CultivationItemActionFileJournal(dataRoot.resolve("data/cultivation-actions")));
        // Appearance is read per item creation through a supplier reading the same volatile field
        // updateConfig writes, so a reload restyles the next item without rebuilding this service.
        java.util.concurrent.atomic.AtomicReference<OmniPetConfig> live =
                new java.util.concurrent.atomic.AtomicReference<>(config);
        PaperPetConsumableInventory consumables = new PaperPetConsumableInventory(
                plugin,
                config.cultivationItems(),
                () -> live.get().appearances(),
                warning -> plugin.getLogger().warning("OmniPet item appearance: " + warning));

        ReleaseService release = new ReleaseService(players, new SnapshotReleaseRewardPolicy());
        ReleaseOutboxDeliveryService delivery = new ReleaseOutboxDeliveryService(players);
        FileReleaseRewardMailbox mailbox = new FileReleaseRewardMailbox(
                dataRoot.resolve("data/release-mailbox"));
        PaperReleaseInternalRewardPort internal = new PaperReleaseInternalRewardPort(
                mailbox, new PaperReleaseMaterialMapper(), new PaperReleaseSyncExecutor(plugin),
                new BukkitReleaseInventoryGateway(plugin));
        PaperReleaseExternalRewardPort external = new PaperReleaseExternalRewardPort(economyProviders);

        CorePetManagementRepositoryAdapter repository = new CorePetManagementRepositoryAdapter(
                players,
                new RepositoryPetManagementService(players),
                new RepositoryProgressionService(players),
                release);
        PaperPetManagementController management = new PaperPetManagementController(
                repository,
                PetManagementAuthorizationPort.ownerOnly(),
                cultivationActions,
                consumables,
                mainThread(plugin),
                (ownerId, transactionId) -> recoverRelease(
                        asynchronous, delivery, internal, external, ownerId, transactionId),
                asynchronous);
        PetManagementMenuController menu = new PetManagementMenuController(
                plugin, management, registry, config.progression(), storageRefresh::reconcile);
        PaperCultivationRecoveryController recovery = new PaperCultivationRecoveryController(
                plugin, cultivationActions, management, registry, config.progression(), asynchronous);
        ReleaseAdminController releaseAdmin = new ReleaseAdminController(
                mailbox, internal, external, delivery, players);
        return new OmniPetManagementServices(
                consumables,
                new PetCultivationItemController(consumables),
                management,
                menu,
                recovery,
                new PaperCultivationAdminCommandTarget(plugin, recovery),
                new PaperReleaseAdminCommandTarget(plugin, releaseAdmin, asynchronous),
                live);
    }

    public PetCultivationItemController cultivationItems() { return cultivationItems; }
    public PetManagementMenuController menu() { return menu; }
    public PetManagementMenuListener listener() { return listener; }
    public PaperCultivationAdminCommandTarget cultivationAdmin() { return cultivationAdmin; }
    public PaperReleaseAdminCommandTarget releaseAdmin() { return releaseAdmin; }

    public void updateConfig(OmniPetConfig config) {
        live.set(config);
        consumables.updateConfig(config.cultivationItems());
        menu.updateProgression(config.progression());
        cultivationRecovery.updateProgression(config.progression());
    }

    public void onJoin(Player player) {
        cultivationRecovery.onJoin(player);
    }

    public void close() {
        menu.closeAll();
        cultivationRecovery.shutdown();
        management.shutdown();
    }

    private static PetManagementMainThread mainThread(JavaPlugin plugin) {
        return new PetManagementMainThread() {
            @Override public boolean isMainThread() { return Bukkit.isPrimaryThread(); }
            @Override public void execute(Runnable task) {
                if (Bukkit.isPrimaryThread()) task.run();
                else plugin.getServer().getScheduler().runTask(plugin, task);
            }
        };
    }

    private static CompletableFuture<InternalOutboxResult> recoverRelease(
            Executor executor,
            ReleaseOutboxDeliveryService delivery,
            PaperReleaseInternalRewardPort internal,
            PaperReleaseExternalRewardPort external,
            java.util.UUID ownerId,
            java.util.UUID transactionId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                InternalOutboxResult internalResult = delivery.recoverInternal(
                        ownerId, transactionId, internal);
                if (internalResult.status() != InternalOutboxResult.Status.ACKNOWLEDGED) return internalResult;
                ExternalReleaseResult externalResult = delivery.attemptExternal(
                        ownerId, transactionId, external);
                boolean complete = externalResult.status() == ExternalReleaseResult.Status.DELIVERED
                        || externalResult.status() == ExternalReleaseResult.Status.NOT_REQUIRED;
                return new InternalOutboxResult(
                        complete ? InternalOutboxResult.Status.ACKNOWLEDGED
                                : InternalOutboxResult.Status.PENDING_FAILURE,
                        internalResult.entry(),
                        internalResult.detail() + "; external=" + externalResult.status()
                                + " - " + externalResult.detail());
            } catch (java.io.IOException failure) {
                throw new java.util.concurrent.CompletionException(failure);
            }
        }, executor);
    }
}
