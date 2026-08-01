package io.github.salyvn.omnipet.paper.incubation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.github.salyvn.omnipet.core.incubation.EggEscrowItemObservation;
import io.github.salyvn.omnipet.core.incubation.EggInventoryHand;
import io.github.salyvn.omnipet.core.incubation.EggItemIdentity;

class EggInventoryEscrowServiceTest {
    private static final UUID NONCE = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final byte[] PAYLOAD = "durable-item".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    private static final String FINGERPRINT = PaperEggItemSnapshot.fingerprint(PAYLOAD);
    private final EggInventoryEscrowService service = new EggInventoryEscrowService();

    @Test
    void observesMovedStacksAndDistinguishesOneRemovedItem() {
        EggItemIdentity identity = identity(5);

        assertEquals(EggEscrowItemObservation.MATCHING_ITEM_PRESENT,
                service.observe(identity, port(stack(19, 5))));
        assertEquals(EggEscrowItemObservation.MATCHING_ITEM_ABSENT,
                service.observe(identity, port(stack(19, 4))));
        assertEquals(EggEscrowItemObservation.AMBIGUOUS,
                service.observe(identity, port()));
    }

    @Test
    void expectedSingleItemCanBeProvenAbsentWithoutAMatchingStack() {
        assertEquals(EggEscrowItemObservation.MATCHING_ITEM_ABSENT,
                service.observe(identity(1), port()));
    }

    @Test
    void duplicateNonceTamperAndUnexpectedAmountsFailClosed() {
        assertEquals(EggEscrowItemObservation.AMBIGUOUS,
                service.observe(identity(5), port(stack(1, 3), stack(2, 2))));
        assertEquals(EggEscrowItemObservation.AMBIGUOUS,
                service.observe(identity(5), port(new ObservedEggStack(
                        1, "minecraft:stone", "tier_d_egg", NONCE, FINGERPRINT, 5, 64))));
        assertEquals(EggEscrowItemObservation.AMBIGUOUS,
                service.observe(identity(5), port(stack(1, 2))));
    }

    @Test
    void removesExactlyOneOnlyFromTheRecordedSlotAndAmount() {
        FakePort success = port(stack(4, 5));
        FakePort moved = port(stack(8, 5));

        assertEquals(EggInventoryMutationResult.REMOVED, service.removeOne(identity(5), success));
        assertEquals(4, success.stacks().getFirst().amount());
        assertEquals(EggInventoryMutationResult.NOT_MATCHING, service.removeOne(identity(5), moved));
        assertEquals(5, moved.stacks().getFirst().amount());
        success.handMatches = false;
        assertEquals(EggInventoryMutationResult.NOT_MATCHING, service.removeOne(identity(4), success));
    }

    @Test
    void refundsByMergingOrRestoringAndIsIdempotent() {
        FakePort merge = port(stack(20, 4));
        FakePort restore = port();
        FakePort alreadyPresent = port(stack(9, 1));

        assertEquals(EggInventoryMutationResult.REFUNDED, service.refundOne(identity(5), merge));
        assertEquals(5, merge.stacks().getFirst().amount());
        assertEquals(EggInventoryMutationResult.REFUNDED, service.refundOne(identity(1), restore));
        assertEquals(1, restore.stacks().getFirst().amount());
        assertEquals(EggInventoryMutationResult.ALREADY_PRESENT,
                service.refundOne(identity(1), alreadyPresent));
    }

    @Test
    void fullInventoryAndDuplicateNonceDoNotRefund() {
        FakePort full = port();
        full.restoreAllowed = false;

        assertEquals(EggInventoryMutationResult.FAILED, service.refundOne(identity(1), full));
        assertEquals(EggInventoryMutationResult.AMBIGUOUS,
                service.refundOne(identity(5), port(stack(1, 2), stack(2, 2))));
    }

    @Test
    void adapterMutationFailuresRemainFailedAndDoNotChangeInventory() {
        FakePort removalFailure = port(stack(4, 5));
        removalFailure.removeAllowed = false;
        FakePort mergeFailure = port(stack(20, 4));
        mergeFailure.addAllowed = false;

        assertEquals(EggInventoryMutationResult.FAILED,
                service.removeOne(identity(5), removalFailure));
        assertEquals(5, removalFailure.stacks().getFirst().amount());
        assertEquals(EggInventoryMutationResult.FAILED,
                service.refundOne(identity(5), mergeFailure));
        assertEquals(4, mergeFailure.stacks().getFirst().amount());
    }

    @Test
    void fingerprintMismatchFailsClosedForObservationRemovalAndRefund() {
        ObservedEggStack tampered = new ObservedEggStack(
                4, "minecraft:player_head", "tier_d_egg", NONCE, "b".repeat(64), 5, 64);

        assertEquals(EggEscrowItemObservation.AMBIGUOUS,
                service.observe(identity(5), port(tampered)));
        assertEquals(EggInventoryMutationResult.AMBIGUOUS,
                service.removeOne(identity(5), port(tampered)));
        assertEquals(EggInventoryMutationResult.AMBIGUOUS,
                service.refundOne(identity(5), port(tampered)));
    }

    @Test
    void malformedEggIdentityBlocksSingleItemRefund() {
        ObservedEggStack malformed = new ObservedEggStack(
                4, "minecraft:stone", "tier_d_egg", null, "invalid", 1, 64, false);

        assertEquals(EggEscrowItemObservation.AMBIGUOUS,
                service.observe(identity(1), port(malformed)));
        assertEquals(EggInventoryMutationResult.AMBIGUOUS,
                service.refundOne(identity(1), port(malformed)));
    }

    @Test
    void exactActionNonceWinsOverAnUncapturedEggInAnotherStack() {
        ObservedEggStack uncaptured = new ObservedEggStack(
                9, "minecraft:player_head", "tier_d_egg", null, "invalid", 1, 64, false);
        FakePort inventory = port(stack(4, 5), uncaptured);

        assertEquals(EggInventoryMutationResult.REMOVED,
                service.removeOne(identity(5), inventory));
        assertEquals(4, inventory.stacks().getFirst().amount());
    }

    private static EggItemIdentity identity(int expectedAmount) {
        return new EggItemIdentity(
                4,
                EggInventoryHand.MAIN_HAND,
                "minecraft:player_head",
                NONCE,
                FINGERPRINT,
                expectedAmount,
                PaperEggItemSnapshot.extensions(PAYLOAD));
    }

    private static ObservedEggStack stack(int slot, int amount) {
        return new ObservedEggStack(
                slot, "minecraft:player_head", "tier_d_egg", NONCE, FINGERPRINT, amount, 64);
    }

    private static FakePort port(ObservedEggStack... stacks) {
        return new FakePort(new ArrayList<>(List.of(stacks)));
    }

    private static final class FakePort implements EggInventoryPort {
        private final List<ObservedEggStack> stacks;
        private boolean restoreAllowed = true;
        private boolean handMatches = true;
        private boolean removeAllowed = true;
        private boolean addAllowed = true;

        private FakePort(List<ObservedEggStack> stacks) { this.stacks = stacks; }

        @Override
        public List<ObservedEggStack> stacks() { return List.copyOf(stacks); }

        @Override
        public boolean handMatches(EggItemIdentity identity) { return handMatches; }

        @Override
        public boolean removeOne(ObservedEggStack expected) {
            if (!removeAllowed) return false;
            int index = stacks.indexOf(expected);
            if (index < 0) return false;
            if (expected.amount() == 1) stacks.remove(index);
            else stacks.set(index, expected.withAmount(expected.amount() - 1));
            return true;
        }

        @Override
        public boolean addOne(ObservedEggStack expected) {
            if (!addAllowed) return false;
            int index = stacks.indexOf(expected);
            if (index < 0 || expected.amount() >= expected.maxStackAmount()) return false;
            stacks.set(index, expected.withAmount(expected.amount() + 1));
            return true;
        }

        @Override
        public boolean restoreOne(EggItemIdentity identity) {
            if (!restoreAllowed) return false;
            stacks.add(new ObservedEggStack(
                    30, identity.materialKey(), "tier_d_egg", identity.itemNonce(),
                    identity.fingerprint(), 1, 64));
            return true;
        }
    }
}
