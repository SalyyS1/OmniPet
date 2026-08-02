package io.github.salyvn.omnipet.paper.management;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import io.github.salyvn.omnipet.core.persistence.RegistrySnapshotRepository;
import io.github.salyvn.omnipet.core.progression.CultivationItemActionService;
import io.github.salyvn.omnipet.core.progression.CultivationItemActionTransaction;
import io.github.salyvn.omnipet.core.progression.ProgressionConfig;
import io.github.salyvn.omnipet.core.progression.ProgressionMutationContext;

/** Bounded join/admin recovery for durable cultivation item transactions. */
public final class PaperCultivationRecoveryController {
    private static final int JOIN_LIMIT = 16;
    private final JavaPlugin plugin;
    private final CultivationItemActionService actions;
    private final PaperPetManagementController management;
    private final RegistrySnapshotRepository registry;
    private final Executor executor;
    private volatile ProgressionConfig progression;
    private volatile boolean closed;

    public PaperCultivationRecoveryController(
            JavaPlugin plugin,
            CultivationItemActionService actions,
            PaperPetManagementController management,
            RegistrySnapshotRepository registry,
            ProgressionConfig progression,
            Executor executor) {
        this.plugin = Objects.requireNonNull(plugin, "cultivation recovery plugin");
        this.actions = Objects.requireNonNull(actions, "cultivation action service");
        this.management = Objects.requireNonNull(management, "pet management controller");
        this.registry = Objects.requireNonNull(registry, "pet registry");
        this.progression = Objects.requireNonNull(progression, "progression config");
        this.executor = Objects.requireNonNull(executor, "cultivation recovery executor");
    }

    public void updateProgression(ProgressionConfig next) {
        progression = Objects.requireNonNull(next, "progression config");
    }

    public void onJoin(Player player) {
        if (closed || player == null || !player.isOnline()) return;
        pending(player.getUniqueId(), JOIN_LIMIT).thenCompose(list -> recoverSequential(player, list, 0))
                .whenComplete((ignored, failure) -> {
                    if (failure != null && !closed) {
                        plugin.getLogger().warning("Cultivation recovery failed for "
                                + player.getUniqueId() + ": " + detail(failure));
                    }
                });
    }

    public CompletionStage<List<CultivationItemActionTransaction>> pending(UUID playerId, int limit) {
        return supply(() -> actions.pending(playerId, limit));
    }

    public CompletionStage<List<CultivationItemActionTransaction>> operatorReview(UUID playerId, int limit) {
        return supply(() -> actions.operatorReview(playerId, limit));
    }

    public CompletionStage<PetManagementOutcome> recover(Player player, UUID actionToken) {
        if (closed || player == null || !player.isOnline()) {
            return CompletableFuture.completedFuture(PetManagementControllerContext.outcome(
                    PetManagementOutcome.Status.ERROR, null, null, null, actionToken, null,
                    "cultivation recovery requires an online player"));
        }
        return supply(() -> actions.find(actionToken).orElseThrow(
                        () -> new IOException("cultivation action does not exist: " + actionToken)))
                .thenCompose(action -> {
                    if (!action.playerId().equals(player.getUniqueId())) {
                        return CompletableFuture.completedFuture(PetManagementControllerContext.outcome(
                                PetManagementOutcome.Status.ERROR, null, null, null, actionToken, null,
                                "cultivation action player identity mismatch"));
                    }
                    return context(player, action).thenCompose(context -> management.recoverOwnedCultivation(
                            action.playerId(), action.actionToken(), context));
                });
    }

    public void shutdown() {
        closed = true;
    }

    private CompletionStage<Void> recoverSequential(
            Player player,
            List<CultivationItemActionTransaction> pending,
            int index) {
        if (closed || index >= pending.size() || !player.isOnline()) {
            return CompletableFuture.completedFuture(null);
        }
        CultivationItemActionTransaction action = pending.get(index);
        return recover(player, action.actionToken()).thenCompose(outcome -> {
            if (outcome.status() == PetManagementOutcome.Status.PERSISTED_CONSUMPTION_PENDING) {
                plugin.getLogger().warning("Cultivation action " + action.actionToken()
                        + " remains pending: " + outcome.detail());
            }
            return recoverSequential(player, pending, index + 1);
        });
    }

    private CompletionStage<ProgressionMutationContext> context(
            Player player,
            CultivationItemActionTransaction action) {
        return management.open(player.getUniqueId(), player.getUniqueId(), action.petId())
                .thenApply(outcome -> {
                    try {
                        if (outcome.view() != null) {
                            return PetManagementMenuSupport.progressionContext(
                                    outcome.view(), progression, registry);
                        }
                        return fallbackContext();
                    } finally {
                        if (outcome.session() != null) management.close(outcome.session());
                    }
                });
    }

    private ProgressionMutationContext fallbackContext() {
        ProgressionConfig current = progression;
        return new ProgressionMutationContext(
                current, null, java.util.Map.of(), current.maxStamina(), System.currentTimeMillis());
    }

    private <T> CompletionStage<T> supply(IoSupplier<T> supplier) {
        if (closed) return CompletableFuture.failedFuture(
                new IllegalStateException("cultivation recovery is shutting down"));
        CompletableFuture<T> future = new CompletableFuture<>();
        try {
            executor.execute(() -> {
                if (closed) {
                    future.completeExceptionally(new IllegalStateException(
                            "cultivation recovery is shutting down"));
                    return;
                }
                try {
                    future.complete(supplier.get());
                } catch (IOException | RuntimeException failure) {
                    future.completeExceptionally(failure);
                }
            });
        } catch (RuntimeException failure) {
            future.completeExceptionally(failure);
        }
        return future;
    }

    private static String detail(Throwable failure) {
        Throwable cause = failure instanceof java.util.concurrent.CompletionException && failure.getCause() != null
                ? failure.getCause() : failure;
        String message = cause.getMessage();
        return message == null || message.isBlank() ? cause.getClass().getSimpleName() : message;
    }

    @FunctionalInterface
    private interface IoSupplier<T> { T get() throws IOException; }
}
