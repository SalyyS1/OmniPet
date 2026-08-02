package io.github.salyvn.omnipet.paper.management;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import io.github.salyvn.omnipet.core.persistence.StaleRevisionException;
import io.github.salyvn.omnipet.core.progression.CultivationItemActionService;
import io.github.salyvn.omnipet.core.progression.ProgressionMutationContext;
import io.github.salyvn.omnipet.core.progression.RepositoryProgressionResult;

final class PetManagementCultivationActions {
    private final PetManagementControllerContext context;
    private final PetConsumableInventoryPort consumables;
    private final PetManagementMainThread mainThread;
    private final PetCultivationActionCoordinator coordinator;

    PetManagementCultivationActions(
            PetManagementControllerContext context,
            CultivationItemActionService actions,
            PetConsumableInventoryPort consumables,
            PetManagementMainThread mainThread) {
        this.context = Objects.requireNonNull(context, "management controller context");
        this.consumables = Objects.requireNonNull(consumables, "management consumable inventory");
        this.mainThread = Objects.requireNonNull(mainThread, "management main-thread bridge");
        coordinator = new PetCultivationActionCoordinator(context, actions, consumables, mainThread);
    }

    CompletionStage<PetManagementOutcome> addExperience(
            PetManagementSession session,
            ProgressionMutationContext progression) {
        return cultivate(session, progression, PetManagementOperation.ADD_EXPERIENCE,
                PetConsumableInventoryPort.Kind.EXPERIENCE_CANDY);
    }

    CompletionStage<PetManagementOutcome> breakthrough(
            PetManagementSession session,
            ProgressionMutationContext progression) {
        return cultivate(session, progression, PetManagementOperation.BREAKTHROUGH,
                PetConsumableInventoryPort.Kind.BREAKTHROUGH_STONE);
    }

    CompletionStage<PetManagementOutcome> recover(
            PetManagementSession session,
            UUID actionToken,
            ProgressionMutationContext progression) {
        PetManagementOutcome rejected = context.begin(session, PetManagementOperation.ADD_EXPERIENCE);
        if (rejected != null) return CompletableFuture.completedFuture(rejected);
        return coordinator.recover(actionToken, progression)
                .handle((result, failure) -> failure == null
                        ? outcome(session, result)
                        : PetManagementControllerContext.error(failure))
                .whenComplete((ignored, failure) -> context.inFlight.remove(session.viewerId()));
    }

    CompletionStage<PetManagementOutcome> recoverOwned(
            UUID ownerId,
            UUID actionToken,
            ProgressionMutationContext progression) {
        if (!context.inFlight.add(ownerId)) {
            return PetManagementControllerContext.completed(
                    PetManagementOutcome.Status.BUSY,
                    "another management action is already processing");
        }
        return coordinator.recover(actionToken, progression)
                .handle((result, failure) -> failure == null
                        ? recoveryOutcome(result)
                        : PetManagementControllerContext.error(failure))
                .whenComplete((ignored, failure) -> context.inFlight.remove(ownerId));
    }

    void shutdown() {
        coordinator.shutdown();
    }

    private CompletionStage<PetManagementOutcome> cultivate(
            PetManagementSession session,
            ProgressionMutationContext progression,
            PetManagementOperation operation,
            PetConsumableInventoryPort.Kind kind) {
        PetManagementOutcome rejected = context.begin(session, operation);
        if (rejected != null) return CompletableFuture.completedFuture(rejected);
        if (!isMainThread()) return finish(session, PetManagementControllerContext.completed(
                PetManagementOutcome.Status.ERROR,
                "consumable capture must run on the server main thread"));

        PetConsumableInventoryPort.CaptureResult captured;
        try {
            captured = consumables.capture(session.viewerId(), kind);
        } catch (RuntimeException failure) {
            return finish(session, CompletableFuture.completedFuture(
                    PetManagementControllerContext.error(failure)));
        }
        if (captured == null || captured.status() != PetConsumableInventoryPort.CaptureResult.Status.CAPTURED) {
            String detail = captured == null ? "consumable capture returned no result" : captured.detail();
            return finish(session, PetManagementControllerContext.completed(
                    PetManagementOutcome.Status.CONSUMABLE_UNAVAILABLE, detail));
        }
        PetConsumableInventoryPort.Capture capture = captured.capture();
        if (!capture.viewerId().equals(session.viewerId()) || capture.kind() != kind) {
            return finish(session, PetManagementControllerContext.completed(
                    PetManagementOutcome.Status.CONSUMABLE_UNAVAILABLE,
                    "captured consumable identity does not match the requested action"));
        }

        CompletionStage<PetCultivationActionCoordinator.Result> action;
        try {
            action = coordinator.execute(session, progression, operation, capture);
        } catch (RuntimeException failure) {
            return finish(session, CompletableFuture.completedFuture(
                    PetManagementControllerContext.error(failure)));
        }
        return finish(session, action.handle((result, failure) -> failure == null
                ? outcome(session, result)
                : PetManagementControllerContext.error(failure)));
    }

    private boolean isMainThread() {
        try {
            return mainThread.isMainThread();
        } catch (RuntimeException failure) {
            return false;
        }
    }

    private CompletionStage<PetManagementOutcome> finish(
            PetManagementSession session,
            CompletionStage<PetManagementOutcome> stage) {
        return stage.whenComplete((ignored, failure) -> context.inFlight.remove(session.viewerId()));
    }

    private PetManagementOutcome outcome(
            PetManagementSession session,
            PetCultivationActionCoordinator.Result result) {
        PetManagementSession next = reconcileSession(session, result);
        return switch (result.status()) {
            case COMMITTED, ALREADY_COMMITTED -> PetManagementControllerContext.outcome(
                    PetManagementOutcome.Status.PERSISTED, next,
                    context.safeView(next, result.state(), null), null, null, null, result.detail());
            case REFUNDED -> PetManagementControllerContext.outcome(
                    rejectedStatus(result.progression()), next,
                    context.safeView(next, result.state(), null), null, null, null, result.detail());
            case RECOVERY_PENDING, OPERATOR_REVIEW -> PetManagementControllerContext.outcome(
                    PetManagementOutcome.Status.PERSISTED_CONSUMPTION_PENDING, next,
                    context.safeView(next, result.state(), null), null, null, null, result.detail());
        };
    }

    private PetManagementOutcome recoveryOutcome(PetCultivationActionCoordinator.Result result) {
        PetManagementOutcome.Status status = switch (result.status()) {
            case COMMITTED, ALREADY_COMMITTED -> PetManagementOutcome.Status.PERSISTED;
            case REFUNDED -> rejectedStatus(result.progression());
            case RECOVERY_PENDING, OPERATOR_REVIEW ->
                    PetManagementOutcome.Status.PERSISTED_CONSUMPTION_PENDING;
        };
        return PetManagementControllerContext.outcome(
                status, null, null, null,
                result.action() == null ? null : result.action().actionToken(),
                null, result.detail());
    }

    private PetManagementSession reconcileSession(
            PetManagementSession session,
            PetCultivationActionCoordinator.Result result) {
        if (result.state() == null || result.state().revision() == session.expectedRevision()) return session;
        try {
            return context.advance(session, result.state());
        } catch (StaleRevisionException stale) {
            context.invalidate(session);
            return null;
        }
    }

    private static PetManagementOutcome.Status rejectedStatus(RepositoryProgressionResult progression) {
        return progression != null && progression.status() == RepositoryProgressionResult.Status.PET_NOT_FOUND
                ? PetManagementOutcome.Status.PET_NOT_FOUND
                : PetManagementOutcome.Status.REJECTED;
    }
}
