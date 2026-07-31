package io.github.salyvn.omnipet.core.economy;

/** Explicit operator evidence for an external call whose outcome was ambiguous. */
public enum SlotReconciliationDecision {
    CHARGE_CONFIRMED,
    NO_CHARGE_CONFIRMED,
    REFUND_CONFIRMED,
    ENTITLEMENT_SYNC_RETRY
}
