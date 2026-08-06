package io.github.salyvn.omnipet.paper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.bukkit.plugin.java.JavaPlugin;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;

import io.github.salyvn.omnipet.core.persistence.FilePlayerStateRepository;
import io.github.salyvn.omnipet.core.persistence.AtomicFileStore;
import io.github.salyvn.omnipet.core.persistence.FoundationRegistryLoader;
import io.github.salyvn.omnipet.core.persistence.InMemoryRegistrySnapshotRepository;
import io.github.salyvn.omnipet.core.persistence.PlayerStateRepository;
import io.github.salyvn.omnipet.core.persistence.RegistrySnapshotRepository;
import io.github.salyvn.omnipet.core.persistence.PetReferenceScanner;
import io.github.salyvn.omnipet.core.persistence.YamlPetDefinitionRepository;
import io.github.salyvn.omnipet.core.economy.FilePurchaseJournal;
import io.github.salyvn.omnipet.core.economy.PurchaseTransactionCoordinator;
import io.github.salyvn.omnipet.core.economy.SlotPurchaseReconciliationService;
import io.github.salyvn.omnipet.core.economy.SlotUnlockService;
import io.github.salyvn.omnipet.core.storage.RepositoryPetStorageService;
import io.github.salyvn.omnipet.core.incubation.IncubationItemActionFileJournal;
import io.github.salyvn.omnipet.core.incubation.IncubationItemActionService;
import io.github.salyvn.omnipet.core.migration.legacy.LegacyEggDefinitionsMigrationResult;
import io.github.salyvn.omnipet.core.migration.legacy.LegacyEggDefinitionsMigrator;
import io.github.salyvn.omnipet.paper.command.FoundationCommandContract;
import io.github.salyvn.omnipet.paper.command.OmniPetAdminCommand;
import io.github.salyvn.omnipet.paper.command.OmniPetCommand;
import io.github.salyvn.omnipet.paper.buff.MythicLibBuffLifecycleListener;
import io.github.salyvn.omnipet.paper.buff.PaperOwnerBuffCoordinator;
import io.github.salyvn.omnipet.paper.catalog.PaperStatCatalogContext;
import io.github.salyvn.omnipet.paper.catalog.ReflectiveMythicLibStatCatalogSource;
import io.github.salyvn.omnipet.paper.catalog.StatCatalogLifecycleListener;
import io.github.salyvn.omnipet.paper.config.Phase4PaperConfig;
import io.github.salyvn.omnipet.paper.config.GuiConfig;
import io.github.salyvn.omnipet.paper.config.GuiSettings;
import io.github.salyvn.omnipet.paper.feedback.BukkitFeedbackOutput;
import io.github.salyvn.omnipet.paper.feedback.Feedback;
import io.github.salyvn.omnipet.paper.feedback.FeedbackService;
import io.github.salyvn.omnipet.paper.feedback.FeedbackSettings;
import io.github.salyvn.omnipet.paper.config.OmniPetConfig;
import io.github.salyvn.omnipet.paper.config.OmniPetConfigLoader;
import io.github.salyvn.omnipet.paper.economy.EconomyProviderLifecycleListener;
import io.github.salyvn.omnipet.paper.economy.PaperEconomyProviderRegistry;
import io.github.salyvn.omnipet.paper.economy.SlotTransactionAdminController;
import io.github.salyvn.omnipet.paper.entitlement.PaperLuckPermsEntitlementRegistry;
import io.github.salyvn.omnipet.paper.entitlement.SlotEntitlementSynchronizer;
import io.github.salyvn.omnipet.paper.gui.pet.PetInteractListener;
import io.github.salyvn.omnipet.paper.incubation.EggAdminController;
import io.github.salyvn.omnipet.paper.incubation.placed.PlacedEggRecord;
import io.github.salyvn.omnipet.paper.incubation.placed.PlacedEggServices;
import io.github.salyvn.omnipet.paper.incubation.PaperEggItemCodec;
import io.github.salyvn.omnipet.paper.gui.player.PlayerPetMenuListener;
import io.github.salyvn.omnipet.paper.gui.hatch.HatchMenuListener;
import io.github.salyvn.omnipet.paper.gui.hub.HubMenuListener;
import io.github.salyvn.omnipet.paper.incubation.PaperIncubationServices;
import io.github.salyvn.omnipet.paper.incubation.PaperIncubationCoordinator;
import io.github.salyvn.omnipet.paper.incubation.HatchAdminController;
import io.github.salyvn.omnipet.paper.incubation.IncubationLifecycleListener;
import io.github.salyvn.omnipet.paper.incubation.action.IncubationActionItemController;
import io.github.salyvn.omnipet.paper.incubation.action.IncubationItemActionCoordinator;
import io.github.salyvn.omnipet.paper.incubation.action.PaperIncubationItemActionCodec;
import io.github.salyvn.omnipet.paper.incubation.action.PaperIncubationItemActionInventory;
import io.github.salyvn.omnipet.paper.incubation.action.RepositoryIncubationItemActionHatchPort;
import io.github.salyvn.omnipet.paper.permission.PaperStorageLimitsResolver;
import io.github.salyvn.omnipet.paper.player.PlayerPetController;
import io.github.salyvn.omnipet.paper.player.PlayerHatchController;
import io.github.salyvn.omnipet.paper.player.PlayerHubController;
import io.github.salyvn.omnipet.paper.player.PlayerSlotPurchaseController;
import io.github.salyvn.omnipet.paper.player.PlayerStorageLifecycleListener;
import io.github.salyvn.omnipet.paper.runtime.PaperPetRuntimeCoordinator;
import io.github.salyvn.omnipet.paper.runtime.PaperRuntimeBootstrap;
import io.github.salyvn.omnipet.paper.runtime.PaperRuntimeSnapshotPublisher;
import io.github.salyvn.omnipet.paper.render.RendererProviderLifecycleListener;
import io.github.salyvn.omnipet.paper.studio.bukkit.PetStudioController;
import io.github.salyvn.omnipet.paper.studio.bukkit.PetStudioListener;
import io.github.salyvn.omnipet.paper.task.PerPlayerTaskQueue;
import io.github.salyvn.omnipet.paper.task.PlayerTaskShutdown;
import io.github.salyvn.omnipet.paper.text.MessageCatalog;
import io.github.salyvn.omnipet.paper.text.MessageCatalogFile;
import io.github.salyvn.omnipet.paper.text.MessageKey;
import io.github.salyvn.omnipet.paper.text.Messages;
import io.github.salyvn.omnipet.paper.skill.MythicMobsSkillLifecycleListener;
import io.github.salyvn.omnipet.paper.skill.PaperActiveSkillController;
import io.github.salyvn.omnipet.paper.skill.PaperMythicMobsSkillContext;
import io.github.salyvn.omnipet.paper.management.OmniPetManagementServices;

public final class OmniPetPlugin extends JavaPlugin {
    private PlayerStateRepository playerStates;
    private RegistrySnapshotRepository registry;
    private PaperIncubationServices incubation;
    private PaperIncubationCoordinator incubationCoordinator;
    private PlayerHatchController hatchController;
    private HatchAdminController hatchAdmin;
    private IncubationActionItemController actionItems;
    private PetStudioController studio;
    private PaperStatCatalogContext statCatalog;
    private PlayerPetController playerPets;
    private PaperPetRuntimeCoordinator petRuntime;
    private PaperOwnerBuffCoordinator ownerBuffs;
    private PlayerSlotPurchaseController slotPurchases;
    private PerPlayerTaskQueue playerTasks;
    private PaperEconomyProviderRegistry economyProviders;
    private PaperLuckPermsEntitlementRegistry luckPermsEntitlements;
    private SlotTransactionAdminController transactionAdmin;
    private Path configFile;
    private OmniPetConfig activeConfig;
    private FeedbackService feedback;
    private PaperMythicMobsSkillContext skillProviders;
    private PaperActiveSkillController activeSkills;
    private OmniPetManagementServices managementServices;
    private PlayerHubController hubController;
    private EggAdminController eggAdmin;
    private Path messagesFile;
    private Path dataRoot;
    private PlacedEggServices placedEggs;

    @Override
    public void onEnable() {
        try {
            Path dataRoot = getDataFolder().toPath();
            if (Files.exists(dataRoot, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(dataRoot)) {
                throw new IOException("OmniPet data folder cannot be a symbolic link");
            }
            Files.createDirectories(dataRoot);
            configFile = dataRoot.resolve("config.yml");
            if (Files.exists(configFile, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(configFile)) {
                throw new IOException("OmniPet config.yml cannot be a symbolic link");
            }
            if (!Files.exists(configFile, LinkOption.NOFOLLOW_LINKS)) saveResource("config.yml", false);
            activeConfig = loadConfig();
            // Bound before any renderer or the Studio is constructed: those read their page sizes and
            // prompt timeout once, which is what makes those keys restart-only.
            GuiSettings.bind(activeConfig.gui());
            GuiSettings.bindWarnings(warning -> getLogger().warning("OmniPet gui.menus: " + warning));
            feedback = new FeedbackService(
                    new BukkitFeedbackOutput(), resolveFeedback(activeConfig.gui().feedback()));
            Feedback.bind(feedback);
            messagesFile = dataRoot.resolve("messages.yml");
            MessageCatalogFile.writeDefaultsIfAbsent(messagesFile);
            // Bundled translations are copied out so an operator can edit them; never overwritten.
            io.github.salyvn.omnipet.paper.text.MessageLocales.writeBundledPacks(
                    dataRoot, note -> getLogger().info("OmniPet: " + note));
            this.dataRoot = dataRoot;
            Messages.bind(loadMessages());
            Phase4PaperConfig phase4Config = activeConfig.storage();
            migrateLegacyEggDefinitions(dataRoot);
            YamlPetDefinitionRepository definitions = new YamlPetDefinitionRepository(dataRoot.resolve("pets"));
            playerStates = new FilePlayerStateRepository(dataRoot.resolve("data/players"));
            incubation = PaperIncubationServices.open(dataRoot, playerStates);
            FilePurchaseJournal purchaseJournal = new FilePurchaseJournal(dataRoot.resolve("data/purchases"));
            PurchaseTransactionCoordinator purchaseTransactions = new PurchaseTransactionCoordinator();
            registry = new InMemoryRegistrySnapshotRepository();
            var snapshot = new FoundationRegistryLoader().load(definitions, registry);
            petRuntime = PaperRuntimeBootstrap.create(this, activeConfig.runtime(), activeConfig.render());
            ownerBuffs = new PaperOwnerBuffCoordinator(this);
            ownerBuffs.refreshProvider();
            skillProviders = new PaperMythicMobsSkillContext(this);
            skillProviders.refresh();
            statCatalog = new PaperStatCatalogContext(new ReflectiveMythicLibStatCatalogSource(
                    getServer().getPluginManager()));
            studio = new PetStudioController(this, definitions, registry, java.util.List.of(
                    playerStates::referenceScan,
                    incubation.petReferences(),
                    PetReferenceScanner.yamlFiles(java.util.List.of(dataRoot.resolve("eggs.yml")))), statCatalog);
            PaperStorageLimitsResolver limitsResolver = new PaperStorageLimitsResolver(phase4Config);
            playerTasks = new PerPlayerTaskQueue(task ->
                    getServer().getScheduler().runTaskAsynchronously(this, task));
            activeSkills = new PaperActiveSkillController(
                    this, playerStates, registry, skillProviders, playerTasks, activeConfig.progression());
            economyProviders = new PaperEconomyProviderRegistry(this);
            luckPermsEntitlements = new PaperLuckPermsEntitlementRegistry(this);
            economyProviders.refresh();
            luckPermsEntitlements.refresh();
            SlotUnlockService slotUnlocks = new SlotUnlockService(
                    playerStates,
                    purchaseJournal,
                    economyProviders,
                    purchaseTransactions);
            SlotEntitlementSynchronizer entitlementSync = new SlotEntitlementSynchronizer(luckPermsEntitlements);
            slotPurchases = new PlayerSlotPurchaseController(
                    this,
                    playerStates,
                    slotUnlocks,
                    economyProviders,
                    entitlementSync,
                    limitsResolver,
                    playerTasks);
            PaperRuntimeSnapshotPublisher runtimeSnapshots = new PaperRuntimeSnapshotPublisher(petRuntime, registry);
            playerPets = new PlayerPetController(
                    this,
                    new RepositoryPetStorageService(playerStates),
                    limitsResolver,
                    playerTasks,
                    snapshotUpdate -> {
                        runtimeSnapshots.accept(snapshotUpdate);
                        ownerBuffs.accept(snapshotUpdate);
                    });
            managementServices = OmniPetManagementServices.open(
                    this, dataRoot, playerStates, registry, activeConfig, economyProviders, playerPets);
            eggAdmin = new EggAdminController(
                    incubation.eggDefinitions(),
                    registry,
                    new RepositoryPetStorageService(playerStates),
                    new PaperEggItemCodec(this),
                    limitsResolver,
                    playerTasks,
                    this,
                    // Read per mint, so a reload restyles the next egg without a restart.
                    () -> activeConfig.appearances());
            // Saving a definition in the Studio now also writes its egg, so a new pet is reachable.
            studio.bindEggs(eggAdmin);
            incubationCoordinator = new PaperIncubationCoordinator(
                    this, incubation, registry, limitsResolver, playerTasks);
            hatchController = new PlayerHatchController(
                    this, incubation, incubationCoordinator, limitsResolver, playerTasks);
            PaperIncubationItemActionCodec actionCodec = new PaperIncubationItemActionCodec(this);
            PaperIncubationItemActionInventory actionInventory = new PaperIncubationItemActionInventory(actionCodec);
            IncubationItemActionCoordinator actionCoordinator = new IncubationItemActionCoordinator(
                    new IncubationItemActionService(new IncubationItemActionFileJournal(
                            dataRoot.resolve("data/incubation-actions"))),
                    new RepositoryIncubationItemActionHatchPort(incubation.hatches()),
                    actionInventory);
            actionItems = new IncubationActionItemController(
                    incubation.hatches(), actionCodec, actionInventory, actionCoordinator,
                    () -> activeConfig.appearances(),
                    warning -> getLogger().warning("OmniPet item appearance: " + warning));
            hatchController.setActionItems(actionItems);
            hatchAdmin = new HatchAdminController(this, incubation.hatches(), playerTasks);
            incubationCoordinator.setRefreshListener(hatchController::refresh);
            hubController = new PlayerHubController(
                    this,
                    incubation.hatches(),
                    limitsResolver,
                    playerTasks,
                    playerPets,
                    hatchController,
                    slotPurchases,
                    studio::openBrowse);
            transactionAdmin = new SlotTransactionAdminController(
                    this,
                    new SlotPurchaseReconciliationService(playerStates, purchaseJournal, purchaseTransactions),
                    slotUnlocks,
                    entitlementSync,
                    phase4Config.activeSlots());
            getServer().getPluginManager().registerEvents(new PetStudioListener(studio), this);
            getServer().getPluginManager().registerEvents(new StatCatalogLifecycleListener(statCatalog), this);
            getServer().getPluginManager().registerEvents(new MythicLibBuffLifecycleListener(ownerBuffs), this);
            getServer().getPluginManager().registerEvents(new RendererProviderLifecycleListener(petRuntime), this);
            getServer().getPluginManager().registerEvents(new MythicMobsSkillLifecycleListener(skillProviders), this);
            getServer().getPluginManager().registerEvents(
                    new PlayerPetMenuListener(playerPets, slotPurchases, managementServices.menu()), this);
            getServer().getPluginManager().registerEvents(managementServices.listener(), this);
            // Only the plain interact event: PlayerInteractAtEntityEvent has its own HandlerList
            // despite extending it, so registering both would deliver two events for one click.
            getServer().getPluginManager().registerEvents(
                    new PetInteractListener(petRuntime::petFor, managementServices.menu()::open), this);
            getServer().getPluginManager().registerEvents(
                    new PlayerStorageLifecycleListener(
                            playerPets, petRuntime, ownerBuffs::ownerQuit, managementServices::onJoin), this);
            getServer().getPluginManager().registerEvents(
                    new IncubationLifecycleListener(incubationCoordinator, hatchController), this);
            getServer().getPluginManager().registerEvents(new HatchMenuListener(hatchController), this);
            // Placing an egg incubates it in the world, keeping its identity in a durable block record.
            // Deliberately its own record rather than an escrow row: escrow proves a held item was paid
            // for, while a placed egg is one the player still owns.
            placedEggs = PlacedEggServices.open(
                    this,
                    dataRoot,
                    incubation.eggDefinitions(),
                    warning -> getLogger().warning("OmniPet placed egg: " + warning),
                    this::hatchPlacedEgg);
            getServer().getPluginManager().registerEvents(placedEggs.listener(), this);
            getServer().getPluginManager().registerEvents(placedEggs.menuListener(), this);
            placedEggs.start();
            getServer().getPluginManager().registerEvents(new HubMenuListener(hubController), this);
            // The only thing that tells a brand-new player OmniPet is here.
            getServer().getPluginManager().registerEvents(
                    new io.github.salyvn.omnipet.paper.player.PlayerOnboardingListener(
                            () -> GuiSettings.gui().firstJoinGreeting()),
                    this);
            // Reconciling a transaction from a menu, so the operator never copies a UUID out of chat.
            getServer().getPluginManager().registerEvents(
                    new io.github.salyvn.omnipet.paper.gui.admin.AdminTransactionMenuListener(
                            transactionAdmin,
                            io.github.salyvn.omnipet.paper.command.SlotTransactionAdminCommandParser.PERMISSION),
                    this);
            getServer().getPluginManager().registerEvents(new EconomyProviderLifecycleListener(
                    this,
                    economyProviders,
                    luckPermsEntitlements), this);
            registerCommands();
            petRuntime.start();
            incubationCoordinator.start();
            getServer().getOnlinePlayers().forEach(player -> {
                playerPets.reconcile(player);
                incubationCoordinator.onJoin(player);
                managementServices.onJoin(player);
            });
            getLogger().info("OmniPet enabled with Pet Studio, " + snapshot.definitions().size()
                    + " pet definitions, and " + incubation.eggDefinitionCount() + " egg definitions.");
        } catch (IOException | RuntimeException failure) {
            getLogger().severe("OmniPet foundation failed to initialize: " + failure.getMessage());
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        try {
            boolean idle = PlayerTaskShutdown.stopAndDrain(
                    () -> {
                        if (hatchController != null) hatchController.close();
                        if (hubController != null) hubController.close();
                        if (incubationCoordinator != null) incubationCoordinator.close();
                        if (managementServices != null) managementServices.close();
                        if (playerPets != null) playerPets.closeAll();
                        if (ownerBuffs != null) ownerBuffs.close();
                        if (petRuntime != null) petRuntime.disable();
                        // Stops the tick and despawns every hologram. The records stay: a placed egg is
                        // an item the player owns, and the holograms are rebuilt from them on enable.
                        if (placedEggs != null) placedEggs.stop();
                        if (slotPurchases != null) slotPurchases.close();
                    },
                    () -> {
                        if (economyProviders != null) economyProviders.close();
                        if (luckPermsEntitlements != null) luckPermsEntitlements.invalidate();
                    },
                    playerTasks,
                    Duration.ofSeconds(10));
            if (!idle) {
                getLogger().severe("Timed out waiting for accepted player mutations during disable.");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            getLogger().severe("Interrupted while waiting for accepted player mutations during disable.");
        }
        if (transactionAdmin != null) transactionAdmin.close();
        if (studio != null) studio.onDisable();
        Messages.unbind();
        GuiSettings.unbind();
        if (feedback != null) feedback.clear();
        Feedback.unbind();
    }

    private void migrateLegacyEggDefinitions(Path dataRoot) throws IOException {
        Path source = dataRoot.resolve("eggs.yml").toAbsolutePath().normalize();
        if (!Files.exists(source, LinkOption.NOFOLLOW_LINKS)) return;
        if (Files.isSymbolicLink(source)) throw new IOException("legacy eggs.yml cannot be a symbolic link");
        if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) return;

        Path journal = dataRoot.resolve("migration/legacy-eggs-v1.yml").toAbsolutePath().normalize();
        LegacyEggDefinitionsMigrationResult result = new LegacyEggDefinitionsMigrator().migrate(source, journal);
        if (result.journalWritten()) {
            getLogger().info("Migrated legacy egg definitions into a durable migration journal.");
        }
    }

    private void registerCommands() {
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            OmniPetCommand command = new OmniPetCommand(
                    studio,
                    playerPets,
                    hatchController,
                    hatchAdmin,
                    actionItems,
                    managementServices.cultivationItems(),
                    activeSkills,
                    managementServices.releaseAdmin(),
                    managementServices.cultivationAdmin(),
                    transactionAdmin,
                    slotPurchases,
                    this::reloadRuntime);
            command.bindHub(hubController::open);
            command.bindEggAdmin(eggAdmin);
            command.bindTransactionMenu(viewer -> transactionAdmin.openMenu(viewer, null,
                    () -> viewer.sendMessage(io.github.salyvn.omnipet.paper.text.Messages.line(
                            io.github.salyvn.omnipet.paper.text.MessageKey.GUI_ADMIN_TX_LOAD_FAILED))));
            command.bindStatDiagnostics(ownerId -> ownerBuffs.diagnose(ownerId));
            event.registrar().register(
                    FoundationCommandContract.NAME, FoundationCommandContract.ALIASES, command);
            // The same administration without the second word. Routes into the router /pet already
            // builds, so the two spellings cannot drift apart.
            event.registrar().register(
                    OmniPetAdminCommand.NAME, OmniPetAdminCommand.ALIASES,
                    new OmniPetAdminCommand(command));
        });
    }

    private boolean reloadRuntime() {
        try {
            OmniPetConfig staged = loadConfig();
            MessageCatalog stagedMessages = loadMessages();
            // Resolved before anything is swapped: an unknown sound name in the new config must not
            // leave feedback half-applied.
            FeedbackSettings stagedFeedback = resolveFeedback(staged.gui().feedback());
            Phase4PaperConfig stagedConfig = staged.storage();
            if (!studio.reload()) return false;
            PaperStorageLimitsResolver nextLimits = new PaperStorageLimitsResolver(stagedConfig);
            playerPets.updateLimitsResolver(nextLimits);
            slotPurchases.updateLimitsResolver(nextLimits);
            incubationCoordinator.updateLimitsResolver(nextLimits);
            hatchController.updateLimitsResolver(nextLimits);
            hubController.updateLimitsResolver(nextLimits);
            transactionAdmin.updateActiveSlots(stagedConfig.activeSlots());
            ownerBuffs.refreshProvider();
            skillProviders.refresh();
            activeSkills.updateProgression(staged.progression());
            managementServices.updateConfig(staged);
            if (!activeConfig.runtime().equals(staged.runtime())) {
                getLogger().warning("Runtime scheduler settings changed; restart the server to activate them safely.");
            }
            activeConfig = staged;
            GuiSettings.bind(staged.gui());
            feedback.apply(stagedFeedback);
            Messages.bind(stagedMessages);
            petRuntime.reload(registry.current());
            getServer().getOnlinePlayers().forEach(playerPets::reconcile);
            return true;
        } catch (IOException | RuntimeException failure) {
            getLogger().warning("OmniPet reload failed before activation: " + failure.getMessage());
            return false;
        }
    }

    /**
     * Resolves configured sound names against this server. Unknown names warn and silence one
     * category rather than failing, so an operator's typo on an unexpected Paper version costs a
     * noise, not the plugin.
     */
    /**
     * Turns a placed egg that has finished into a pet in its owner's vault.
     *
     * <p>The record is consumed only once the pet is admitted. A full vault leaves the egg on the ground
     * reading ready, so the reward waits for the player rather than being destroyed by bad timing — the
     * same rule the held-egg claim already follows.
     */
    private void hatchPlacedEgg(java.util.UUID ownerId, PlacedEggRecord record) {
        var owner = getServer().getPlayer(ownerId);
        if (owner == null || !owner.isOnline()) return;
        String definitionId = PlacedEggServices.definitionId(
                incubation.eggDefinitions(),
                record,
                warning -> getLogger().warning("OmniPet placed egg: " + warning));
        var definition = registry.current().definitions().get(definitionId);
        if (definition == null) return;
        var limits = new PaperStorageLimitsResolver(activeConfig.storage())
                .resolve(owner::hasPermission).limits();
        eggAdmin.grantAsync(ownerId, definition, limits, result -> {
            if (result == null || !result.succeeded()) {
                owner.sendMessage(Messages.line(MessageKey.EGG_PLACED_VAULT_FULL));
                return;
            }
            placedEggs.consume(record);
            owner.sendMessage(Messages.line(MessageKey.EGG_PLACED_HATCHED,
                    Messages.of("pet", definition.id())));
        });
    }

    private FeedbackSettings resolveFeedback(GuiConfig.Feedback config) {
        return FeedbackSettings.resolve(config, warning -> getLogger().warning("OmniPet feedback: " + warning));
    }

    private OmniPetConfig loadConfig() throws IOException {
        if (Files.isSymbolicLink(configFile)) {
            throw new IOException("OmniPet config.yml cannot be a symbolic link");
        }
        OmniPetConfigLoader loader = new OmniPetConfigLoader();
        OmniPetConfigLoader.LoadResult result = loader.load(
                configFile, warning -> getLogger().warning("OmniPet config.yml: " + warning));
        if (result.migratedLegacy()) {
            new AtomicFileStore().write(configFile, loader.encode(result.config()).getBytes(StandardCharsets.UTF_8));
            getLogger().info("Migrated legacy config to the OmniPet aggregate schema; original kept as config.yml.bak.");
        }
        return result.config();
    }

    /**
     * Reads {@code messages.yml}. Unknown keys and unusable values are logged and skipped, so an
     * operator typo degrades one line instead of blocking startup or a reload.
     */
    /**
     * The text catalog for the configured locale.
     *
     * <p>{@code messages.yml} layers over the language pack, which layers over the built-in English, so
     * an operator's own edits always win and a partial translation still loads.
     */
    private MessageCatalog loadMessages() throws IOException {
        return io.github.salyvn.omnipet.paper.text.MessageLocales.load(
                dataRoot,
                activeConfig.gui().locale(),
                warning -> getLogger().warning("OmniPet messages: " + warning));
    }

    PlayerStateRepository playerStates() {
        return playerStates;
    }

    RegistrySnapshotRepository registry() {
        return registry;
    }
}
