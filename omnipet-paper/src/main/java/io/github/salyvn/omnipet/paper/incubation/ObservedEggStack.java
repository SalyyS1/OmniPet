package io.github.salyvn.omnipet.paper.incubation;

import java.util.Objects;
import java.util.UUID;

public record ObservedEggStack(
        int slot,
        String materialKey,
        String eggId,
        UUID nonce,
        String fingerprint,
        int amount,
        int maxStackAmount,
        boolean identityValid) {
    private static final int MAX_EFFECTIVE_STACK_AMOUNT = 99;

    public ObservedEggStack(
            int slot,
            String materialKey,
            String eggId,
            UUID nonce,
            String fingerprint,
            int amount,
            int maxStackAmount) {
        this(slot, materialKey, eggId, nonce, fingerprint, amount, maxStackAmount, true);
    }

    public ObservedEggStack {
        if (slot < 0 || slot > 255) throw new IllegalArgumentException("egg stack slot is outside 0..255");
        materialKey = Objects.requireNonNull(materialKey, "material key");
        eggId = Objects.requireNonNull(eggId, "egg id");
        if (identityValid) nonce = Objects.requireNonNull(nonce, "item nonce");
        fingerprint = Objects.requireNonNull(fingerprint, "item fingerprint");
        if (maxStackAmount < 1 || maxStackAmount > MAX_EFFECTIVE_STACK_AMOUNT) {
            throw new IllegalArgumentException("egg max stack amount is outside 1..99");
        }
        if (amount < 1 || amount > maxStackAmount) {
            throw new IllegalArgumentException("egg stack amount is outside its material limit");
        }
    }

    public ObservedEggStack withAmount(int nextAmount) {
        return new ObservedEggStack(
                slot, materialKey, eggId, nonce, fingerprint, nextAmount, maxStackAmount, identityValid);
    }
}
