package io.github.salyvn.omnipet.core.storage;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SlotEntitlementPolicyTest {
    @Test
    void hybridPrecedenceIsDeterministic() {
        assertTrue(new SlotEntitlementPolicy(
                SlotEntitlementMode.HYBRID,
                SlotEntitlementPrecedence.UNION).effective(true, false));
        assertFalse(new SlotEntitlementPolicy(
                SlotEntitlementMode.HYBRID,
                SlotEntitlementPrecedence.REQUIRE_BOTH).effective(true, false));
        assertTrue(new SlotEntitlementPolicy(
                SlotEntitlementMode.HYBRID,
                SlotEntitlementPrecedence.LUCKPERMS_AUTHORITATIVE).effective(false, true));
    }
}
