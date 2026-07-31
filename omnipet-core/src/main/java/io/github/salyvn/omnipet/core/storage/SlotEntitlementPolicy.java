package io.github.salyvn.omnipet.core.storage;

import java.util.Objects;

/** Makes hybrid ownership precedence explicit instead of silently trusting whichever provider answered first. */
public record SlotEntitlementPolicy(
        SlotEntitlementMode mode,
        SlotEntitlementPrecedence precedence) {
    public SlotEntitlementPolicy {
        mode = Objects.requireNonNull(mode, "slot entitlement mode");
        precedence = Objects.requireNonNull(precedence, "slot entitlement precedence");
        if (mode != SlotEntitlementMode.HYBRID
                && precedence != expectedPrecedence(mode)) {
            throw new IllegalArgumentException("non-hybrid entitlement mode has a fixed precedence");
        }
    }

    public static SlotEntitlementPolicy omniPet() {
        return new SlotEntitlementPolicy(
                SlotEntitlementMode.OMNIPET,
                SlotEntitlementPrecedence.OMNIPET_AUTHORITATIVE);
    }

    public boolean effective(boolean omniPetEntitled, boolean luckPermsEntitled) {
        return switch (precedence) {
            case OMNIPET_AUTHORITATIVE -> omniPetEntitled;
            case LUCKPERMS_AUTHORITATIVE -> luckPermsEntitled;
            case REQUIRE_BOTH -> omniPetEntitled && luckPermsEntitled;
            case UNION -> omniPetEntitled || luckPermsEntitled;
        };
    }

    private static SlotEntitlementPrecedence expectedPrecedence(SlotEntitlementMode mode) {
        return switch (mode) {
            case OMNIPET -> SlotEntitlementPrecedence.OMNIPET_AUTHORITATIVE;
            case LUCKPERMS -> SlotEntitlementPrecedence.LUCKPERMS_AUTHORITATIVE;
            case HYBRID -> throw new IllegalArgumentException("hybrid mode requires explicit precedence");
        };
    }
}
