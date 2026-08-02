package io.github.salyvn.omnipet.core.release;

public record ExternalReleaseResult(Status status, ReleaseOutboxEntry entry, String detail) {
    public ExternalReleaseResult {
        if (status == null) throw new IllegalArgumentException("external release status is required");
        detail = detail == null ? "" : detail;
    }

    public enum Status {
        DELIVERED,
        FAILED,
        NOT_REQUIRED,
        UNKNOWN_REQUIRES_RECONCILIATION,
        NOT_FOUND,
        IDENTITY_MISMATCH
    }
}
