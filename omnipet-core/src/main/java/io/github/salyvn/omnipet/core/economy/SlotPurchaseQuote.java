package io.github.salyvn.omnipet.core.economy;

import java.util.Objects;
import java.util.UUID;

public record SlotPurchaseQuote(UUID playerId, long expectedRevision, SlotUnlockRule rule) {
    public SlotPurchaseQuote {
        playerId = Objects.requireNonNull(playerId, "player id");
        if (expectedRevision < 0) throw new IllegalArgumentException("expected revision cannot be negative");
        rule = Objects.requireNonNull(rule, "slot unlock rule");
    }
}
