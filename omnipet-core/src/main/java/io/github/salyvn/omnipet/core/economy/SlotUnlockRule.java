package io.github.salyvn.omnipet.core.economy;

import java.util.Objects;

public record SlotUnlockRule(int slot, EconomyAmount amount) {
    public SlotUnlockRule {
        if (slot < 2 || slot > 64) throw new IllegalArgumentException("slot is outside the supported range");
        amount = Objects.requireNonNull(amount, "slot unlock amount");
    }
}
