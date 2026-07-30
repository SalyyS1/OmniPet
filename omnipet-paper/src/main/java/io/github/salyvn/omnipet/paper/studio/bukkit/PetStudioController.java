package io.github.salyvn.omnipet.paper.studio.bukkit;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;

import io.github.salyvn.omnipet.core.domain.DisplayDefinition;
import io.github.salyvn.omnipet.core.domain.HeadIcon;
import io.github.salyvn.omnipet.core.domain.PetDefinition;
import io.github.salyvn.omnipet.core.domain.PetTier;
import io.github.salyvn.omnipet.core.persistence.RegistrySnapshot;
import io.github.salyvn.omnipet.core.persistence.RegistrySnapshotRepository;
import io.github.salyvn.omnipet.core.persistence.PetReferenceScanner;
import io.github.salyvn.omnipet.core.persistence.YamlPetDefinitionRepository;
import io.github.salyvn.omnipet.core.studio.PetDefinitionStudioService;
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

/** Bukkit-facing orchestration for the detached, transaction-backed Pet Studio. */
public final class PetStudioController {
    private final JavaPlugin plugin;
    private final YamlPetDefinitionRepository definitions;
    private final RegistrySnapshotRepository registry;
    private final PetDefinitionStudioService service;
    private final PetStudioSessionManager sessions;
    private final ChatInputService inputs;
    private final StudioInventoryRenderer renderer = new StudioInventoryRenderer();
    private final Map<UUID, StudioState> states = new HashMap<>();

    public PetStudioController(JavaPlugin plugin, YamlPetDefinitionRepository definitions,
                               RegistrySnapshotRepository registry) {
        this(plugin, definitions, registry, java.util.List.of());
    }

    public PetStudioController(JavaPlugin plugin, YamlPetDefinitionRepository definitions,
                               RegistrySnapshotRepository registry, java.util.List<PetReferenceScanner> referenceScanners) {
        this.plugin = plugin;
        this.definitions = definitions;
        this.registry = registry;
        this.service = new PetDefinitionStudioService(definitions, registry, ignored -> {}, referenceScanners,
                entry -> plugin.getLogger().info("Studio audit " + entry.operation() + " " + entry.definitionId()
                        + " generation=" + entry.registryGeneration() + " key=" + entry.idempotencyKey()));
        StudioThreadGuard guard = () -> { if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Studio must run on the main thread"); };
        StudioScheduler scheduler = (delay, task) -> {
            long ticks = Math.max(1L, (delay.toMillis() + 49L) / 50L);
            var handle = plugin.getServer().getScheduler().runTaskLater(plugin, task, ticks);
            return () -> handle.cancel();
        };
        StudioClock clock = Instant::now;
        this.sessions = new PetStudioSessionManager(clock, scheduler, guard, Duration.ofMinutes(15), this::onSessionClosed);
        StudioMainThreadDispatcher dispatcher = task -> plugin.getServer().getScheduler().runTask(plugin, task);
        this.inputs = new ChatInputService(clock, dispatcher, guard, sessions::isCurrent);
    }

    public void openBrowse(Player player) {
        openBrowse(player, PetTier.D, false);
    }

    public boolean reload() {
        assertMainThread();
        closeStudioInventories();
        sessions.onReload();
        try { service.reload(); return true; }
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
            case PREVIOUS -> { state.page = Math.max(0, state.page - 1); render(state, StudioInventoryHolder.Screen.LIST); }
            case NEXT -> { state.page++; render(state, StudioInventoryHolder.Screen.LIST); }
            case BACK -> openBrowse(player, state.tier, holder.screen() == StudioInventoryHolder.Screen.LIST ? false : true);
            case EDIT_TIER -> editTier(state);
            case EDIT_ICON -> awaitField(player, state, "icon.head", StudioDraftInputParsers::icon, state.draft::withIcon);
            case EDIT_DISPLAY -> awaitField(player, state, "display", StudioDraftInputParsers::display, state.draft::withDisplay);
            case EDIT_STATS -> awaitField(player, state, "stats", StudioDraftInputParsers::stats, state.draft::withStats);
            case EDIT_RARITY -> awaitField(player, state, "rarity", StudioDraftInputParsers::rarity, state.draft::withRarityBands);
            case EDIT_PROGRESSION -> awaitField(player, state, "progression", StudioDraftInputParsers::progression, state.draft::withProgression);
            case EDIT_SKILLS -> awaitField(player, state, "skills", StudioDraftInputParsers::skills, state.draft::withSkills);
            case EDIT_BEHAVIOR -> awaitField(player, state, "behavior", StudioDraftInputParsers::behavior, state.draft::withBehaviorExtensions);
            case EDIT_RELEASE -> awaitField(player, state, "release", StudioDraftInputParsers::release, state.draft::withReleasePolicy);
            case SAVE -> save(player, state);
            case CANCEL -> openBrowse(player, state.tier, true);
            case CONFIRM_ARCHIVE -> archive(player, state);
            case CANCEL_ARCHIVE -> render(state, StudioInventoryHolder.Screen.LIST);
        }
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

    private void editTier(StudioState state) {
        PetTier next = PetTier.values()[(state.draft.tier().ordinal() + 1) % PetTier.values().length];
        state.draft = state.draft.withTier(next);
        sessions.markDirty(state.token);
        render(state, StudioInventoryHolder.Screen.EDITOR);
    }

    private void save(Player player, StudioState state) {
        try {
            service.save(state.draft, state.saveKey);
            player.sendMessage("OmniPet: definition saved and registry generation advanced.");
            openBrowse(player, state.tier, false);
        } catch (Exception error) {
            plugin.getLogger().log(Level.WARNING, "OmniPet Studio save failed for " + state.draft.id(), error);
            player.sendMessage("OmniPet: save rejected - " + StudioErrorMessages.forAdmin(error));
        }
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

    private void awaitId(Player player, StudioState state) {
        StudioViewToken inputToken = sessions.nextView(state.token);
        state.token = inputToken;
        sessions.setPendingInput(inputToken, true);
        player.closeInventory();
        PendingChatInput<String> input = inputs.await(player.getUniqueId(), inputToken, "definition.id", Duration.ofMinutes(2),
                StudioInputParsers::parseStableId,
                id -> {
                    PetStudioSession session = sessions.open(player.getUniqueId(), id, 0, "", registry.current().generation());
                    StudioState next = new StudioState(player.getUniqueId(), session);
                    next.tier = state.tier;
                    next.draft = StudioPetDraft.create(id, session.registryGeneration(), state.tier,
                            new HeadIcon("BASE64", "CHANGE_ME"), new DisplayDefinition(DisplayDefinition.Provider.HEAD, null), Map.of())
                            .withIcon(new HeadIcon("BASE64", "CHANGE_ME"));
                    states.put(player.getUniqueId(), next);
                    render(next, StudioInventoryHolder.Screen.EDITOR);
                }, failure -> inputFailure(player, state, inputToken, failure, StudioInventoryHolder.Screen.LIST));
        plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> inputs.expire(player.getUniqueId(), input.inputId()), 2 * 60 * 20L);
    }

    private void awaitSearch(Player player, StudioState state) {
        StudioViewToken inputToken = sessions.nextView(state.token);
        state.token = inputToken;
        sessions.setPendingInput(inputToken, true);
        player.closeInventory();
        PendingChatInput<String> input = inputs.await(player.getUniqueId(), inputToken, "search", Duration.ofMinutes(2),
                raw -> raw.trim().equalsIgnoreCase("none") ? "" : StudioInputParsers.parseStableId(raw.trim()),
                filter -> {
                    state.filter = filter;
                    state.page = 0;
                    sessions.setPendingInput(inputToken, false);
                    render(state, StudioInventoryHolder.Screen.LIST);
                }, failure -> inputFailure(player, state, inputToken, failure, StudioInventoryHolder.Screen.LIST));
        plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> inputs.expire(player.getUniqueId(), input.inputId()), 2 * 60 * 20L);
    }

    private <T> void awaitField(Player player, StudioState state, String path, ChatInputParser<T> parser,
                                Function<T, StudioPetDraft> apply) {
        StudioViewToken inputToken = sessions.nextView(state.token);
        state.token = inputToken;
        sessions.setPendingInput(inputToken, true);
        player.closeInventory();
        PendingChatInput<T> input = inputs.await(player.getUniqueId(), inputToken, path, Duration.ofMinutes(2), parser,
                value -> {
                    state.draft = apply.apply(value);
                    sessions.setPendingInput(inputToken, false);
                    sessions.markDirty(inputToken);
                    render(state, StudioInventoryHolder.Screen.EDITOR);
                }, failure -> inputFailure(player, state, inputToken, failure, StudioInventoryHolder.Screen.EDITOR));
        plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> inputs.expire(player.getUniqueId(), input.inputId()), 2 * 60 * 20L);
    }

    private void inputFailure(Player player, StudioState state, StudioViewToken token, ChatInputFailure failure,
                              StudioInventoryHolder.Screen resumeScreen) {
        if (failure.reason() == ChatInputFailure.Reason.INVALID_INPUT) {
            sessions.touch(token);
            player.sendMessage("OmniPet: invalid " + failure.message() + ". Try again or type cancel.");
            return;
        }
        sessions.setPendingInput(token, false);
        if (sessions.isCurrent(token)) render(state, resumeScreen);
    }

    private void openBrowse(Player player, PetTier tier, boolean list) {
        PetStudioSession session = sessions.open(player.getUniqueId(), "browse", 0, "", registry.current().generation());
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
        Inventory inventory = switch (screen) {
            case TIERS -> renderer.tiers(player, state, registry.current());
            case LIST -> renderer.list(player, state, registry.current());
            case EDITOR -> renderer.editor(player, state);
            case ARCHIVE_CONFIRM -> renderer.archiveConfirm(player, state);
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
