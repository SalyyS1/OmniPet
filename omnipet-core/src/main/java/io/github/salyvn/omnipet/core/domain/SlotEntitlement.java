package io.github.salyvn.omnipet.core.domain;

import java.util.Map;

public record SlotEntitlement(
        int slot,
        String source,
        String referenceId,
        Map<String, Object> extensions) {
    public static final int MAX_SLOT = 64;

    public SlotEntitlement {
        if (slot < 2 || slot > MAX_SLOT) {
            throw new IllegalArgumentException("slot entitlement is outside the supported range");
        }
        source = requireText(source, "slot entitlement source", 64);
        referenceId = requireText(referenceId, "slot entitlement reference id", 128);
        extensions = RawNodeValues.immutableMap(extensions == null ? Map.of() : extensions);
        RawNodeValues.rejectNonFinite(extensions, "slotEntitlements[" + slot + "]");
    }

    private static String requireText(String value, String label, int maximumLength) {
        if (value == null || value.isBlank() || value.length() > maximumLength) {
            throw new IllegalArgumentException(label + " must be non-blank and at most " + maximumLength + " characters");
        }
        return value;
    }
}
