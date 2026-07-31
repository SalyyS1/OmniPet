package io.github.salyvn.omnipet.core.incubation;

import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import io.github.salyvn.omnipet.core.domain.RawNodeValues;

public record EggItemIdentity(
        int inventorySlot,
        EggInventoryHand hand,
        String materialKey,
        UUID itemNonce,
        String fingerprint,
        int expectedStackAmount,
        Map<String, Object> extensions) {
    private static final Pattern SHA256 = Pattern.compile("[0-9a-fA-F]{64}");

    public EggItemIdentity {
        if (inventorySlot < 0 || inventorySlot > 255) {
            throw new IllegalArgumentException("egg inventory slot is outside the supported range");
        }
        if (hand == null) throw new IllegalArgumentException("egg inventory hand is required");
        materialKey = requireReference(materialKey, "egg material key");
        if (itemNonce == null) throw new IllegalArgumentException("egg item nonce is required");
        if (fingerprint == null || !SHA256.matcher(fingerprint).matches()) {
            throw new IllegalArgumentException("egg item fingerprint must be a SHA-256 hex value");
        }
        fingerprint = fingerprint.toLowerCase(java.util.Locale.ROOT);
        if (expectedStackAmount < 1 || expectedStackAmount > 64) {
            throw new IllegalArgumentException("expected egg stack amount is outside 1..64");
        }
        extensions = RawNodeValues.immutableMap(extensions == null ? Map.of() : extensions);
        RawNodeValues.rejectNonFinite(extensions, "eggItemIdentity.extensions");
    }

    private static String requireReference(String value, String field) {
        if (value == null || value.isBlank() || value.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException(field + " must be non-blank and whitespace-free");
        }
        return value;
    }
}
