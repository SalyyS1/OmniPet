package io.github.salyvn.omnipet.core.release;

import io.github.salyvn.omnipet.core.domain.PlayerState;

public record ReleaseResult(Status status, PlayerState state, ReleaseOutboxEntry outboxEntry, String detail) {
    public ReleaseResult {
        if (status == null) throw new IllegalArgumentException("release result status is required");
        detail = detail == null ? "" : detail;
    }

    public boolean committed() {
        return status == Status.COMMITTED || status == Status.ALREADY_COMMITTED;
    }

    public enum Status {
        COMMITTED,
        ALREADY_COMMITTED,
        INVALID_CONFIRMATION,
        STALE_REVISION,
        PET_NOT_FOUND,
        LOCKED,
        TRANSACTION_CONFLICT,
        OUTBOX_FULL,
        OUTBOX_INVALID
    }
}
