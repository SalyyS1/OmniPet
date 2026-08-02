package io.github.salyvn.omnipet.paper.management;

import java.util.UUID;

import io.github.salyvn.omnipet.core.release.InternalOutboxResult;
import io.github.salyvn.omnipet.core.release.ReleasePreview;

public record PetManagementOutcome(
        Status status,
        PetManagementSession session,
        PetManagementViewModel view,
        ReleasePreview releasePreview,
        UUID transactionId,
        InternalOutboxResult outbox,
        String detail) {
    public PetManagementOutcome {
        if (status == null) throw new IllegalArgumentException("management outcome status is required");
        detail = detail == null ? "" : detail;
    }

    public enum Status {
        VIEW_READY,
        PERSISTED,
        REJECTED,
        PET_NOT_FOUND,
        UNAUTHORIZED,
        STALE_SESSION,
        BUSY,
        CONSUMABLE_UNAVAILABLE,
        PERSISTED_CONSUMPTION_PENDING,
        RELEASE_PREVIEW_READY,
        RELEASE_COMMITTED_OUTBOX_PENDING,
        OUTBOX_RECOVERED,
        ERROR
    }
}
