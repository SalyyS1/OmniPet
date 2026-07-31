package io.github.salyvn.omnipet.paper.entitlement;

public record SlotEntitlementSyncResult(Status status, String detail) {
    public SlotEntitlementSyncResult {
        if (status == null) throw new IllegalArgumentException("entitlement sync status is required");
        detail = detail == null ? "" : detail;
        if (detail.length() > 512) throw new IllegalArgumentException("entitlement sync detail is too long");
    }

    public boolean succeeded() {
        return status == Status.SUCCESS || status == Status.ALREADY_APPLIED;
    }

    public enum Status {
        SUCCESS,
        ALREADY_APPLIED,
        UNAVAILABLE,
        UNKNOWN
    }
}
