package io.github.salyvn.omnipet.paper.management;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import io.github.salyvn.omnipet.core.domain.PlayerState;
import io.github.salyvn.omnipet.core.incubation.EggEscrowItemObservation;
import io.github.salyvn.omnipet.core.progression.CultivationItemActionKind;
import io.github.salyvn.omnipet.core.progression.CultivationItemActionService;
import io.github.salyvn.omnipet.core.progression.CultivationItemActionStage;
import io.github.salyvn.omnipet.core.progression.CultivationItemActionTransaction;
import io.github.salyvn.omnipet.core.progression.ProgressionMutationContext;
import io.github.salyvn.omnipet.core.progression.RepositoryProgressionResult;
import io.github.salyvn.omnipet.paper.incubation.EggInventoryMutationResult;

/** Remove-first cultivation saga with durable progression receipts and exact refunds. */
final class PetCultivationActionCoordinator {
    private final PetManagementControllerContext context;
    private final CultivationItemActionService actions;
    private final PetConsumableInventoryPort inventory;
    private final PetManagementMainThread mainThread;
    private final Set<CompletableFuture<?>> queuedMainThreadCalls = ConcurrentHashMap.newKeySet();
    private volatile boolean closed;

    PetCultivationActionCoordinator(
            PetManagementControllerContext context,
            CultivationItemActionService actions,
            PetConsumableInventoryPort inventory,
            PetManagementMainThread mainThread) {
        this.context = Objects.requireNonNull(context, "management controller context");
        this.actions = Objects.requireNonNull(actions, "cultivation item actions");
        this.inventory = Objects.requireNonNull(inventory, "cultivation inventory");
        this.mainThread = Objects.requireNonNull(mainThread, "management main-thread bridge");
    }

    CompletionStage<Result> execute(
            PetManagementSession session,
            ProgressionMutationContext progression,
            PetManagementOperation operation,
            PetConsumableInventoryPort.Capture capture) {
        CultivationItemActionTransaction requested = transaction(session, operation, capture);
        return context.submitValue(() -> actions.prepare(requested))
                .thenCompose(action -> recover(action, progression))
                .exceptionally(failure -> result(
                        Status.RECOVERY_PENDING, requested, null, null,
                        "cultivation action requires recovery: "
                                + PetManagementControllerContext.shortDetail(failure)));
    }

    CompletionStage<Result> recover(
            UUID actionToken,
            ProgressionMutationContext progression) {
        return context.submitValue(() -> actions.find(actionToken)
                        .orElseThrow(() -> new java.io.IOException(
                                "cultivation action does not exist: " + actionToken)))
                .thenCompose(action -> recover(action, progression))
                .exceptionally(failure -> result(
                        Status.RECOVERY_PENDING, null, null, null,
                        "cultivation recovery failed: "
                                + PetManagementControllerContext.shortDetail(failure)));
    }

    void shutdown() {
        closed = true;
        IllegalStateException failure = new IllegalStateException("pet management is shutting down");
        for (CompletableFuture<?> call : queuedMainThreadCalls) call.completeExceptionally(failure);
        queuedMainThreadCalls.clear();
    }

    private CompletionStage<Result> recover(
            CultivationItemActionTransaction action,
            ProgressionMutationContext progression) {
        return switch (action.stage()) {
            case PREPARED -> recoverPrepared(action, progression);
            case ITEM_REMOVED -> recoverRemoved(action, progression);
            case REFUND_PENDING -> recoverRefund(action, progression);
            case COMMITTED -> recoverCommitted(action);
            case REFUNDED -> snapshot(action, Status.REFUNDED, "cultivation item was already refunded");
            case OPERATOR_REVIEW -> snapshot(
                    action, Status.OPERATOR_REVIEW, "cultivation action requires operator review");
        };
    }

    private CompletionStage<Result> recoverPrepared(
            CultivationItemActionTransaction action,
            ProgressionMutationContext progression) {
        return onMain(() -> inventory.observe(action.playerId(), action.item())).thenCompose(observation -> {
            if (observation == EggEscrowItemObservation.AMBIGUOUS) {
                return review(action, "prepared cultivation inventory is ambiguous");
            }
            if (observation == EggEscrowItemObservation.MATCHING_ITEM_ABSENT) {
                return context.submitValue(() -> context.repository.hasCultivationAction(
                                action.playerId(), action.petId(), action))
                        .thenCompose(applied -> applied
                                ? markRemovedAndRecover(action, progression)
                                : review(action, "prepared item is absent without a durable progression receipt"));
            }
            return onMain(() -> inventory.removeOne(action.playerId(), action.item()))
                    .thenCompose(removed -> switch (removed) {
                        case REMOVED -> markRemovedAndRecover(action, progression);
                        case AMBIGUOUS, NOT_MATCHING -> review(
                                action, "cultivation item removal is ambiguous: " + removed);
                        default -> snapshot(
                                action, Status.RECOVERY_PENDING,
                                "cultivation item was not removed: " + removed);
                    });
        });
    }

    private CompletionStage<Result> markRemovedAndRecover(
            CultivationItemActionTransaction action,
            ProgressionMutationContext progression) {
        return context.submitValue(() -> actions.markItemRemoved(action.actionToken()))
                .thenCompose(removed -> removed.stage() == CultivationItemActionStage.ITEM_REMOVED
                        ? recoverRemoved(removed, progression)
                        : review(removed, "ITEM_REMOVED transition was rejected"));
    }

    private CompletionStage<Result> recoverRemoved(
            CultivationItemActionTransaction action,
            ProgressionMutationContext progression) {
        return onMain(() -> inventory.observe(action.playerId(), action.item())).thenCompose(observation -> {
            if (observation != EggEscrowItemObservation.MATCHING_ITEM_ABSENT) {
                return review(action, "removed cultivation item is no longer provably absent");
            }
            return context.submitValue(() -> applyOrCommit(action, progression))
                    .handle(ApplyAttempt::new)
                    .thenCompose(attempt -> {
                        if (attempt.failure() == null) {
                            return attempt.result().persisted()
                                    ? commitAndAcknowledge(action, attempt.result())
                                    : markRefundPending(action, attempt.result());
                        }
                        return context.submitValue(() -> context.repository.hasCultivationAction(
                                        action.playerId(), action.petId(), action))
                                .thenCompose(persisted -> persisted
                                        ? commitExisting(action)
                                        : markRefundPending(action, null));
                    });
        });
    }

    private CompletionStage<Result> commitExisting(CultivationItemActionTransaction action) {
        return context.submitValue(() -> {
            PlayerState state = context.repository.snapshot(action.playerId());
            return new RepositoryProgressionResult(
                    RepositoryProgressionResult.Status.PERSISTED, state, null, null);
        }).thenCompose(existing -> commitAndAcknowledge(action, existing));
    }

    private RepositoryProgressionResult applyOrCommit(
            CultivationItemActionTransaction action,
            ProgressionMutationContext progression) throws java.io.IOException {
        if (context.repository.hasCultivationAction(action.playerId(), action.petId(), action)) {
            PlayerState state = context.repository.snapshot(action.playerId());
            return new RepositoryProgressionResult(
                    RepositoryProgressionResult.Status.PERSISTED, state, null, null);
        }
        return action.kind() == CultivationItemActionKind.EXPERIENCE_CANDY
                ? context.repository.addExperience(
                        action.playerId(), action.expectedPlayerRevision(), action.petId(),
                        action.experienceAmount(), progression, action)
                : context.repository.breakthrough(
                        action.playerId(), action.expectedPlayerRevision(), action.petId(),
                        action.requiredLevel(), action.requiredEvolution(), progression, action);
    }

    private CompletionStage<Result> commitAndAcknowledge(
            CultivationItemActionTransaction action,
            RepositoryProgressionResult progression) {
        return context.submitValue(() -> {
            CultivationItemActionTransaction committed = actions.commit(action.actionToken());
            if (committed.stage() != CultivationItemActionStage.COMMITTED) {
                throw new java.io.IOException("cultivation commit transition was rejected");
            }
            context.repository.acknowledgeCultivationAction(
                    committed.playerId(), committed.petId(), committed);
            PlayerState state = context.repository.snapshot(committed.playerId());
            return result(Status.COMMITTED, committed, progression, state, "cultivation action committed");
        });
    }

    private CompletionStage<Result> markRefundPending(
            CultivationItemActionTransaction action,
            RepositoryProgressionResult rejected) {
        return context.submitValue(() -> actions.markRefundPending(action.actionToken()))
                .thenCompose(pending -> recoverRefund(pending, null))
                .thenApply(result -> result.withProgression(rejected));
    }

    private CompletionStage<Result> recoverRefund(
            CultivationItemActionTransaction action,
            ProgressionMutationContext progression) {
        return context.submitValue(() -> context.repository.hasCultivationAction(
                        action.playerId(), action.petId(), action))
                .thenCompose(applied -> {
                    if (applied) {
                        RepositoryProgressionResult existing;
                        try {
                            PlayerState state = context.repository.snapshot(action.playerId());
                            existing = new RepositoryProgressionResult(
                                    RepositoryProgressionResult.Status.PERSISTED, state, null, null);
                        } catch (java.io.IOException failure) {
                            return failed(failure);
                        }
                        return commitAndAcknowledge(action.withStage(CultivationItemActionStage.ITEM_REMOVED), existing);
                    }
                    return onMain(() -> inventory.observe(action.playerId(), action.item()))
                            .thenCompose(observation -> refund(action, observation));
                });
    }

    private CompletionStage<Result> refund(
            CultivationItemActionTransaction action,
            EggEscrowItemObservation observation) {
        if (observation == EggEscrowItemObservation.AMBIGUOUS) {
            return review(action, "cultivation refund inventory is ambiguous");
        }
        CompletionStage<EggInventoryMutationResult> mutation = observation
                == EggEscrowItemObservation.MATCHING_ITEM_PRESENT
                ? CompletableFuture.completedFuture(EggInventoryMutationResult.ALREADY_PRESENT)
                : onMain(() -> inventory.refundOne(action.playerId(), action.item()));
        return mutation.thenCompose(refunded -> {
            if (refunded != EggInventoryMutationResult.REFUNDED
                    && refunded != EggInventoryMutationResult.ALREADY_PRESENT) {
                return snapshot(action, Status.RECOVERY_PENDING, "cultivation refund remains pending: " + refunded);
            }
            return context.submitValue(() -> {
                CultivationItemActionTransaction terminal = actions.markRefunded(action.actionToken());
                PlayerState state = context.repository.snapshot(action.playerId());
                return result(Status.REFUNDED, terminal, null, state, "cultivation item refunded");
            });
        });
    }

    private CompletionStage<Result> recoverCommitted(CultivationItemActionTransaction action) {
        return onMain(() -> inventory.observe(action.playerId(), action.item())).thenCompose(observation -> {
            if (observation == EggEscrowItemObservation.MATCHING_ITEM_PRESENT) {
                return snapshot(action, Status.OPERATOR_REVIEW,
                        "committed cultivation action still has its matching item");
            }
            if (observation == EggEscrowItemObservation.AMBIGUOUS) {
                return snapshot(action, Status.OPERATOR_REVIEW,
                        "committed cultivation inventory is ambiguous");
            }
            return context.submitValue(() -> {
                context.repository.acknowledgeCultivationAction(action.playerId(), action.petId(), action);
                PlayerState state = context.repository.snapshot(action.playerId());
                return result(Status.ALREADY_COMMITTED, action, null, state, "cultivation action already committed");
            });
        });
    }

    private CompletionStage<Result> review(CultivationItemActionTransaction action, String detail) {
        return context.submitValue(() -> {
            CultivationItemActionTransaction reviewed = actions.requireOperatorReview(action.actionToken());
            PlayerState state = context.repository.snapshot(action.playerId());
            return result(Status.OPERATOR_REVIEW, reviewed, null, state, detail);
        });
    }

    private CompletionStage<Result> snapshot(
            CultivationItemActionTransaction action,
            Status status,
            String detail) {
        return context.submitValue(() -> result(
                status, action, null, context.repository.snapshot(action.playerId()), detail));
    }

    private <T> CompletionStage<T> onMain(Supplier<T> task) {
        CompletableFuture<T> future = new CompletableFuture<>();
        if (closed) return failed(new IllegalStateException("pet management is shutting down"));
        queuedMainThreadCalls.add(future);
        try {
            mainThread.execute(() -> {
                if (!queuedMainThreadCalls.remove(future)) return;
                if (closed) {
                    future.completeExceptionally(new IllegalStateException("pet management is shutting down"));
                    return;
                }
                try {
                    future.complete(task.get());
                } catch (RuntimeException failure) {
                    future.completeExceptionally(failure);
                }
            });
        } catch (RuntimeException failure) {
            queuedMainThreadCalls.remove(future);
            future.completeExceptionally(failure);
        }
        return future;
    }

    private static CultivationItemActionTransaction transaction(
            PetManagementSession session,
            PetManagementOperation operation,
            PetConsumableInventoryPort.Capture capture) {
        if (!session.viewerId().equals(session.ownerId()) || !capture.viewerId().equals(session.ownerId())) {
            throw new IllegalArgumentException("cultivation item owner must match the managed pet owner");
        }
        CultivationItemActionKind kind = operation == PetManagementOperation.ADD_EXPERIENCE
                ? CultivationItemActionKind.EXPERIENCE_CANDY
                : CultivationItemActionKind.BREAKTHROUGH_STONE;
        return new CultivationItemActionTransaction(
                capture.nonce(), session.ownerId(), session.petId(), session.expectedRevision(), capture.item(), kind,
                capture.experienceAmount(), capture.requiredLevel(), capture.requiredEvolution(),
                CultivationItemActionStage.PREPARED);
    }

    private static <T> CompletionStage<T> failed(Throwable failure) {
        return CompletableFuture.failedFuture(failure);
    }

    private static Result result(
            Status status,
            CultivationItemActionTransaction action,
            RepositoryProgressionResult progression,
            PlayerState state,
            String detail) {
        return new Result(status, action, progression, state, detail);
    }

    enum Status { COMMITTED, ALREADY_COMMITTED, REFUNDED, RECOVERY_PENDING, OPERATOR_REVIEW }

    record Result(
            Status status,
            CultivationItemActionTransaction action,
            RepositoryProgressionResult progression,
            PlayerState state,
            String detail) {
        Result {
            Objects.requireNonNull(status, "cultivation saga status");
            detail = detail == null ? "" : detail;
        }

        Result withProgression(RepositoryProgressionResult next) {
            return new Result(status, action, next, state, detail);
        }
    }

    private record ApplyAttempt(RepositoryProgressionResult result, Throwable failure) {}
}
