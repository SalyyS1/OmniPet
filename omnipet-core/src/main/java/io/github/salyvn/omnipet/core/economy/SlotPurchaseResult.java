package io.github.salyvn.omnipet.core.economy;

public record SlotPurchaseResult(Status status, SlotPurchaseTransaction transaction, String detail) {
    public SlotPurchaseResult {
        if (status == null) throw new IllegalArgumentException("purchase result status is required");
        detail = detail == null ? "" : detail;
    }

    public boolean succeeded() {
        return status == Status.COMPLETED;
    }

    public enum Status {
        COMPLETED,
        IN_PROGRESS,
        STALE_QUOTE,
        INVALID_TRANSACTION,
        NOT_NEXT_SLOT,
        ALREADY_ENTITLED,
        PROVIDER_UNAVAILABLE,
        WITHDRAWAL_FAILED,
        PERSISTENCE_FAILED_REFUNDED,
        REFUND_FAILED_REQUIRES_RECOVERY,
        UNKNOWN_REQUIRES_RECONCILIATION,
        TRANSACTION_NOT_FOUND,
        FAILED
    }
}
