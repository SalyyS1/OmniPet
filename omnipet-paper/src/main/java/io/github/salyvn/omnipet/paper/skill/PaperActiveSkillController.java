package io.github.salyvn.omnipet.paper.skill;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import io.github.salyvn.omnipet.core.domain.PetDefinition;
import io.github.salyvn.omnipet.core.persistence.PlayerStateRepository;
import io.github.salyvn.omnipet.core.persistence.RegistrySnapshotRepository;
import io.github.salyvn.omnipet.core.progression.ProgressionConfig;
import io.github.salyvn.omnipet.core.skill.RepositorySkillActionResult;
import io.github.salyvn.omnipet.core.skill.RepositorySkillActionService;
import io.github.salyvn.omnipet.core.skill.PetSkillStateProjection;
import io.github.salyvn.omnipet.core.skill.SkillBinding;
import io.github.salyvn.omnipet.core.skill.SkillBindingProjection;
import io.github.salyvn.omnipet.core.skill.SkillCastRequest;
import io.github.salyvn.omnipet.core.skill.SkillCastResult;
import io.github.salyvn.omnipet.core.skill.SkillProvider;
import io.github.salyvn.omnipet.core.skill.SkillTrigger;
import io.github.salyvn.omnipet.paper.task.PerPlayerTaskQueue;
import io.github.salyvn.omnipet.paper.feedback.Feedback;
import io.github.salyvn.omnipet.paper.feedback.FeedbackEvent;
import io.github.salyvn.omnipet.paper.text.Displays;
import io.github.salyvn.omnipet.paper.text.MessageKey;
import io.github.salyvn.omnipet.paper.text.Messages;

/** Durable prepare -> main-thread provider cast -> complete/rollback skill workflow. */
public final class PaperActiveSkillController {
    private final JavaPlugin plugin;
    private final PlayerStateRepository players;
    private final RegistrySnapshotRepository registry;
    private final RepositorySkillActionService actions;
    private final PaperMythicMobsSkillContext providers;
    private final PerPlayerTaskQueue tasks;
    private final java.util.Set<UUID> inFlight = ConcurrentHashMap.newKeySet();
    /** Where the pets actually are. Null in tests that only exercise the durable workflow. */
    private volatile io.github.salyvn.omnipet.paper.runtime.PaperPetRuntimeCoordinator runtime;
    /**
     * What each owner's active pets can be triggered by.
     *
     * <p>An index rather than a lookup, because two of the callers are event handlers that must decide
     * whether to cancel a vanilla action — a drop, an off-hand swap — before the event returns, and reading
     * player state from disk on the main thread to answer that is not an option.
     *
     * <p>Rebuilt on the player's own queue whenever a trigger is dispatched or their pets change, so it
     * trails reality by at most one event. A stale entry costs at most one skipped or one wasted cast, both
     * of which the durable prepare step catches.
     */
    private final Map<UUID, OwnerTriggers> triggerIndex = new ConcurrentHashMap<>();
    private volatile ProgressionConfig progression;

    public PaperActiveSkillController(
            JavaPlugin plugin,
            PlayerStateRepository players,
            RegistrySnapshotRepository registry,
            PaperMythicMobsSkillContext providers,
            PerPlayerTaskQueue tasks,
            ProgressionConfig progression) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.players = Objects.requireNonNull(players, "player repository");
        this.registry = Objects.requireNonNull(registry, "pet registry");
        this.actions = new RepositorySkillActionService(players);
        this.providers = Objects.requireNonNull(providers, "skill providers");
        this.tasks = Objects.requireNonNull(tasks, "player task queue");
        this.progression = Objects.requireNonNull(progression, "progression config");
    }

    public void updateProgression(ProgressionConfig next) {
        progression = Objects.requireNonNull(next, "progression config");
    }

    /**
     * Binds the runtime that knows where the pets are.
     *
     * <p>Set after construction because the runtime and this controller are built in the same pass and one
     * of them has to come second. Without it, targeting still works for owner-relative policies and yields
     * nothing for pet-relative ones.
     */
    public void bindRuntime(io.github.salyvn.omnipet.paper.runtime.PaperPetRuntimeCoordinator next) {
        runtime = next;
    }

    public void cast(Player player, UUID petId, String bindingId) {
        Objects.requireNonNull(player, "skill player");
        Objects.requireNonNull(petId, "skill pet ID");
        if (bindingId == null || bindingId.isBlank()) {
            player.sendMessage(Messages.line(MessageKey.SKILL_BINDING_REQUIRED));
            return;
        }
        UUID playerId = player.getUniqueId();
        if (!inFlight.add(playerId)) {
            player.sendMessage(Messages.line(MessageKey.SKILL_IN_FLIGHT));
            return;
        }
        String binding = bindingId.trim();
        boolean accepted = tasks.submit(playerId, () -> prepare(playerId, petId, binding, null, true));
        if (!accepted) finish(playerId, Messages.line(MessageKey.SKILL_NOT_SCHEDULED));
    }

    /**
     * Fires every binding on the owner's active pets whose trigger matches.
     *
     * <p>Called from the event listeners, on the main thread. One trigger can match several bindings across
     * several pets; each becomes its own reservation, so one failing does not take the others with it.
     *
     * <p>The in-flight guard is skipped for a passive trigger. Passive triggers arrive at the rate the world
     * generates them — a hit, a jump, a tick — and a guard meant to stop a player double-casting by hand
     * would instead silently drop most passive casts and make them look unreliable. The per-binding cooldown
     * is the real rate limit, and it is durable.
     */
    public void trigger(Player player, SkillTrigger trigger) {
        Objects.requireNonNull(trigger, "skill trigger");
        if (player == null || !player.isOnline()) return;
        UUID playerId = player.getUniqueId();
        boolean announce = trigger.announces();
        if (announce && !inFlight.add(playerId)) return;
        boolean accepted = tasks.submit(playerId, () -> prepareTrigger(playerId, trigger, announce));
        if (!accepted && announce) inFlight.remove(playerId);
    }

    /**
     * Finds this trigger's bindings from stored state, then prepares each.
     *
     * <p>Runs on the player's queue thread, so the registry and repository reads are off the main thread.
     */
    private void prepareTrigger(UUID playerId, SkillTrigger trigger, boolean announce) {
        // Reads every binding and refreshes the index in the same pass, so the listener's cancel decision
        // for the next press is informed by what this press found.
        List<PetBinding> all = reindex(playerId);
        if (all == null) {
            if (announce) {
                finish(playerId, Messages.line(MessageKey.SKILL_PREPARE_FAILED,
                        Messages.of("detail", "player state could not be read")));
            } else {
                inFlight.remove(playerId);
            }
            return;
        }
        List<PetBinding> matches = all.stream()
                .filter(match -> match.binding().trigger() == trigger)
                .toList();
        if (matches.isEmpty()) {
            if (announce) inFlight.remove(playerId);
            return;
        }
        for (PetBinding match : matches) {
            prepare(playerId, match.petId(), match.binding().bindingId(), trigger, announce);
        }
    }

    /** One matched binding, so a trigger's fan-out carries which pet each binding came from. */
    private record PetBinding(UUID petId, SkillBinding binding) {}

    /**
     * What an owner's active pets react to.
     *
     * @param lowestHealthThreshold the loosest low-health threshold among their bindings, so the listener
     *     can watch a single number rather than every binding's
     */
    private record OwnerTriggers(java.util.Set<SkillTrigger> triggers, Double lowestHealthThreshold) {
        static final OwnerTriggers NONE = new OwnerTriggers(java.util.Set.of(), null);
    }

    /**
     * Whether this owner has any pet skill on this trigger.
     *
     * <p>Main-thread-safe and allocation-free: a listener deciding whether to cancel a drop cannot wait on
     * disk. Answers from the last-known index, and false when nothing is known yet — so the very first
     * press after a login may pass the vanilla action through. That is the right way to be wrong: the
     * alternative is eating a player's item because of a skill they might not have.
     */
    public boolean hasTrigger(UUID ownerId, SkillTrigger trigger) {
        if (ownerId == null || trigger == null) return false;
        return triggerIndex.getOrDefault(ownerId, OwnerTriggers.NONE).triggers().contains(trigger);
    }

    /** The loosest low-health threshold among an owner's bindings, or null when they have none. */
    public Double lowHealthThreshold(UUID ownerId) {
        if (ownerId == null) return null;
        return triggerIndex.getOrDefault(ownerId, OwnerTriggers.NONE).lowestHealthThreshold();
    }

    /**
     * Rebuilds an owner's trigger index from their stored pets.
     *
     * <p>Submitted to their queue, so it never blocks the caller. Called when their active pets change, and
     * on join, since an index that is only refreshed by dispatch would never learn about a cancelling
     * trigger the player has not successfully used yet.
     */
    public void refreshTriggers(UUID ownerId) {
        if (ownerId == null) return;
        tasks.submit(ownerId, () -> reindex(ownerId));
    }

    /** Drops an owner's index. Called on quit, so the map cannot grow for the server's lifetime. */
    public void forget(UUID ownerId) {
        if (ownerId != null) triggerIndex.remove(ownerId);
    }

    private List<PetBinding> reindex(UUID playerId) {
        List<PetBinding> all;
        try {
            var state = players.snapshot(playerId);
            var definitions = registry.current().definitions();
            all = state.pets().stream()
                    .filter(pet -> state.desiredActivePetIds().contains(pet.id()))
                    .flatMap(pet -> {
                        PetDefinition definition = definitions.get(pet.definitionId());
                        if (definition == null) return java.util.stream.Stream.<PetBinding>empty();
                        return SkillBindingProjection.read(definition).stream()
                                .map(binding -> new PetBinding(pet.id(), binding));
                    })
                    .limit(SkillBindingProjection.MAX_BINDINGS)
                    .toList();
        } catch (IOException | RuntimeException failure) {
            return null;
        }
        java.util.EnumSet<SkillTrigger> triggers = java.util.EnumSet.noneOf(SkillTrigger.class);
        Double lowest = null;
        for (PetBinding match : all) {
            triggers.add(match.binding().trigger());
            if (match.binding().trigger() == SkillTrigger.ON_LOW_HEALTH) {
                double threshold = match.binding().healthThreshold();
                // The loosest, so the listener notices the first crossing any binding cares about; the
                // controller still applies each binding's own threshold before casting.
                lowest = lowest == null ? threshold : Math.max(lowest, threshold);
            }
        }
        triggerIndex.put(playerId, new OwnerTriggers(java.util.Set.copyOf(triggers), lowest));
        return all;
    }


    public void adminCommand(CommandSender sender, List<String> arguments) {
        Objects.requireNonNull(sender, "skill admin sender");
        List<String> values = List.copyOf(arguments == null ? List.of() : arguments);
        if (values.size() == 2 && values.getFirst().equalsIgnoreCase("pending")) {
            UUID playerId = parsePlayer(sender, values.get(1));
            if (playerId != null) inspectPending(sender, playerId);
            return;
        }
        if (values.size() == 4 && values.getFirst().equalsIgnoreCase("rollback")) {
            UUID playerId = parsePlayer(sender, values.get(1));
            UUID petId = parseUuid(sender, values.get(2), "pet");
            UUID actionId = parseUuid(sender, values.get(3), "action");
            if (playerId != null && petId != null && actionId != null) {
                rollbackPending(sender, playerId, petId, actionId);
            }
            return;
        }
        message(sender, "Use /pet admin skill pending <player_name> or "
                + "/pet admin skill rollback <player_name> <pet-uuid> <action-uuid>.", NamedTextColor.YELLOW);
    }

    /**
     * @param trigger which trigger asked for this, or null for a hand-cast {@code /pet skill}
     * @param announce whether refusals reach the player, false for a passive trigger nobody asked for
     */
    private void prepare(UUID playerId, UUID petId, String bindingId, SkillTrigger trigger, boolean announce) {
        try {
            var state = players.snapshot(playerId);
            if (!state.desiredActivePetIds().contains(petId)) {
                report(playerId, announce, Messages.line(MessageKey.SKILL_REQUIRES_ACTIVE_PET));
                return;
            }
            var pet = state.pets().stream().filter(candidate -> candidate.id().equals(petId)).findFirst().orElse(null);
            if (pet == null) {
                report(playerId, announce, Messages.line(MessageKey.SKILL_PET_NOT_OWNED));
                return;
            }
            PetDefinition definition = registry.current().definitions().get(pet.definitionId());
            SkillBinding binding = definition == null ? null : SkillBindingProjection.read(definition).stream()
                    .filter(candidate -> candidate.bindingId().equals(bindingId)).findFirst().orElse(null);
            // A trigger dispatch already matched the trigger; a hand cast has to be an ACTIVE binding, since
            // /pet skill firing a passive one would let a player bypass the condition it exists for.
            boolean triggerMatches = trigger == null
                    ? binding != null && binding.trigger() == SkillTrigger.ACTIVE
                    : binding != null && binding.trigger() == trigger;
            if (!triggerMatches) {
                report(playerId, announce, Messages.line(MessageKey.SKILL_BINDING_NOT_FOUND));
                return;
            }
            SkillProvider provider = providers.provider();
            // Each refusal names itself. All three used to send one message that said only "provider or
            // skill ID is unavailable", so an operator whose skill silently never fired could not tell a
            // misspelled provider from a MythicMobs that had not loaded from a skill name that did not
            // exist — and the most common cause, a casing mismatch, is now not a refusal at all.
            if (!provider.providerId().equalsIgnoreCase(binding.provider())) {
                report(playerId, announce, Messages.line(MessageKey.SKILL_PROVIDER_MISMATCH,
                        Messages.of("provider", binding.provider()),
                        Messages.of("detail", provider.providerId())));
                return;
            }
            if (!provider.catalog().health().available()) {
                report(playerId, announce, Messages.line(MessageKey.SKILL_PROVIDER_UNAVAILABLE,
                        Messages.of("detail", providerDetail(provider))));
                return;
            }
            // The provider's own spelling, not the definition's. MythicMobs registers a skill under its
            // YAML node name exactly as written, so a definition that names it in another casing has to be
            // translated before the cast rather than refused.
            String skillId = provider.catalog().canonical(binding.skillId());
            if (skillId == null) {
                report(playerId, announce, Messages.line(MessageKey.SKILL_NOT_REGISTERED,
                        Messages.of("detail", binding.skillId())));
                return;
            }
            if (Math.random() >= binding.chance()) {
                report(playerId, announce, Messages.line(MessageKey.SKILL_CHANCE_MISSED),
                        FeedbackEvent.SKILL_CHANCE_MISSED);
                return;
            }
            UUID actionId = UUID.randomUUID();
            long now = System.currentTimeMillis();
            ProgressionConfig config = progression;
            RepositorySkillActionResult prepared = actions.prepare(
                    playerId, state.revision(), petId, binding, actionId, now,
                    config.maxStamina(), config.staminaRegenPerSecond());
            if (prepared.status() == RepositorySkillActionResult.Status.COOLDOWN) {
                // The one refusal a player is owed even when they did not press anything: a passive skill
                // that is cooling down looks identical to one that is broken.
                cooldownNotice(playerId, prepared.cooldownRemainingMillis(), announce);
                return;
            }
            if (prepared.status() != RepositorySkillActionResult.Status.PREPARED
                    && prepared.status() != RepositorySkillActionResult.Status.ALREADY_PREPARED) {
                report(playerId, announce, Messages.line(MessageKey.SKILL_REJECTED,
                        Messages.of("status", words(prepared.status()))), FeedbackEvent.SKILL_REJECTED);
                return;
            }
            runMain(playerId, () -> castPrepared(playerId, petId, binding, skillId, actionId, announce));
        } catch (IOException | RuntimeException failure) {
            report(playerId, announce, Messages.line(MessageKey.SKILL_PREPARE_FAILED,
                    Messages.of("detail", detail(failure))));
        }
    }

    /**
     * Tells the player how long is left, on the action bar with a sound.
     *
     * <p>Always shown, whether the cast was asked for or not, because "nothing happened" and "not yet" are
     * indistinguishable in the world and only one of them is a bug worth reporting.
     */
    private void cooldownNotice(UUID playerId, long remainingMillis, boolean announce) {
        Component text = Messages.line(MessageKey.ACTION_BAR_SKILL_COOLDOWN,
                Messages.of("remaining", Displays.remaining(remainingMillis)));
        inFlight.remove(playerId);
        runMain(playerId, () -> {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) return;
            Feedback.emit(player, FeedbackEvent.SKILL_COOLING_DOWN, text);
            // Chat as well, but only when they typed the command: they are reading chat already, and a
            // keypress-triggered skill would otherwise fill the log.
            if (announce) {
                player.sendMessage(Messages.line(MessageKey.SKILL_COOLDOWN,
                        Messages.of("remaining", Displays.remaining(remainingMillis))));
            }
        });
    }

    /** Reports an outcome, or drops it silently when nobody asked for the cast. */
    private void report(UUID playerId, boolean announce, Component text) {
        report(playerId, announce, text, null);
    }

    private void report(UUID playerId, boolean announce, Component text, FeedbackEvent event) {
        if (announce) {
            finish(playerId, text, event);
            return;
        }
        inFlight.remove(playerId);
    }

    /** Why the provider says it is unusable, or a stand-in when it did not say. */
    private static String providerDetail(SkillProvider provider) {
        String detail = provider.catalog().health().detail();
        return detail == null || detail.isBlank() ? "no reason given" : detail;
    }

    private void castPrepared(
            UUID playerId, UUID petId, SkillBinding binding, String skillId, UUID actionId, boolean announce) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            queueRollback(playerId, petId, actionId, Messages.plain(Messages.line(MessageKey.SKILL_PLAYER_LEFT)),
                    announce);
            return;
        }
        SkillCastResult cast;
        try {
            // Targets are chosen here, on the main thread, immediately before the cast — the shortest
            // possible window between "what is nearby" and hitting it. Passing them explicitly is what makes
            // the cast a *pet's* cast: without them MythicMobs targets from the caster, and the caster is the
            // owning player, so the skill aimed wherever the player's own targeting rules pointed.
            List<UUID> targets = targets(player, petId, binding);
            cast = providers.provider().cast(new SkillCastRequest(
                    actionId, playerId, playerId, petId, skillId, binding.targetPolicy(),
                    targets, binding.power(),
                    Map.of("world", player.getWorld().getName())));
        } catch (RuntimeException | LinkageError failure) {
            queueRollback(playerId, petId, actionId, Messages.plain(Messages.line(
                    MessageKey.SKILL_PROVIDER_FAILED, Messages.of("detail", detail(failure)))), announce);
            return;
        }
        if (!cast.succeeded()) {
            queueRollback(playerId, petId, actionId, Messages.plain(Messages.line(
                    MessageKey.SKILL_CAST_FAILED, Messages.of("detail", cast.detail()))), announce);
            return;
        }
        // The cast has already happened, so the player is told now rather than after the durable write:
        // the write confirms bookkeeping, and waiting for it would put the cue behind the visible effect.
        Feedback.emit(player, FeedbackEvent.SKILL_TRIGGERED);
        if (!tasks.submit(playerId, () -> complete(playerId, petId, actionId, announce))) {
            report(playerId, announce, Messages.line(MessageKey.SKILL_COMPLETION_NOT_SCHEDULED));
        }
    }

    /**
     * The entities this cast should aim at, with the owner's own pets excluded from the search.
     *
     * <p>Empty when the pet is not rendered, since a policy relative to the pet has no anchor then, and for
     * {@link io.github.salyvn.omnipet.core.skill.SkillTargetPolicy#PROVIDER_DEFAULT}, where empty is the
     * point: the skill's own {@code @Target} clause decides.
     */
    private List<UUID> targets(Player player, UUID petId, SkillBinding binding) {
        if (runtime == null) {
            return PaperSkillTargetResolver.resolve(
                    binding.targetPolicy(), player, null, binding.targetRange(), List.of());
        }
        List<UUID> ownPetEntities = runtime.petEntityIds(player.getUniqueId());
        org.bukkit.entity.Entity petEntity = runtime.petEntity(player.getUniqueId(), petId);
        return PaperSkillTargetResolver.resolve(
                binding.targetPolicy(), player, petEntity, binding.targetRange(), ownPetEntities);
    }

    private void complete(UUID playerId, UUID petId, UUID actionId, boolean announce) {
        try {
            var current = players.snapshot(playerId);
            ProgressionConfig config = progression;
            RepositorySkillActionResult result = actions.complete(
                    playerId, current.revision(), petId, actionId, System.currentTimeMillis(),
                    config.maxStamina(), config.staminaRegenPerSecond());
            if (result.status() == RepositorySkillActionResult.Status.COMPLETED) {
                // The action bar already said it fired. A chat line as well is for the player who typed the
                // command and is looking at chat for an answer.
                report(playerId, announce, Messages.line(MessageKey.SKILL_SUCCEEDED));
            } else {
                // Not silenced for a passive trigger: the cast happened and the bookkeeping did not, which
                // an operator has to see whether or not the player asked for it.
                finish(playerId, Messages.line(MessageKey.SKILL_COMPLETION_NEEDS_REVIEW,
                        Messages.of("status", words(result.status()))));
            }
        } catch (IOException | RuntimeException failure) {
            finish(playerId, Messages.line(MessageKey.SKILL_RESERVATION_PENDING,
                    Messages.of("detail", detail(failure))));
        }
    }

    private void queueRollback(UUID playerId, UUID petId, UUID actionId, String detail, boolean announce) {
        boolean accepted = tasks.submit(playerId, () -> {
            try {
                var current = players.snapshot(playerId);
                actions.rollback(playerId, current.revision(), petId, actionId);
            } catch (IOException | RuntimeException failure) {
                finish(playerId, Messages.line(MessageKey.SKILL_ROLLBACK_NEEDS_REVIEW,
                        Messages.of("detail", detail), Messages.of("reason", detail(failure))));
                return;
            }
            report(playerId, announce, Messages.line(MessageKey.SKILL_ROLLED_BACK, Messages.of("detail", detail)));
        });
        if (!accepted) {
            finish(playerId, Messages.line(MessageKey.SKILL_ROLLBACK_NOT_SCHEDULED, Messages.of("detail", detail)));
        }
    }

    private void inspectPending(CommandSender sender, UUID playerId) {
        if (!tasks.submit(playerId, () -> {
            try {
                var state = players.snapshot(playerId);
                List<String> rows = state.pets().stream()
                        .flatMap(pet -> PetSkillStateProjection.read(pet).pendingActions().values().stream()
                                .map(action -> "pet=" + pet.id() + " action=" + action.actionId()
                                        + " binding=" + action.bindingId() + " createdAt="
                                        + action.createdAtEpochMillis()))
                        .limit(100)
                        .toList();
                runMain(() -> {
                    if (rows.isEmpty()) {
                        message(sender, "No pending skill reservations for " + playerId + ".", NamedTextColor.GREEN);
                        return;
                    }
                    message(sender, "Pending skill reservations for " + playerId + ":", NamedTextColor.YELLOW);
                    rows.forEach(row -> message(sender, row, NamedTextColor.GRAY));
                    if (rows.size() == 100) {
                        message(sender, "Output capped at 100 rows; inspect player data before bulk recovery.",
                                NamedTextColor.RED);
                    }
                });
            } catch (IOException | RuntimeException failure) {
                runMain(() -> message(sender, "Pending skill inspection failed: " + detail(failure) + ".",
                        NamedTextColor.RED));
            }
        })) {
            message(sender, "Pending skill inspection could not be scheduled.", NamedTextColor.RED);
        }
    }

    private void rollbackPending(CommandSender sender, UUID playerId, UUID petId, UUID actionId) {
        if (!tasks.submit(playerId, () -> {
            try {
                var state = players.snapshot(playerId);
                RepositorySkillActionResult result = actions.rollback(playerId, state.revision(), petId, actionId);
                runMain(() -> message(sender,
                        result.status() == RepositorySkillActionResult.Status.ROLLED_BACK
                                ? "Pending skill reservation rolled back."
                                : "Skill rollback rejected: " + words(result.status()) + ".",
                        result.status() == RepositorySkillActionResult.Status.ROLLED_BACK
                                ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
            } catch (IOException | RuntimeException failure) {
                runMain(() -> message(sender, "Skill rollback failed: " + detail(failure) + ".",
                        NamedTextColor.RED));
            }
        })) {
            message(sender, "Skill rollback could not be scheduled.", NamedTextColor.RED);
        }
    }

    private void finish(UUID playerId, Component text) {
        finish(playerId, text, null);
    }

    /**
     * Every skill outcome funnels through here, so the cue attaches once rather than at each of the
     * dozen call sites that report a status.
     */
    private void finish(UUID playerId, Component text, FeedbackEvent event) {
        inFlight.remove(playerId);
        runMain(playerId, () -> {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                player.sendMessage(text);
                Feedback.emit(player, event);
            }
        });
    }

    private void runMain(UUID playerId, Runnable action) {
        if (!plugin.isEnabled()) {
            inFlight.remove(playerId);
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, action);
    }

    private void runMain(Runnable action) {
        if (!plugin.isEnabled()) return;
        plugin.getServer().getScheduler().runTask(plugin, action);
    }

    /** Operator output: an audit trail, deliberately not catalog-owned. */
    private static void message(CommandSender sender, String text, NamedTextColor color) {
        sender.sendMessage(Component.text("OmniPet: " + text, color));
    }

    /**
     * Resolves a player argument from an online name, then from a UUID.
     *
     * <p>Only the player argument accepts a name; a pet or action ID has no name to resolve from and
     * stays strict. Online-only, because an offline name needs a blocking profile lookup.
     */
    private static UUID parsePlayer(CommandSender sender, String raw) {
        org.bukkit.entity.Player online = raw == null || Bukkit.getServer() == null
                ? null
                : Bukkit.getPlayerExact(raw);
        if (online != null) return online.getUniqueId();
        return parseUuid(sender, raw, "player");
    }

    private static UUID parseUuid(CommandSender sender, String raw, String label) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException invalid) {
            message(sender, label + " must be an online player name or a valid UUID.", NamedTextColor.RED);
            return null;
        }
    }

    private static String words(Enum<?> value) {
        return Displays.words(value);
    }

    private static String detail(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }
}
