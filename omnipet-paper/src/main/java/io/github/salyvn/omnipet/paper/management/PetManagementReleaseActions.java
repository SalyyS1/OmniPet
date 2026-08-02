package io.github.salyvn.omnipet.paper.management;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import io.github.salyvn.omnipet.core.domain.PlayerState;
import io.github.salyvn.omnipet.core.persistence.StaleRevisionException;
import io.github.salyvn.omnipet.core.release.InternalOutboxResult;
import io.github.salyvn.omnipet.core.release.ReleasePreview;
import io.github.salyvn.omnipet.core.release.ReleasePreviewResult;
import io.github.salyvn.omnipet.core.release.ReleaseResult;

final class PetManagementReleaseActions {
    private final PetManagementControllerContext context;
    private final PetReleaseOutboxRecoveryPort outboxRecovery;

    PetManagementReleaseActions(
            PetManagementControllerContext context,
            PetReleaseOutboxRecoveryPort outboxRecovery) {
        this.context = java.util.Objects.requireNonNull(context, "management controller context");
        this.outboxRecovery = java.util.Objects.requireNonNull(outboxRecovery, "release outbox recovery");
    }

    CompletionStage<PetManagementOutcome> preview(PetManagementSession session) {
        PetManagementOutcome rejected = context.begin(session, PetManagementOperation.PREVIEW_RELEASE);
        if (rejected != null) return CompletableFuture.completedFuture(rejected);

        UUID transactionId;
        try {
            transactionId = PetManagementControllerContext.requireId(context.ids.get());
        } catch (RuntimeException failure) {
            context.inFlight.remove(session.viewerId());
            return CompletableFuture.completedFuture(PetManagementControllerContext.error(failure));
        }
        return context.submit(() -> {
            try {
                ReleasePreviewResult result = context.repository.previewRelease(
                        transactionId, session.ownerId(), session.petId());
                if (result.status() != ReleasePreviewResult.Status.READY || result.preview() == null) {
                    PetManagementOutcome.Status status = result.status() == ReleasePreviewResult.Status.PET_NOT_FOUND
                            ? PetManagementOutcome.Status.PET_NOT_FOUND
                            : PetManagementOutcome.Status.REJECTED;
                    return PetManagementControllerContext.outcome(
                            status, session, null, null, transactionId, null, result.detail());
                }

                ReleasePreview preview = result.preview();
                if (!preview.transactionId().equals(transactionId)
                        || !preview.playerId().equals(session.ownerId())
                        || !preview.petId().equals(session.petId())) {
                    return PetManagementControllerContext.outcome(
                            PetManagementOutcome.Status.REJECTED, session, null, null, transactionId, null,
                            "release preview identity does not match the management session");
                }

                PlayerState current = context.repository.snapshot(session.ownerId());
                if (current.revision() != session.expectedRevision()
                        || preview.expectedRevision() != session.expectedRevision()) {
                    context.invalidate(session);
                    return PetManagementControllerContext.outcome(
                            PetManagementOutcome.Status.STALE_SESSION, null, null, null, transactionId, null,
                            "player state changed while release preview was prepared");
                }
                return context.viewOutcome(PetManagementOutcome.Status.RELEASE_PREVIEW_READY,
                        session, current, preview, result.detail());
            } catch (StaleRevisionException stale) {
                context.invalidate(session);
                throw stale;
            }
        }).whenComplete((ignored, failure) -> context.inFlight.remove(session.viewerId()));
    }

    CompletionStage<PetManagementOutcome> confirm(PetManagementSession session, ReleasePreview preview) {
        if (session == null) {
            return PetManagementControllerContext.completed(
                    PetManagementOutcome.Status.STALE_SESSION, "management session is stale");
        }
        if (preview == null
                || !preview.playerId().equals(session.ownerId())
                || !preview.petId().equals(session.petId())
                || preview.expectedRevision() != session.expectedRevision()) {
            return PetManagementControllerContext.completed(
                    PetManagementOutcome.Status.REJECTED,
                    "release preview does not match the management session");
        }

        PetManagementOutcome rejected = context.begin(session, PetManagementOperation.CONFIRM_RELEASE);
        if (rejected != null) return CompletableFuture.completedFuture(rejected);
        return context.submit(() -> {
            try {
                ReleaseResult result = context.repository.release(preview);
                if (result.committed()) {
                    context.invalidate(session);
                    return PetManagementControllerContext.outcome(
                            PetManagementOutcome.Status.RELEASE_COMMITTED_OUTBOX_PENDING,
                            null, null, preview, preview.transactionId(), null, result.detail());
                }
                if (result.status() == ReleaseResult.Status.STALE_REVISION) {
                    context.invalidate(session);
                    return PetManagementControllerContext.outcome(
                            PetManagementOutcome.Status.STALE_SESSION,
                            null, null, preview, preview.transactionId(), null, result.detail());
                }
                PetManagementOutcome.Status status = result.status() == ReleaseResult.Status.PET_NOT_FOUND
                        ? PetManagementOutcome.Status.PET_NOT_FOUND
                        : PetManagementOutcome.Status.REJECTED;
                return PetManagementControllerContext.outcome(
                        status, session, context.safeView(session, result.state(), preview),
                        preview, preview.transactionId(), null, result.detail());
            } catch (StaleRevisionException stale) {
                context.invalidate(session);
                throw stale;
            }
        }).whenComplete((ignored, failure) -> context.inFlight.remove(session.viewerId()));
    }

    CompletionStage<PetManagementOutcome> recover(
            UUID viewerId,
            UUID ownerId,
            UUID petId,
            UUID transactionId) {
        PetManagementAuthorizationPort.Decision decision;
        try {
            decision = context.authorize(viewerId, ownerId, petId, PetManagementOperation.RECOVER_RELEASE);
        } catch (RuntimeException failure) {
            return CompletableFuture.completedFuture(PetManagementControllerContext.error(failure));
        }
        if (!decision.allowed()) {
            return PetManagementControllerContext.completed(
                    PetManagementOutcome.Status.UNAUTHORIZED, decision.detail());
        }
        if (transactionId == null) {
            return PetManagementControllerContext.completed(
                    PetManagementOutcome.Status.ERROR, "release transaction UUID is required");
        }

        CompletionStage<InternalOutboxResult> recovery;
        try {
            recovery = outboxRecovery.recover(ownerId, transactionId);
        } catch (RuntimeException failure) {
            return CompletableFuture.completedFuture(PetManagementControllerContext.error(failure));
        }
        if (recovery == null) {
            return PetManagementControllerContext.completed(
                    PetManagementOutcome.Status.ERROR, "release outbox recovery returned no stage");
        }
        return recovery.handle((result, failure) -> {
            if (failure != null) return PetManagementControllerContext.error(failure);
            if (result == null) {
                return PetManagementControllerContext.outcome(
                        PetManagementOutcome.Status.ERROR, null, null, null, transactionId, null,
                        "release outbox recovery returned no result");
            }
            PetManagementOutcome.Status status = result.status() == InternalOutboxResult.Status.ACKNOWLEDGED
                    ? PetManagementOutcome.Status.OUTBOX_RECOVERED
                    : PetManagementOutcome.Status.RELEASE_COMMITTED_OUTBOX_PENDING;
            return PetManagementControllerContext.outcome(
                    status,
                    null, null, null, transactionId, result, result.detail());
        });
    }
}
