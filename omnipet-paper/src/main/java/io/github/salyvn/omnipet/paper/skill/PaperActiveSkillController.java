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
        boolean accepted = tasks.submit(playerId, () -> prepare(playerId, petId, bindingId.trim()));
        if (!accepted) finish(playerId, Messages.line(MessageKey.SKILL_NOT_SCHEDULED));
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
        message(sender, "Use /pet admin skill pending <player> or "
                + "/pet admin skill rollback <player> <pet-uuid> <action-uuid>.", NamedTextColor.YELLOW);
    }

    private void prepare(UUID playerId, UUID petId, String bindingId) {
        try {
            var state = players.snapshot(playerId);
            if (!state.desiredActivePetIds().contains(petId)) {
                finish(playerId, Messages.line(MessageKey.SKILL_REQUIRES_ACTIVE_PET));
                return;
            }
            var pet = state.pets().stream().filter(candidate -> candidate.id().equals(petId)).findFirst().orElse(null);
            if (pet == null) {
                finish(playerId, Messages.line(MessageKey.SKILL_PET_NOT_OWNED));
                return;
            }
            PetDefinition definition = registry.current().definitions().get(pet.definitionId());
            SkillBinding binding = definition == null ? null : SkillBindingProjection.read(definition).stream()
                    .filter(candidate -> candidate.bindingId().equals(bindingId)).findFirst().orElse(null);
            if (binding == null || binding.trigger() != SkillTrigger.ACTIVE) {
                finish(playerId, Messages.line(MessageKey.SKILL_BINDING_NOT_FOUND));
                return;
            }
            SkillProvider provider = providers.provider();
            // Each refusal names itself. All three used to send one message that said only "provider or
            // skill ID is unavailable", so an operator whose skill silently never fired could not tell a
            // misspelled provider from a MythicMobs that had not loaded from a skill name that did not
            // exist — and the most common cause, a casing mismatch, is now not a refusal at all.
            if (!provider.providerId().equalsIgnoreCase(binding.provider())) {
                finish(playerId, Messages.line(MessageKey.SKILL_PROVIDER_MISMATCH,
                        Messages.of("provider", binding.provider()),
                        Messages.of("detail", provider.providerId())));
                return;
            }
            if (!provider.catalog().health().available()) {
                finish(playerId, Messages.line(MessageKey.SKILL_PROVIDER_UNAVAILABLE,
                        Messages.of("detail", providerDetail(provider))));
                return;
            }
            // The provider's own spelling, not the definition's. MythicMobs registers a skill under its
            // YAML node name exactly as written, so a definition that names it in another casing has to be
            // translated before the cast rather than refused.
            String skillId = provider.catalog().canonical(binding.skillId());
            if (skillId == null) {
                finish(playerId, Messages.line(MessageKey.SKILL_NOT_REGISTERED,
                        Messages.of("detail", binding.skillId())));
                return;
            }
            if (Math.random() >= binding.chance()) {
                finish(playerId, Messages.line(MessageKey.SKILL_CHANCE_MISSED),
                        FeedbackEvent.SKILL_CHANCE_MISSED);
                return;
            }
            UUID actionId = UUID.randomUUID();
            long now = System.currentTimeMillis();
            ProgressionConfig config = progression;
            RepositorySkillActionResult prepared = actions.prepare(
                    playerId, state.revision(), petId, binding, actionId, now, config.maxStamina());
            if (prepared.status() != RepositorySkillActionResult.Status.PREPARED
                    && prepared.status() != RepositorySkillActionResult.Status.ALREADY_PREPARED) {
                finish(playerId, Messages.line(MessageKey.SKILL_REJECTED,
                        Messages.of("status", words(prepared.status()))), FeedbackEvent.SKILL_REJECTED);
                return;
            }
            runMain(playerId, () -> castPrepared(playerId, petId, binding, skillId, actionId));
        } catch (IOException | RuntimeException failure) {
            finish(playerId, Messages.line(MessageKey.SKILL_PREPARE_FAILED,
                    Messages.of("detail", detail(failure))));
        }
    }

    /** Why the provider says it is unusable, or a stand-in when it did not say. */
    private static String providerDetail(SkillProvider provider) {
        String detail = provider.catalog().health().detail();
        return detail == null || detail.isBlank() ? "no reason given" : detail;
    }

    private void castPrepared(
            UUID playerId, UUID petId, SkillBinding binding, String skillId, UUID actionId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            queueRollback(playerId, petId, actionId, Messages.plain(Messages.line(MessageKey.SKILL_PLAYER_LEFT)));
            return;
        }
        SkillCastResult cast;
        try {
            cast = providers.provider().cast(new SkillCastRequest(
                    actionId, playerId, playerId, petId, skillId, binding.targetPolicy(),
                    Map.of("world", player.getWorld().getName())));
        } catch (RuntimeException | LinkageError failure) {
            queueRollback(playerId, petId, actionId, Messages.plain(Messages.line(
                    MessageKey.SKILL_PROVIDER_FAILED, Messages.of("detail", detail(failure)))));
            return;
        }
        if (!cast.succeeded()) {
            queueRollback(playerId, petId, actionId, Messages.plain(Messages.line(
                    MessageKey.SKILL_CAST_FAILED, Messages.of("detail", cast.detail()))));
            return;
        }
        if (!tasks.submit(playerId, () -> complete(playerId, petId, actionId))) {
            finish(playerId, Messages.line(MessageKey.SKILL_COMPLETION_NOT_SCHEDULED));
        }
    }

    private void complete(UUID playerId, UUID petId, UUID actionId) {
        try {
            var current = players.snapshot(playerId);
            RepositorySkillActionResult result = actions.complete(
                    playerId, current.revision(), petId, actionId, System.currentTimeMillis(), progression.maxStamina());
            if (result.status() == RepositorySkillActionResult.Status.COMPLETED) {
                finish(playerId, Messages.line(MessageKey.SKILL_SUCCEEDED), FeedbackEvent.SKILL_SUCCEEDED);
            } else {
                finish(playerId, Messages.line(MessageKey.SKILL_COMPLETION_NEEDS_REVIEW,
                        Messages.of("status", words(result.status()))));
            }
        } catch (IOException | RuntimeException failure) {
            finish(playerId, Messages.line(MessageKey.SKILL_RESERVATION_PENDING,
                    Messages.of("detail", detail(failure))));
        }
    }

    private void queueRollback(UUID playerId, UUID petId, UUID actionId, String detail) {
        boolean accepted = tasks.submit(playerId, () -> {
            try {
                var current = players.snapshot(playerId);
                actions.rollback(playerId, current.revision(), petId, actionId);
            } catch (IOException | RuntimeException failure) {
                finish(playerId, Messages.line(MessageKey.SKILL_ROLLBACK_NEEDS_REVIEW,
                        Messages.of("detail", detail), Messages.of("reason", detail(failure))));
                return;
            }
            finish(playerId, Messages.line(MessageKey.SKILL_ROLLED_BACK, Messages.of("detail", detail)));
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
