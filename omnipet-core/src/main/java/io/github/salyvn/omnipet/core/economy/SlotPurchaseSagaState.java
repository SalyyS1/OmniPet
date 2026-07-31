package io.github.salyvn.omnipet.core.economy;

public enum SlotPurchaseSagaState {
    PREPARED,
    EXTERNAL_PENDING,
    ENTITLEMENT_PERSISTED,
    REFUND_PENDING,
    COMPLETED,
    REFUNDED,
    FAILED,
    UNKNOWN_REQUIRES_RECONCILIATION
}
