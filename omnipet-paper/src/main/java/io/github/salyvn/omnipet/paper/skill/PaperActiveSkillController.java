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
            UUID playerId = parseUuid(sender, values.get(1), "player");
            if (playerId != null) inspectPending(sender, playerId);
            return;
        }
        if (values.size() == 4 && values.getFirst().equalsIgnoreCase("rollback")) {
            UUID playerId = parseUuid(sender, values.get(1), "player");
            UUID petId = parseUuid(sender, values.get(2), "pet");
            UUID actionId = parseUuid(sender, values.get(3), "action");
            if (playerId != null && petId != null && actionId != null) {
                rollbackPending(sender, playerId, petId, actionId);
            }
            return;
        }
        message(sender, "Use /pet admin skill pending <player-uuid> or "
                + "/pet admin skill rollback <player-uuid> <pet-uuid> <action-uuid>.", NamedTextColor.YELLOW);
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
            if (!provider.providerId().equalsIgnoreCase(binding.provider())
                    || !provider.catalog().health().available()
                    || !provider.catalog().contains(binding.skillId())) {
                finish(playerId, Messages.line(MessageKey.SKILL_PROVIDER_UNAVAILABLE));
                return;
            }
            if (Math.random() >= binding.chance()) {
                finish(playerId, Messages.line(MessageKey.SKILL_CHANCE_MISSED));
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
                        Messages.of("status", words(prepared.status()))));
                return;
            }
            runMain(playerId, () -> castPrepared(playerId, petId, binding, actionId));
        } catch (IOException | RuntimeException failure) {
            finish(playerId, Messages.line(MessageKey.SKILL_PREPARE_FAILED,
                    Messages.of("detail", detail(failure))));
        }
    }

    private void castPrepared(UUID playerId, UUID petId, SkillBinding binding, UUID actionId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            queueRollback(playerId, petId, actionId, Messages.plain(Messages.line(MessageKey.SKILL_PLAYER_LEFT)));
            return;
        }
        SkillCastResult cast;
        try {
            cast = providers.provider().cast(new SkillCastRequest(
                    actionId, playerId, playerId, petId, binding.skillId(), binding.targetPolicy(),
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
                finish(playerId, Messages.line(MessageKey.SKILL_SUCCEEDED));
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
        inFlight.remove(playerId);
        runMain(playerId, () -> {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) player.sendMessage(text);
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

    private static UUID parseUuid(CommandSender sender, String raw, String label) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException invalid) {
            message(sender, label + " UUID is invalid.", NamedTextColor.RED);
            return null;
        }
    }

    private static String words(Enum<?> value) {
        return value.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
    }

    private static String detail(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }
}
