package io.github.salyvn.omnipet.paper.incubation;

import java.util.List;
import java.util.Objects;

import io.github.salyvn.omnipet.core.incubation.EggEscrowItemObservation;
import io.github.salyvn.omnipet.core.incubation.EggItemIdentity;

public final class EggInventoryEscrowService {
    public EggEscrowItemObservation observe(EggItemIdentity identity, EggInventoryPort inventory) {
        Match match = match(identity, inventory);
        if (match.ambiguous()) return EggEscrowItemObservation.AMBIGUOUS;
        if (match.stack() == null) {
            return identity.expectedStackAmount() == 1
                    ? EggEscrowItemObservation.MATCHING_ITEM_ABSENT
                    : EggEscrowItemObservation.AMBIGUOUS;
        }
        int amount = match.stack().amount();
        if (amount == identity.expectedStackAmount()) return EggEscrowItemObservation.MATCHING_ITEM_PRESENT;
        if (identity.expectedStackAmount() > 1 && amount == identity.expectedStackAmount() - 1) {
            return EggEscrowItemObservation.MATCHING_ITEM_ABSENT;
        }
        return EggEscrowItemObservation.AMBIGUOUS;
    }

    /**
     * Removes the paid egg, locating it by nonce rather than by where it was when captured.
     *
     * <p>Deliberately does not require the egg to still be in the original slot or hand. Capture and
     * removal are separated by an async round trip, so scrolling the hotbar or moving the stack in that
     * window used to make removal impossible: the egg stayed in the inventory while the incubation
     * persisted, freezing the countdown and locking claim forever, and recovery could not heal it
     * because {@link #observe} finds the egg by nonce and so kept reporting it present.
     *
     * <p>Safe because the nonce is unique per egg and {@link #match} refuses to act on a duplicate, and
     * because {@code inventory.removeOne} re-observes the located slot and removes only on an exact
     * match. The amount check stays: an unexpected stack size is genuine ambiguity about which egg was
     * paid for.
     */
    public EggInventoryMutationResult removeOne(EggItemIdentity identity, EggInventoryPort inventory) {
        Match match = match(identity, inventory);
        if (match.ambiguous()) return EggInventoryMutationResult.AMBIGUOUS;
        ObservedEggStack stack = match.stack();
        if (stack == null || stack.amount() != identity.expectedStackAmount()) {
            return EggInventoryMutationResult.NOT_MATCHING;
        }
        return inventory.removeOne(stack)
                ? EggInventoryMutationResult.REMOVED
                : EggInventoryMutationResult.FAILED;
    }

    public EggInventoryMutationResult refundOne(EggItemIdentity identity, EggInventoryPort inventory) {
        EggEscrowItemObservation observation = observe(identity, inventory);
        if (observation == EggEscrowItemObservation.MATCHING_ITEM_PRESENT) {
            return EggInventoryMutationResult.ALREADY_PRESENT;
        }
        if (observation == EggEscrowItemObservation.AMBIGUOUS) {
            return EggInventoryMutationResult.AMBIGUOUS;
        }
        Match match = match(identity, inventory);
        boolean refunded = match.stack() == null
                ? inventory.restoreOne(identity)
                : inventory.addOne(match.stack());
        return refunded ? EggInventoryMutationResult.REFUNDED : EggInventoryMutationResult.FAILED;
    }

    private static Match match(EggItemIdentity identity, EggInventoryPort inventory) {
        Objects.requireNonNull(identity, "egg item identity");
        Objects.requireNonNull(inventory, "egg inventory");
        List<ObservedEggStack> stacks = inventory.stacks();
        List<ObservedEggStack> sameNonce = stacks.stream()
                .filter(stack -> identity.itemNonce().equals(stack.nonce()))
                .toList();
        if (sameNonce.size() > 1) return new Match(null, true);
        if (sameNonce.isEmpty()) {
            // Without an exact action nonce, any malformed egg prevents proving the paid item is absent.
            boolean malformedIdentity = stacks.stream()
                    .anyMatch(stack -> !stack.identityValid());
            return new Match(null, malformedIdentity);
        }
        ObservedEggStack stack = sameNonce.getFirst();
        boolean identityMismatch = !identity.materialKey().equals(stack.materialKey())
                || !identity.fingerprint().equals(stack.fingerprint());
        return new Match(identityMismatch ? null : stack, identityMismatch);
    }

    private record Match(ObservedEggStack stack, boolean ambiguous) {}
}
