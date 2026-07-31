package io.github.salyvn.omnipet.core.economy;

import java.util.Objects;
import java.util.UUID;

public record SlotPurchaseTransaction(
        UUID transactionId,
        UUID playerId,
        long expectedRevision,
        int slot,
        EconomyAmount amount,
        SlotPurchaseSagaState state,
        EconomyOperationResult withdrawal,
        EconomyOperationResult refund,
        String detail) {
    public SlotPurchaseTransaction {
        transactionId = Objects.requireNonNull(transactionId, "transaction id");
        playerId = Objects.requireNonNull(playerId, "player id");
        if (expectedRevision < 0) throw new IllegalArgumentException("expected revision cannot be negative");
        if (slot < 2 || slot > 64) throw new IllegalArgumentException("slot is outside the supported range");
        amount = Objects.requireNonNull(amount, "economy amount");
        state = Objects.requireNonNull(state, "purchase state");
        detail = detail == null ? "" : detail;
        if (detail.length() > 512) throw new IllegalArgumentException("purchase detail is too long");
    }

    public static SlotPurchaseTransaction prepared(UUID transactionId, SlotPurchaseQuote quote) {
        return new SlotPurchaseTransaction(
                transactionId,
                quote.playerId(),
                quote.expectedRevision(),
                quote.rule().slot(),
                quote.rule().amount(),
                SlotPurchaseSagaState.PREPARED,
                null,
                null,
                "");
    }

    public boolean matches(SlotPurchaseQuote quote) {
        return playerId.equals(quote.playerId())
                && expectedRevision == quote.expectedRevision()
                && slot == quote.rule().slot()
                && amount.equals(quote.rule().amount());
    }

    public SlotPurchaseTransaction withState(
            SlotPurchaseSagaState nextState,
            EconomyOperationResult nextWithdrawal,
            EconomyOperationResult nextRefund,
            String nextDetail) {
        return new SlotPurchaseTransaction(
                transactionId,
                playerId,
                expectedRevision,
                slot,
                amount,
                nextState,
                nextWithdrawal,
                nextRefund,
                nextDetail);
    }
}
