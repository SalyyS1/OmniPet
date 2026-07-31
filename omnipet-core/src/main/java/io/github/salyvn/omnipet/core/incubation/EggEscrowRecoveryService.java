package io.github.salyvn.omnipet.core.incubation;

import java.util.Objects;

import io.github.salyvn.omnipet.core.domain.PlayerState;
import io.github.salyvn.omnipet.core.domain.incubation.IncubationState;
import io.github.salyvn.omnipet.core.domain.incubation.IncubationStatus;

public final class EggEscrowRecoveryService {
    public EggEscrowRecovery inspect(
            EggEscrowTransaction transaction,
            PlayerState player,
            EggEscrowItemObservation itemObservation) {
        Objects.requireNonNull(transaction, "egg escrow transaction");
        Objects.requireNonNull(player, "player state");
        Objects.requireNonNull(itemObservation, "egg escrow item observation");
        if (!transaction.playerId().equals(player.playerId())) {
            throw new IllegalArgumentException("egg escrow player does not match player state");
        }
        IncubationState incubation = player.incubation();
        boolean matches = incubation != null && incubation.id().equals(transaction.incubationId());
        IncubationStatus status = matches ? incubation.status() : null;
        boolean viable = status == IncubationStatus.INCUBATING || status == IncubationStatus.READY;
        boolean claimed = status == IncubationStatus.CLAIMED;
        if (requiresAbsentItem(transaction.stage())
                && itemObservation != EggEscrowItemObservation.MATCHING_ITEM_ABSENT) {
            return recovery(EggEscrowRecoveryDirective.OPERATOR_REVIEW, transaction,
                    "durable escrow stage cannot prove the matching item was removed");
        }
        return switch (transaction.stage()) {
            case PREPARED -> prepared(transaction, itemObservation, viable, claimed);
            case ITEM_REMOVED -> claimed
                    ? recovery(EggEscrowRecoveryDirective.OPERATOR_REVIEW, transaction,
                            "claimed incubation conflicts with an uncommitted removed item")
                    : viable
                            ? recovery(EggEscrowRecoveryDirective.COMMIT_TRANSACTION, transaction,
                                    "item removal and incubation are both durable")
                            : recovery(EggEscrowRecoveryDirective.REFUND_ITEM, transaction,
                                    "item was removed but incubation is unavailable");
            case REFUND_PENDING -> claimed
                    ? recovery(EggEscrowRecoveryDirective.OPERATOR_REVIEW, transaction,
                            "claimed incubation conflicts with a pending refund")
                    : recovery(EggEscrowRecoveryDirective.REFUND_ITEM, transaction, "refund remains pending");
            case COMMITTED -> viable || claimed
                    ? recovery(EggEscrowRecoveryDirective.NONE, transaction, "transaction is consistent")
                    : recovery(EggEscrowRecoveryDirective.OPERATOR_REVIEW, transaction,
                            "committed transaction lost its incubation");
            case CANCELLED, REFUNDED -> viable
                    ? recovery(EggEscrowRecoveryDirective.CANCEL_INCUBATION, transaction,
                            "terminal escrow requires matching incubation cancellation")
                    : claimed
                            ? recovery(EggEscrowRecoveryDirective.OPERATOR_REVIEW, transaction,
                                    "claimed incubation conflicts with terminal escrow")
                            : recovery(EggEscrowRecoveryDirective.NONE, transaction,
                                    "transaction and incubation are terminal");
            case FAILED -> recovery(EggEscrowRecoveryDirective.OPERATOR_REVIEW, transaction,
                    "failure stage does not prove whether the item was removed");
        };
    }

    private static boolean requiresAbsentItem(EggEscrowStage stage) {
        return stage == EggEscrowStage.ITEM_REMOVED
                || stage == EggEscrowStage.REFUND_PENDING
                || stage == EggEscrowStage.COMMITTED;
    }

    private static EggEscrowRecovery prepared(
            EggEscrowTransaction transaction,
            EggEscrowItemObservation observation,
            boolean viable,
            boolean claimed) {
        if (claimed) {
            return recovery(EggEscrowRecoveryDirective.OPERATOR_REVIEW, transaction,
                    "claimed incubation conflicts with prepared escrow");
        }
        if (observation != EggEscrowItemObservation.MATCHING_ITEM_PRESENT) {
            return recovery(EggEscrowRecoveryDirective.OPERATOR_REVIEW, transaction,
                    "prepared escrow cannot prove the matching item remains present");
        }
        return viable
                ? recovery(EggEscrowRecoveryDirective.REMOVE_MATCHING_ITEM, transaction,
                        "resolved incubation and matching item are both present")
                : recovery(EggEscrowRecoveryDirective.CANCEL_TRANSACTION, transaction,
                        "matching item remains but no viable incubation exists");
    }

    private static EggEscrowRecovery recovery(
            EggEscrowRecoveryDirective directive,
            EggEscrowTransaction transaction,
            String reason) {
        return new EggEscrowRecovery(directive, transaction, reason);
    }
}
