package io.github.salyvn.omnipet.paper.studio.bukkit;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;

import io.github.salyvn.omnipet.core.catalog.StatCatalogEntry;
import io.github.salyvn.omnipet.core.domain.DisplayDefinition;
import io.github.salyvn.omnipet.core.domain.HeadIcon;
import io.github.salyvn.omnipet.core.domain.PetDefinition;
import io.github.salyvn.omnipet.core.domain.PetTier;
import io.github.salyvn.omnipet.core.persistence.RegistrySnapshot;
import io.github.salyvn.omnipet.core.persistence.RegistrySnapshotRepository;
import io.github.salyvn.omnipet.core.persistence.PetReferenceScanner;
import io.github.salyvn.omnipet.core.persistence.YamlPetDefinitionRepository;
import io.github.salyvn.omnipet.core.studio.FileStudioAuditSink;
import io.github.salyvn.omnipet.core.studio.PetDefinitionStudioService;
import io.github.salyvn.omnipet.core.studio.StatModifierType;
import io.github.salyvn.omnipet.core.studio.StudioPetDraft;
import io.github.salyvn.omnipet.core.studio.input.StudioInputParsers;
import io.github.salyvn.omnipet.paper.studio.input.ChatInputFailure;
import io.github.salyvn.omnipet.paper.studio.input.ChatInputParser;
import io.github.salyvn.omnipet.paper.studio.input.ChatInputService;
import io.github.salyvn.omnipet.paper.studio.input.PendingChatInput;
import io.github.salyvn.omnipet.paper.studio.input.StudioMainThreadDispatcher;
import io.github.salyvn.omnipet.paper.studio.session.PetStudioSession;
import io.github.salyvn.omnipet.paper.studio.session.PetStudioSessionManager;
import io.github.salyvn.omnipet.paper.studio.session.SessionCloseReason;
import io.github.salyvn.omnipet.paper.studio.session.SessionClosed;
import io.github.salyvn.omnipet.paper.studio.session.StudioClock;
import io.github.salyvn.omnipet.paper.studio.session.StudioScheduler;
import io.github.salyvn.omnipet.paper.studio.session.StudioThreadGuard;
import io.github.salyvn.omnipet.paper.studio.session.StudioViewToken;
import io.github.salyvn.omnipet.paper.catalog.PaperStatCatalogContext;
import io.github.salyvn.omnipet.paper.config.GuiSettings;
import io.github.salyvn.omnipet.paper.incubation.EggAdminController;

/** Bukkit-facing orchestration for the detached, transaction-backed Pet Studio. */
public final class PetStudioController {
    private final JavaPlugin plugin;
    private final YamlPetDefinitionRepository definitions;
    private final RegistrySnapshotRepository registry;
    private final PetDefinitionStudioService service;
    private final PetStudioSessionManager sessions;
    private final ChatInputService inputs;
    private final PaperStatCatalogContext statCatalog;
    private final Duration promptTimeout;
    private final long promptTimeoutTicks;
    /**
     * Set after construction so the three existing constructors stay unchanged. When absent, saving a
     * definition simply creates no egg, which is what the constructors used by tests rely on.
     */
    private volatile EggAdminController eggs;

    /** Wires companion-egg creation. Called once from {@code onEnable}. */
    public void bindEggs(EggAdminController target) {
        this.eggs = target;
    }
    private final StudioInventoryRenderer renderer = new StudioInventoryRenderer();
    private final Map<UUID, StudioState> states = new HashMap<>();

    public PetStudioController(JavaPlugin plugin, YamlPetDefinitionRepository definitions,
                               RegistrySnapshotRepository registry) {
        this(plugin, definitions, registry, List.of(), PaperStatCatalogContext.unavailable());
    }

    public PetStudioController(JavaPlugin plugin, YamlPetDefinitionRepository definitions,
                               RegistrySnapshotRepository registry, java.util.List<PetReferenceScanner> referenceScanners) {
        this(plugin, definitions, registry, referenceScanners, PaperStatCatalogContext.unavailable());
    }

    public PetStudioController(JavaPlugin plugin, YamlPetDefinitionRepository definitions,
                               RegistrySnapshotRepository registry, List<PetReferenceScanner> referenceScanners,
                               PaperStatCatalogContext statCatalog) {
        this.plugin = plugin;
        this.definitions = definitions;
        this.registry = registry;
        this.statCatalog = statCatalog;
        StudioThreadGuard guard = () -> { if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Studio must run on the main thread"); };
        StudioScheduler scheduler = (delay, task) -> {
            long ticks = Math.max(1L, (delay.toMillis() + 49L) / 50L);
            var handle = plugin.getServer().getScheduler().runTaskLater(plugin, task, ticks);
            return () -> handle.cancel();
        };
        StudioClock clock = Instant::now;
        this.sessions = new PetStudioSessionManager(clock, scheduler, guard, Duration.ofMinutes(15), this::onSessionClosed);
        List<PetReferenceScanner> scanners = new ArrayList<>(referenceScanners == null ? List.of() : referenceScanners);
        scanners.add(sessions::references);
        this.service = new PetDefinitionStudioService(
                definitions,
                registry,
                ignored -> {},
                scanners,
                new FileStudioAuditSink(plugin.getDataFolder().toPath().resolve("data/audit/studio")));
        StudioMainThreadDispatcher dispatcher = task -> plugin.getServer().getScheduler().runTask(plugin, task);
        this.inputs = new ChatInputService(clock, dispatcher, guard, sessions::isCurrent);
        // Read once, restart-only: ChatInputService is built here and reload() does not rebuild it.
        this.promptTimeout = GuiSettings.gui().studioPromptTimeout();
        // Derived, never maintained alongside the Duration. Six call sites previously carried a
        // hand-written tick count next to their own Duration, which is six chances to disagree.
        this.promptTimeoutTicks = GuiSettings.gui().studioPromptTimeoutTicks();
    }

    public void openBrowse(Player player) {
        openBrowse(player, PetTier.D, false);
    }

    public boolean reload() {
        assertMainThread();
        closeStudioInventories();
        sessions.onReload();
        try { statCatalog.invalidate(); service.reload(); return true; }
        catch (Exception error) {
            plugin.getLogger().severe("OmniPet reload failed: " + error.getMessage());
            return false;
        }
    }

    public void onDisable() {
        assertMainThread();
        closeStudioInventories();
        sessions.onDisable();
    }

    public boolean capturesChat(UUID playerId) { return inputs.hasAsyncCapture(playerId); }

    public void captureChat(UUID playerId, String message) { inputs.captureAsync(playerId, message); }

    public void onQuit(UUID playerId) { sessions.onQuit(playerId); }

    public void onKick(UUID playerId) { sessions.close(playerId, SessionCloseReason.KICK); }

    public void onClose(StudioInventoryHolder holder, Player player) {
        if (!holder.viewerId().equals(player.getUniqueId())) return;
        if (sessions.isCurrent(holder.token())) sessions.close(holder.token(), SessionCloseReason.USER_CLOSE);
    }

    public void click(Player player, StudioInventoryHolder holder, StudioAction action) {
        if (!player.hasPermission("omnipet.admin.managepet") || !sessions.isCurrent(holder.token())) return;
        StudioState state = states.get(player.getUniqueId());
        if (state == null || !state.matches(holder.token())) return;
        sessions.touch(holder.token());
        switch (action.type()) {
            case TIER -> { state.tier = PetTier.valueOf(action.value()); state.page = 0; render(state, StudioInventoryHolder.Screen.LIST); }
            case PET -> actionPet(player, state, action.value());
            case CREATE -> awaitId(player, state);
            case SEARCH -> awaitSearch(player, state);
            case TOGGLE_ARCHIVE -> { state.archiveMode = !state.archiveMode; render(state, StudioInventoryHolder.Screen.LIST); }
            case PREVIOUS -> {
                if (holder.screen() == StudioInventoryHolder.Screen.STAT_PICKER) state.statPage = Math.max(0, state.statPage - 1);
                else state.page = Math.max(0, state.page - 1);
                render(state, holder.screen());
            }
            case NEXT -> {
                if (holder.screen() == StudioInventoryHolder.Screen.STAT_PICKER) state.statPage++;
                else state.page++;
                render(state, holder.screen());
            }
            case BACK -> {
                if (holder.screen() == StudioInventoryHolder.Screen.STAT_MODIFIER) {
                    // Abandoning the modifier choice must not leave it staged for the next stat.
                    state.clearPendingStat();
                    render(state, StudioInventoryHolder.Screen.STAT_PICKER);
                } else if (holder.screen() == StudioInventoryHolder.Screen.STAT_PICKER) {
                    state.clearPendingStat();
                    render(state, StudioInventoryHolder.Screen.EDITOR);
                } else {
                    openBrowse(player, state.tier, holder.screen() != StudioInventoryHolder.Screen.LIST);
                }
            }
            case EDIT_TIER -> editTier(state);
            case EDIT_ICON -> awaitField(player, state, StudioFieldPrompt.ICON,
                    StudioDraftInputParsers::icon, state.draft::withIcon);
            case EDIT_DISPLAY -> awaitField(player, state, StudioFieldPrompt.DISPLAY,
                    StudioDraftInputParsers::display, state.draft::withDisplay);
            case EDIT_STATS -> openStats(state);
            case CLONE -> awaitCloneId(player, state);
            case EDIT_RARITY -> awaitField(player, state, StudioFieldPrompt.RARITY,
                    StudioDraftInputParsers::rarity, state.draft::withRarityBands);
            case EDIT_PROGRESSION -> awaitField(player, state, StudioFieldPrompt.PROGRESSION,
                    StudioDraftInputParsers::progression, state.draft::withProgression);
            case EDIT_SKILLS -> awaitField(player, state, StudioFieldPrompt.SKILLS,
                    StudioDraftInputParsers::skills, state.draft::withSkills);
            case EDIT_BEHAVIOR -> awaitField(player, state, StudioFieldPrompt.BEHAVIOR,
                    StudioDraftInputParsers::behavior, state.draft::withBehaviorExtensions);
            case EDIT_RELEASE -> awaitField(player, state, StudioFieldPrompt.RELEASE,
                    StudioDraftInputParsers::release, state.draft::withReleasePolicy);
            case EDIT_NAME -> awaitField(player, state, StudioFieldPrompt.NAME,
                    StudioDraftInputParsers::displayName,
                    name -> state.draft.withRawValue(name, "display", "name"));
            case SAVE -> save(player, state);
            case CANCEL -> openBrowse(player, state.tier, true);
            case CONFIRM_ARCHIVE -> archive(player, state);
            case CANCEL_ARCHIVE -> render(state, StudioInventoryHolder.Screen.LIST);
            case HARD_DELETE -> awaitHardDelete(player, state);
            case STAT -> openStatModifiers(player, state, action.value());
            case STAT_MODIFIER -> awaitStatRange(player, state, action.value());
            case STAT_MANUAL -> awaitField(player, state, StudioFieldPrompt.STATS_MANUAL,
                    StudioDraftInputParsers::stats, state.draft::withStats);
            case STAT_SEARCH -> awaitStatSearch(player, state);
            case STAT_REFRESH -> { statCatalog.invalidate(); openStats(state); }
            // Deferred a tick: closing an inventory from inside InventoryClickEvent is unreliable,
            // and the resulting InventoryCloseEvent already ends the session and drops the state.
            case CLOSE -> plugin.getServer().getScheduler().runTask(plugin, (Runnable) () -> player.closeInventory());
        }
    }

    private void openStats(StudioState state) {
        state.statSnapshot = statCatalog.snapshot(registry.current().generation());
        state.statPage = 0;
        state.clearPendingStat();
        render(state, StudioInventoryHolder.Screen.STAT_PICKER);
    }

    /** First half of the stat flow: remember the stat and show the explained modifier choices. */
    private void openStatModifiers(Player player, StudioState state, String statId) {
        state.statSnapshot = statCatalog.snapshot(registry.current().generation());
        StatCatalogEntry entry = statEntry(state, statId);
        if (entry == null) {
            state.clearPendingStat();
            player.sendMessage("OmniPet: selected stat is no longer in the catalog; reopen the picker.");
            return;
        }
        if (entry.supportedModifierTypes().isEmpty()) {
            state.clearPendingStat();
            player.sendMessage("OmniPet: " + entry.displayName() + " declares no supported modifiers.");
            return;
        }
        state.pendingStatId = entry.id();
        state.pendingModifier = null;
        render(state, StudioInventoryHolder.Screen.STAT_MODIFIER);
    }

    /**
     * Second half: the modifier is chosen, so the prompt asks only for {@code min max}.
     *
     * <p>The parser still accepts the full {@code FLAT 10 50} form, so an operator who knows the
     * syntax is not forced through the screen.
     */
    private void awaitStatRange(Player player, StudioState state, String modifierName) {
        StatCatalogEntry entry = pendingStatEntry(state);
        if (entry == null) {
            state.clearPendingStat();
            player.sendMessage("OmniPet: selected stat is no longer in the catalog; reopen the picker.");
            return;
        }
        StatModifierType modifier;
        try {
            modifier = StatModifierType.valueOf(modifierName);
        } catch (IllegalArgumentException invalid) {
            state.clearPendingStat();
            player.sendMessage("OmniPet: unknown stat modifier; reopen the picker.");
            return;
        }
        if (!entry.supportedModifierTypes().contains(modifier)) {
            player.sendMessage("OmniPet: " + entry.displayName() + " does not support "
                    + StatModifierPresentation.label(modifier) + ".");
            return;
        }
        state.pendingModifier = modifier;
        awaitField(player, state, "stats." + entry.id(),
                () -> StudioFieldPrompt.sendStatRange(player, entry.displayName(), modifier),
                raw -> StudioDraftInputParsers.catalogStat(raw, entry, modifier),
                value -> {
                    state.clearPendingStat();
                    return state.draft.withStats(
                            StudioDraftInputParsers.upsertStat(state.draft.stats(), value));
                },
                StudioInventoryHolder.Screen.STAT_PICKER);
    }

    private StatCatalogEntry pendingStatEntry(StudioState state) {
        return state.pendingStatId == null ? null : statEntry(state, state.pendingStatId);
    }

    private StatCatalogEntry statEntry(StudioState state, String statId) {
        if (state.statSnapshot == null) return null;
        return state.statSnapshot.entries().stream()
                .filter(candidate -> candidate.id().equals(statId)).findFirst().orElse(null);
    }

    private void awaitStatSearch(Player player, StudioState state) {
        StudioViewToken inputToken = sessions.nextView(state.token);
        state.token = inputToken;
        sessions.setPendingInput(inputToken, true);
        StudioFieldPrompt.STAT_SEARCH.send(player);
        player.closeInventory();
        PendingChatInput<String> input = inputs.await(player.getUniqueId(), inputToken, "stats.search",
                promptTimeout, raw -> {
            String value = raw == null ? "" : raw.trim();
            if (value.equalsIgnoreCase("none")) return "";
            if (value.length() > 64 || value.chars().anyMatch(Character::isISOControl)) {
                throw new IllegalArgumentException("search text is invalid");
            }
            return value.toLowerCase(java.util.Locale.ROOT);
        }, value -> {
                    state.statFilter = value;
                    state.statPage = 0;
                    sessions.setPendingInput(inputToken, false);
                    render(state, StudioInventoryHolder.Screen.STAT_PICKER);
                }, failure -> inputFailure(player, state, inputToken, failure,
                        StudioInventoryHolder.Screen.STAT_PICKER));
        plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> inputs.expire(player.getUniqueId(), input.inputId()), promptTimeoutTicks);
    }

    private void actionPet(Player player, StudioState state, String id) {
        PetDefinition definition = registry.current().definitions().get(id);
        if (definition == null) { player.sendMessage("OmniPet: definition is no longer available."); return; }
        if (state.archiveMode) {
            state.archiveTarget = id;
            state.archiveRevision = definition.revision();
            state.archiveHash = StudioPetDraft.semanticHash(definition.rawNode());
            state.archiveGeneration = registry.current().generation();
            state.archiveKey = UUID.randomUUID();
            state.hardDeleteKey = UUID.randomUUID();
            render(state, StudioInventoryHolder.Screen.ARCHIVE_CONFIRM);
            return;
        }
        PetStudioSession session = sessions.open(player.getUniqueId(), id, definition.revision(),
                StudioPetDraft.semanticHash(definition.rawNode()), registry.current().generation());
        StudioState next = new StudioState(player.getUniqueId(), session);
        next.tier = state.tier;
        try {
            next.draft = StudioPetDraft.edit(definition, session.baseHash(), session.registryGeneration());
        } catch (IllegalArgumentException error) {
            plugin.getLogger().log(Level.WARNING, "OmniPet Studio failed to open definition " + definition.id(), error);
            player.sendMessage("OmniPet: definition cannot be opened in Studio - " + StudioErrorMessages.forAdmin(error));
            sessions.close(session.viewToken(), SessionCloseReason.INVALIDATED);
            return;
        }
        states.put(player.getUniqueId(), next);
        render(next, StudioInventoryHolder.Screen.EDITOR);
    }

    private void awaitCloneId(Player player, StudioState state) {
        StudioPetDraft source = state.draft;
        if (source == null || source.id() == null || source.mode() != StudioPetDraft.Mode.EDIT) {
            player.sendMessage("OmniPet: only a persisted definition can be cloned.");
            return;
        }
        StudioViewToken inputToken = sessions.nextView(state.token);
        state.token = inputToken;
        sessions.setPendingInput(inputToken, true);
        StudioFieldPrompt.CLONE_ID.send(player);
        player.closeInventory();
        PendingChatInput<String> input = inputs.await(player.getUniqueId(), inputToken, "clone.definition.id",
                promptTimeout, raw -> {
                    String id = StudioInputParsers.parseStableId(raw);
                    if (id.equalsIgnoreCase(source.id())) {
                        throw new IllegalArgumentException("clone ID must differ from the source");
                    }
                    return id;
                },
                id -> {
                    long generation = registry.current().generation();
                    PetStudioSession session = sessions.open(player.getUniqueId(), id, 0, "", generation);
                    StudioState next = new StudioState(player.getUniqueId(), session);
                    next.tier = source.tier();
                    next.draft = source.cloneTo(id, generation);
                    states.put(player.getUniqueId(), next);
                    render(next, StudioInventoryHolder.Screen.EDITOR);
                }, failure -> inputFailure(player, state, inputToken, failure, StudioInventoryHolder.Screen.EDITOR));
        plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> inputs.expire(player.getUniqueId(), input.inputId()), promptTimeoutTicks);
    }

    private void editTier(StudioState state) {
        PetTier next = PetTier.values()[(state.draft.tier().ordinal() + 1) % PetTier.values().length];
        state.draft = state.draft.withTier(next);
        sessions.markDirty(state.token);
        render(state, StudioInventoryHolder.Screen.EDITOR);
    }

    private void save(Player player, StudioState state) {
        try {
            validateCatalogSelections(state);
            var saved = service.save(state.draft, state.saveKey);
            player.sendMessage("OmniPet: definition saved and registry generation advanced.");
            createCompanionEgg(player, saved.definition().definition());
            openBrowse(player, state.tier, false);
        } catch (Exception error) {
            plugin.getLogger().log(Level.WARNING, "OmniPet Studio save failed for " + state.draft.id(), error);
            player.sendMessage("OmniPet: save rejected - " + StudioErrorMessages.forAdmin(error));
        }
    }

    /**
     * Writes an egg that hatches the definition just saved, so a new pet is reachable in game.
     *
     * <p>Without this a Studio-created pet had no route to a player: the catalog had no entry naming
     * it, and no command created one. It runs after the save so a failure here cannot roll back or
     * obscure the definition write — the pet is the point, the egg is a convenience.
     *
     * <p>Never overwrites an existing entry, since an operator may have tuned its duration or
     * candidate pool by hand. A tier change is still reported, because the hatch roller rejects an egg
     * whose tier disagrees with its pet and the operator is the only one who can fix it.
     */
    private void createCompanionEgg(Player player, PetDefinition definition) {
        if (eggs == null || !GuiSettings.gui().studioAutoCreateEgg()) return;
        try {
            var result = eggs.createCompanionEgg(definition);
            switch (result.outcome()) {
                case CREATED -> player.sendMessage("OmniPet: created egg " + result.eggId()
                        + "; give it with /pet admin egg give <player_name> " + result.eggId() + ".");
                case TIER_MISMATCH -> player.sendMessage("OmniPet: egg " + result.eggId()
                        + " is still on the previous tier and will not hatch this pet. Re-create it with "
                        + "/pet admin egg create " + result.eggId() + " " + definition.id() + ".");
                case ALREADY_PRESENT -> { }
            }
        } catch (IOException | RuntimeException failure) {
            plugin.getLogger().log(Level.WARNING,
                    "OmniPet could not create a companion egg for " + definition.id()
                            + "; the definition was saved and the egg can be added with "
                            + "/pet admin egg create", failure);
            player.sendMessage("OmniPet: definition saved, but its egg could not be created - "
                    + StudioErrorMessages.forAdmin(failure));
        }
    }

    private void validateCatalogSelections(StudioState state) {
        state.statSnapshot = statCatalog.snapshot(registry.current().generation());
        StudioCatalogSelections.validate(state.statSnapshot, state.draft.stats());
    }

    private void archive(Player player, StudioState state) {
        if (state.archiveTarget == null) { player.sendMessage("OmniPet: archive confirmation is stale."); return; }
        boolean activeEdit = states.values().stream().anyMatch(other -> other != state && other.draft != null
                && other.draft.id() != null && other.draft.id().equalsIgnoreCase(state.archiveTarget));
        if (activeEdit) { player.sendMessage("OmniPet: another Studio session is editing this definition."); return; }
        try {
            service.archive(state.archiveTarget, state.archiveRevision, state.archiveHash,
                    state.archiveGeneration, state.archiveKey);
            player.sendMessage("OmniPet: definition archived safely.");
            openBrowse(player, state.tier, false);
        } catch (Exception error) {
            plugin.getLogger().log(Level.WARNING, "OmniPet Studio archive failed for " + state.archiveTarget, error);
            player.sendMessage("OmniPet: archive rejected - " + StudioErrorMessages.forAdmin(error));
        }
    }

    private void awaitHardDelete(Player player, StudioState state) {
        if (state.archiveTarget == null) {
            player.sendMessage("OmniPet: hard-delete confirmation is stale.");
            return;
        }
        String expectedId = state.archiveTarget;
        StudioViewToken inputToken = sessions.nextView(state.token);
        state.token = inputToken;
        sessions.setPendingInput(inputToken, true);
        player.sendMessage("OmniPet: type the exact definition ID '" + expectedId
                + "' to permanently delete it, or type cancel.");
        player.closeInventory();
        PendingChatInput<String> input = inputs.await(player.getUniqueId(), inputToken, "hard-delete.confirmation",
                promptTimeout, raw -> StudioDraftInputParsers.exactDefinitionId(raw, expectedId),
                typedId -> hardDelete(player, state, inputToken, typedId),
                failure -> inputFailure(player, state, inputToken, failure,
                        StudioInventoryHolder.Screen.ARCHIVE_CONFIRM));
        plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> inputs.expire(player.getUniqueId(), input.inputId()), promptTimeoutTicks);
    }

    private void hardDelete(Player player, StudioState state, StudioViewToken token, String typedId) {
        sessions.setPendingInput(token, false);
        if (!sessions.isCurrent(token) || state.archiveTarget == null) return;
        try {
            service.hardDelete(state.archiveTarget, typedId, state.archiveRevision, state.archiveHash,
                    state.archiveGeneration, state.hardDeleteKey);
            player.sendMessage("OmniPet: definition permanently deleted.");
            openBrowse(player, state.tier, false);
        } catch (Exception error) {
            plugin.getLogger().log(Level.WARNING,
                    "OmniPet Studio hard delete failed for " + state.archiveTarget, error);
            player.sendMessage("OmniPet: hard delete rejected - " + StudioErrorMessages.forAdmin(error));
            if (sessions.isCurrent(token)) render(state, StudioInventoryHolder.Screen.ARCHIVE_CONFIRM);
        }
    }

    /**
     * The icon a brand-new draft starts with: a placeholder the operator is expected to replace.
     *
     * <p>Save refuses it for a HEAD pet, whose icon is its whole appearance. A MODELENGINE pet is given a
     * working default instead, because its head is never what a player sees; see
     * {@code PetDefinitionStudioService.UNSET_ICON_VALUE}.
     */
    private static HeadIcon unsetIcon() {
        return new HeadIcon("BASE64", PetDefinitionStudioService.UNSET_ICON_VALUE);
    }

    private void awaitId(Player player, StudioState state) {        StudioViewToken inputToken = sessions.nextView(state.token);
        state.token = inputToken;
        sessions.setPendingInput(inputToken, true);
        StudioFieldPrompt.DEFINITION_ID.send(player);
        player.closeInventory();
        PendingChatInput<String> input = inputs.await(player.getUniqueId(), inputToken, "definition.id", promptTimeout,
                StudioInputParsers::parseStableId,
                id -> {
                    PetStudioSession session = sessions.open(player.getUniqueId(), id, 0, "", registry.current().generation());
                    StudioState next = new StudioState(player.getUniqueId(), session);
                    next.tier = state.tier;
                    next.draft = StudioPetDraft.create(id, session.registryGeneration(), state.tier,
                            unsetIcon(), new DisplayDefinition(DisplayDefinition.Provider.HEAD, null), Map.of());
                    states.put(player.getUniqueId(), next);
                    render(next, StudioInventoryHolder.Screen.EDITOR);
                }, failure -> inputFailure(player, state, inputToken, failure, StudioInventoryHolder.Screen.LIST));
        plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> inputs.expire(player.getUniqueId(), input.inputId()), promptTimeoutTicks);
    }

    private void awaitSearch(Player player, StudioState state) {
        StudioViewToken inputToken = sessions.nextView(state.token);
        state.token = inputToken;
        sessions.setPendingInput(inputToken, true);
        StudioFieldPrompt.LIST_SEARCH.send(player);
        player.closeInventory();
        PendingChatInput<String> input = inputs.await(player.getUniqueId(), inputToken, "search", promptTimeout,
                raw -> raw.trim().equalsIgnoreCase("none") ? "" : StudioInputParsers.parseStableId(raw.trim()),
                filter -> {
                    state.filter = filter;
                    state.page = 0;
                    sessions.setPendingInput(inputToken, false);
                    render(state, StudioInventoryHolder.Screen.LIST);
                }, failure -> inputFailure(player, state, inputToken, failure, StudioInventoryHolder.Screen.LIST));
        plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> inputs.expire(player.getUniqueId(), input.inputId()), promptTimeoutTicks);
    }

    private <T> void awaitField(Player player, StudioState state, StudioFieldPrompt prompt,
                                ChatInputParser<T> parser, Function<T, StudioPetDraft> apply) {
        awaitField(player, state, prompt, parser, apply, StudioInventoryHolder.Screen.EDITOR);
    }

    private <T> void awaitField(Player player, StudioState state, StudioFieldPrompt prompt,
                                ChatInputParser<T> parser, Function<T, StudioPetDraft> apply,
                                StudioInventoryHolder.Screen resumeScreen) {
        awaitField(player, state, prompt.path(), () -> prompt.send(player), parser, apply, resumeScreen);
    }

    /**
     * Closes the inventory and captures one chat line.
     *
     * <p>{@code sendPrompt} is required: this path used to close the inventory and wait silently, so
     * the operator was given no format, no example, and no hint that {@code cancel} works. The prompt
     * is sent immediately before the inventory closes so it is the last thing left on screen.
     */
    private <T> void awaitField(Player player, StudioState state, String path, Runnable sendPrompt,
                                ChatInputParser<T> parser, Function<T, StudioPetDraft> apply,
                                StudioInventoryHolder.Screen resumeScreen) {
        StudioViewToken inputToken = sessions.nextView(state.token);
        state.token = inputToken;
        sessions.setPendingInput(inputToken, true);
        sendPrompt.run();
        player.closeInventory();
        PendingChatInput<T> input = inputs.await(player.getUniqueId(), inputToken, path, promptTimeout, parser,
                value -> {
                    state.draft = apply.apply(value);
                    sessions.setPendingInput(inputToken, false);
                    sessions.markDirty(inputToken);
                    render(state, resumeScreen);
                }, failure -> inputFailure(player, state, inputToken, failure, resumeScreen));
        plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> inputs.expire(player.getUniqueId(), input.inputId()), promptTimeoutTicks);
    }

    private void inputFailure(Player player, StudioState state, StudioViewToken token, ChatInputFailure failure,
                              StudioInventoryHolder.Screen resumeScreen) {
        if (failure.reason() == ChatInputFailure.Reason.INVALID_INPUT) {
            sessions.touch(token);
            player.sendMessage("OmniPet: invalid " + failure.message() + ". Try again or type cancel.");
            return;
        }
        sessions.setPendingInput(token, false);
        // A cancelled or expired input leaves no staged modifier behind.
        state.clearPendingStat();
        if (sessions.isCurrent(token)) render(state, resumeScreen);
    }

    private void openBrowse(Player player, PetTier tier, boolean list) {
        PetStudioSession session = sessions.open(player.getUniqueId(), "", 0, "", registry.current().generation());
        StudioState state = new StudioState(player.getUniqueId(), session);
        state.tier = tier;
        states.put(player.getUniqueId(), state);
        render(state, list ? StudioInventoryHolder.Screen.LIST : StudioInventoryHolder.Screen.TIERS);
    }

    private void render(StudioState state, StudioInventoryHolder.Screen screen) {
        assertMainThread();
        Player player = Bukkit.getPlayer(state.viewerId);
        if (player == null || !sessions.isCurrent(state.token)) return;
        if (!sessions.touch(state.token)) return;
        state.token = sessions.nextView(state.token);
        if (screen == StudioInventoryHolder.Screen.EDITOR
                || screen == StudioInventoryHolder.Screen.STAT_PICKER
                || screen == StudioInventoryHolder.Screen.STAT_MODIFIER) {
            state.statSnapshot = statCatalog.snapshot(registry.current().generation());
        }
        Inventory inventory = switch (screen) {
            case TIERS -> renderer.tiers(player, state, registry.current());
            case LIST -> renderer.list(player, state, registry.current());
            case EDITOR -> renderer.editor(player, state);
            case ARCHIVE_CONFIRM -> renderer.archiveConfirm(player, state);
            case STAT_PICKER -> renderer.stats(player, state);
            case STAT_MODIFIER -> {
                StatCatalogEntry pending = pendingStatEntry(state);
                // A refreshed catalog can drop the stat between the two clicks; fall back to the
                // picker rather than rendering a modifier screen for something that no longer exists.
                if (pending == null) {
                    state.clearPendingStat();
                    yield renderer.stats(player, state);
                }
                yield renderer.statModifiers(player, state, pending);
            }
        };
        player.openInventory(inventory);
    }

    private void onSessionClosed(SessionClosed closed) {
        Player player = Bukkit.getPlayer(closed.viewerId());
        if (player != null && player.getOpenInventory().getTopInventory().getHolder() instanceof StudioInventoryHolder holder
                && holder.token().sessionId().equals(closed.sessionId())) {
            player.closeInventory();
        }
        inputs.cancelSession(closed.sessionId());
        StudioState state = states.get(closed.viewerId());
        if (state != null && state.sessionId.equals(closed.sessionId())) states.remove(closed.viewerId());
    }

    private void assertMainThread() { if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Studio must run on main thread"); }

    private void closeStudioInventories() {
        for (UUID viewerId : states.keySet().toArray(UUID[]::new)) {
            Player player = Bukkit.getPlayer(viewerId);
            if (player != null && player.getOpenInventory().getTopInventory().getHolder() instanceof StudioInventoryHolder) {
                player.closeInventory();
            }
        }
    }

}
