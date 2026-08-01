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

    public EggInventoryMutationResult removeOne(EggItemIdentity identity, EggInventoryPort inventory) {
        Match match = match(identity, inventory);
        if (match.ambiguous()) return EggInventoryMutationResult.AMBIGUOUS;
        ObservedEggStack stack = match.stack();
        if (stack == null
                || !inventory.handMatches(identity)
                || stack.slot() != identity.inventorySlot()
                || stack.amount() != identity.expectedStackAmount()) {
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
