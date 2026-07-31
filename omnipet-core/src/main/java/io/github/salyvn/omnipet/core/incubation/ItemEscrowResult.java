package io.github.salyvn.omnipet.core.incubation;

public record ItemEscrowResult(Status status, EggEscrowTransaction transaction) {
    public enum Status {
        CREATED,
        ALREADY_EXISTS,
        ITEM_REMOVED,
        COMMITTED,
        CANCELLED,
        REFUND_PENDING,
        REFUNDED,
        FAILED,
        INVALID_TRANSITION
    }

    public ItemEscrowResult {
        if (status == null) throw new IllegalArgumentException("item escrow result status is required");
        if (transaction == null) throw new IllegalArgumentException("egg escrow transaction is required");
    }

    public boolean changed(EggEscrowTransaction before) {
        return !transaction.equals(before);
    }
}
