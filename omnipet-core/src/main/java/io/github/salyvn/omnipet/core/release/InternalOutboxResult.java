package io.github.salyvn.omnipet.core.release;

public record InternalOutboxResult(Status status, ReleaseOutboxEntry entry, String detail) {
    public InternalOutboxResult {
        if (status == null) throw new IllegalArgumentException("internal outbox result status is required");
        detail = detail == null ? "" : detail;
    }

    public enum Status {
        ACKNOWLEDGED,
        PENDING_CAPACITY,
        PENDING_FAILURE,
        ACK_PERSIST_FAILED,
        NOT_FOUND,
        IDENTITY_MISMATCH
    }
}
