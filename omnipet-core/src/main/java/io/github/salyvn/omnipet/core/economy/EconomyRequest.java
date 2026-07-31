package io.github.salyvn.omnipet.core.economy;

import java.util.Objects;
import java.util.UUID;

public record EconomyRequest(UUID transactionId, UUID playerId, int slot, EconomyAmount amount) {
    public EconomyRequest {
        transactionId = Objects.requireNonNull(transactionId, "transaction id");
        playerId = Objects.requireNonNull(playerId, "player id");
        if (slot < 2 || slot > 64) throw new IllegalArgumentException("slot is outside the supported range");
        amount = Objects.requireNonNull(amount, "economy amount");
    }
}
