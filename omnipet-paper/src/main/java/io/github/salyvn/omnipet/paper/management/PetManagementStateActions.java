package io.github.salyvn.omnipet.paper.management;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import io.github.salyvn.omnipet.core.domain.PlayerState;
import io.github.salyvn.omnipet.core.management.PetManagementResult;
import io.github.salyvn.omnipet.core.persistence.StaleRevisionException;

final class PetManagementStateActions {
    private final PetManagementControllerContext context;

    PetManagementStateActions(PetManagementControllerContext context) {
        this.context = context;
    }

    CompletionStage<PetManagementOutcome> open(UUID viewerId, UUID ownerId, UUID petId) {
        PetManagementAuthorizationPort.Decision decision;
        try {
            decision = context.authorize(viewerId, ownerId, petId, PetManagementOperation.VIEW);
        } catch (RuntimeException failure) {
            return CompletableFuture.completedFuture(PetManagementControllerContext.error(failure));
        }
        if (!decision.allowed()) {
            return PetManagementControllerContext.completed(PetManagementOutcome.Status.UNAUTHORIZED, decision.detail());
        }
        UUID sessionId;
        try {
            sessionId = PetManagementControllerContext.requireId(context.ids.get());
        } catch (RuntimeException failure) {
            return CompletableFuture.completedFuture(PetManagementControllerContext.error(failure));
        }
        context.openRequests.put(viewerId, sessionId);
        return context.submit(() -> {
            PlayerState state = context.repository.snapshot(ownerId);
            if (state.pets().stream().noneMatch(pet -> pet.id().equals(petId))) {
                return PetManagementControllerContext.outcome(
                        PetManagementOutcome.Status.PET_NOT_FOUND, null, null, null, null, null,
                        "pet UUID is not owned");
            }
            if (!sessionId.equals(context.openRequests.get(viewerId))) {
                return PetManagementControllerContext.outcome(
                        PetManagementOutcome.Status.STALE_SESSION, null, null, null, null, null,
                        "a newer management view replaced this request");
            }
            PetManagementSession session = new PetManagementSession(
                    viewerId, ownerId, petId, sessionId, state.revision());
            context.sessions.put(viewerId, session);
            return context.viewOutcome(PetManagementOutcome.Status.VIEW_READY, session, state, null,
                    "management view ready");
        }).whenComplete((ignored, failure) -> context.openRequests.remove(viewerId, sessionId));
    }

    CompletionStage<PetManagementOutcome> favorite(PetManagementSession session, boolean value) {
        return mutate(session, PetManagementOperation.FAVORITE,
                () -> context.repository.favorite(
                        session.ownerId(), session.expectedRevision(), session.petId(), value));
    }

    CompletionStage<PetManagementOutcome> lock(PetManagementSession session, boolean value) {
        return mutate(session, PetManagementOperation.LOCK,
                () -> context.repository.lock(
                        session.ownerId(), session.expectedRevision(), session.petId(), value));
    }

    CompletionStage<PetManagementOutcome> move(PetManagementSession session, int targetIndex) {
        return mutate(session, PetManagementOperation.MOVE,
                () -> context.repository.move(
                        session.ownerId(), session.expectedRevision(), session.petId(), targetIndex));
    }

    private CompletionStage<PetManagementOutcome> mutate(
            PetManagementSession session,
            PetManagementOperation operation,
            PetManagementControllerContext.IoSupplier<PetManagementResult> mutation) {
        PetManagementOutcome rejected = context.begin(session, operation);
        if (rejected != null) return CompletableFuture.completedFuture(rejected);
        return context.submit(() -> {
            try {
                PetManagementResult result = mutation.get();
                if (!result.succeeded()) {
                    return PetManagementControllerContext.outcome(
                            PetManagementOutcome.Status.REJECTED, session,
                            context.safeView(session, result.state(), null), null, null, null, result.detail());
                }
                PetManagementSession next = context.advance(session, result.state());
                return context.viewOutcome(PetManagementOutcome.Status.PERSISTED,
                        next, result.state(), null, result.detail());
            } catch (StaleRevisionException stale) {
                context.invalidate(session);
                throw stale;
            }
        }).whenComplete((ignored, failure) -> context.inFlight.remove(session.viewerId()));
    }
}
