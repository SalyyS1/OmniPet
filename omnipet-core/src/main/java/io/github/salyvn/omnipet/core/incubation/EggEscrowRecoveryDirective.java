package io.github.salyvn.omnipet.core.incubation;

public enum EggEscrowRecoveryDirective {
    NONE,
    REMOVE_MATCHING_ITEM,
    CANCEL_TRANSACTION,
    CANCEL_INCUBATION,
    COMMIT_TRANSACTION,
    REFUND_ITEM,
    OPERATOR_REVIEW
}
