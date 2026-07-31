package io.github.salyvn.omnipet.core.economy;

public enum SlotPurchaseSagaState {
    PREPARED,
    EXTERNAL_PENDING,
    ENTITLEMENT_PERSISTED,
    ENTITLEMENT_SYNC_PENDING,
    REFUND_PENDING,
    COMPLETED,
    REFUNDED,
    FAILED,
    UNKNOWN_REQUIRES_RECONCILIATION
}
