package io.github.salyvn.omnipet.paper.release;

public record ReleaseInventoryClaimResult(Status status, String detail) {
    public ReleaseInventoryClaimResult {
        if (status == null) throw new IllegalArgumentException("release inventory claim status is required");
        detail = detail == null ? "" : detail;
    }

    public enum Status {
        DELIVERED,
        ALREADY_DELIVERED,
        CAPACITY_FULL,
        PLAYER_OFFLINE,
        AMBIGUOUS
    }
}
