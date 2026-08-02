package io.github.salyvn.omnipet.core.release;

public record ReleasePreviewResult(Status status, ReleasePreview preview, String detail) {
    public ReleasePreviewResult {
        if (status == null) throw new IllegalArgumentException("release preview status is required");
        detail = detail == null ? "" : detail;
    }

    public enum Status {
        READY,
        PET_NOT_FOUND,
        LOCKED,
        TRANSACTION_CONFLICT,
        INVALID_REWARDS
    }
}
