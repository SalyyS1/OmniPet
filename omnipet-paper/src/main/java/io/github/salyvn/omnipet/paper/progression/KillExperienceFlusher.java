package io.github.salyvn.omnipet.paper.progression;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import io.github.salyvn.omnipet.core.domain.PetInstance;
import io.github.salyvn.omnipet.core.domain.PlayerState;
import io.github.salyvn.omnipet.core.persistence.PlayerStateRepository;
import io.github.salyvn.omnipet.core.persistence.RegistrySnapshotRepository;
import io.github.salyvn.omnipet.core.persistence.StaleRevisionException;
import io.github.salyvn.omnipet.core.progression.PendingExperienceLedger;
import io.github.salyvn.omnipet.core.progression.ProgressionConfig;
import io.github.salyvn.omnipet.core.progression.ProgressionMutationContext;
import io.github.salyvn.omnipet.core.progression.RepositoryProgressionService;
import io.github.salyvn.omnipet.paper.task.PerPlayerTaskQueue;

/**
 * Writes banked kill experience to disk, one write per player per interval.
 *
 * <p>The counterpart to {@link KillExperienceListener}. Kills bank a number on the main thread; this runs
 * off it and turns a burst of them into a single mutation under the player's revision lock. On a grinder
 * that is the difference between a write per kill and a write every few seconds.
 *
 * <p>Runs on the per-player task queue rather than on a bare executor, so a flush cannot interleave with
 * that player's own vault or cultivation work — the revision lock would reject one of them, and the loser
 * would be this, silently, on every flush.
 *
 * <p>Unjournalled, unlike EXP candy. A candy is an item the player spent and must not lose to a crash; kill
 * experience is a stream, and journalling it would write a file per kill to protect a few seconds of
 * progress. A lost race puts the amount back on the ledger instead, so nothing is dropped short of a
 * shutdown.
 */
public final class KillExperienceFlusher {
    private final PendingExperienceLedger ledger;
    private final PlayerStateRepository players;
    private final RepositoryProgressionService progression;
    private final RegistrySnapshotRepository registry;
    private final PerPlayerTaskQueue tasks;
    private final java.util.function.Supplier<ProgressionConfig> config;
    private final java.util.function.Consumer<String> warnings;

    public KillExperienceFlusher(
            PendingExperienceLedger ledger,
            PlayerStateRepository players,
            RegistrySnapshotRepository registry,
            PerPlayerTaskQueue tasks,
            java.util.function.Supplier<ProgressionConfig> config,
            java.util.function.Consumer<String> warnings) {
        this.ledger = Objects.requireNonNull(ledger, "pending experience ledger");
        this.players = Objects.requireNonNull(players, "player state repository");
        this.progression = new RepositoryProgressionService(players);
        this.registry = Objects.requireNonNull(registry, "registry snapshot repository");
        this.tasks = Objects.requireNonNull(tasks, "player task queue");
        this.config = Objects.requireNonNull(config, "progression config supplier");
        this.warnings = Objects.requireNonNull(warnings, "warning sink");
    }

    /**
     * Writes everything banked so far.
     *
     * <p>Safe to call on the main thread: it only drains the ledger and hands each owner's share to that
     * player's queue, which runs asynchronously.
     */
    public void flush() {
        Map<UUID, Map<UUID, Double>> drained = ledger.drain();
        drained.forEach((ownerId, amounts) -> {
            if (!tasks.submit(ownerId, () -> apply(ownerId, amounts))) {
                // Refused means the queue is shutting down. Put it back rather than dropping it: a restart
                // is not a reason to lose what a player earned.
                ledger.restore(ownerId, amounts);
            }
        });
    }

    /**
     * Writes one owner's banked experience, pet by pet.
     *
     * <p>What is put back on a failure is only what has not been written yet. Restoring the whole batch
     * would re-credit pets whose grant already succeeded, so a mid-batch race would pay a player twice —
     * and a contended server hits mid-batch races routinely rather than rarely.
     */
    private void apply(UUID ownerId, Map<UUID, Double> amounts) {
        Map<UUID, Double> unwritten = new java.util.LinkedHashMap<>(amounts);
        try {
            for (Map.Entry<UUID, Double> entry : amounts.entrySet()) {
                // Re-read per pet: each grant bumps the revision, so a snapshot taken once would be stale
                // for every pet after the first and every one of those grants would lose its race.
                PlayerState state = players.snapshot(ownerId);
                grant(ownerId, state, entry.getKey(), entry.getValue());
                unwritten.remove(entry.getKey());
            }
        } catch (StaleRevisionException race) {
            // Somebody else wrote first. What is left goes back and the next flush carries it, which is
            // why this needs no retry loop of its own.
            ledger.restore(ownerId, unwritten);
        } catch (IOException | RuntimeException failure) {
            ledger.restore(ownerId, unwritten);
            warnings.accept("kill experience could not be saved for " + ownerId + ": " + detail(failure));
        }
    }

    /** Grants one pet its share. */
    private void grant(UUID ownerId, PlayerState state, UUID petId, double amount) throws IOException {
        PetInstance pet = pet(state, petId);
        // Released, or given away, between the kill and the flush. Dropping the amount is the only option
        // and is the right one: it belonged to a pet that is no longer there.
        if (pet == null) return;
        progression.addExperience(ownerId, state.revision(), petId, amount, context(pet));
    }

    private static PetInstance pet(PlayerState state, UUID petId) {
        List<PetInstance> pets = state.pets();
        for (PetInstance pet : pets) {
            if (pet.id().equals(petId)) return pet;
        }
        return null;
    }

    /**
     * The progression rules for one pet, including any the definition overrides.
     *
     * <p>Built here rather than reusing the management screen's builder, which needs a GUI view model. The
     * per-definition {@code maxLevel} and {@code experienceFormula} lookups are the part that matters: a pet
     * levelled by kills has to follow the same curve as one levelled by candy, or the two paths would
     * disagree about what level a pet is.
     */
    private ProgressionMutationContext context(PetInstance pet) {
        ProgressionConfig base = config.get();
        var definition = registry.current().definitions().get(pet.definitionId());
        Object raw = definition == null ? null : definition.rawNode().get("progression");
        Map<?, ?> node = raw instanceof Map<?, ?> map ? map : Map.of();

        ProgressionConfig effective = base;
        if (node.get("maxLevel") instanceof Number number && number.intValue() >= 1) {
            effective = new ProgressionConfig(
                    Math.min(base.maxLevel(), number.intValue()), base.maxStamina(),
                    base.staminaRegenPerSecond(), base.defaultFormula(), base.formulaSamples(),
                    base.overflowPolicy());
        }
        String formula = node.get("experienceFormula") instanceof String text && !text.isBlank() ? text : null;
        return new ProgressionMutationContext(
                effective, formula, formulaValues(pet), effective.maxStamina(), System.currentTimeMillis());
    }

    /** The same formula inputs the management screen supplies, read straight off the pet. */
    private static Map<String, Double> formulaValues(PetInstance pet) {
        Map<String, Double> values = new java.util.LinkedHashMap<>();
        if (pet.rawComponents().get("progression") instanceof Map<?, ?> progression
                && progression.get("evolution") instanceof Number evolution) {
            values.put("evolution", evolution.doubleValue());
        }
        if (pet.rawComponents().get("hatching") instanceof Map<?, ?> hatching) {
            if (hatching.get("qualityScore") instanceof Number quality
                    && Double.isFinite(quality.doubleValue())) {
                values.put("quality", quality.doubleValue());
            }
        }
        return values;
    }

    private static String detail(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }
}
