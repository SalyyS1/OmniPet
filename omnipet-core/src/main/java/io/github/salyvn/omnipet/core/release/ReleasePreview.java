package io.github.salyvn.omnipet.core.release;

import java.util.UUID;

public record ReleasePreview(
        UUID transactionId,
        UUID playerId,
        UUID petId,
        long expectedRevision,
        String petFingerprint,
        ReleaseRewardBundle rewards,
        String confirmationToken) {
    public ReleasePreview {
        if (transactionId == null) throw new IllegalArgumentException("release transaction UUID is required");
        if (playerId == null) throw new IllegalArgumentException("release player UUID is required");
        if (petId == null) throw new IllegalArgumentException("release pet UUID is required");
        if (expectedRevision < 0) throw new IllegalArgumentException("release revision cannot be negative");
        if (petFingerprint == null || petFingerprint.isBlank()) {
            throw new IllegalArgumentException("pet fingerprint is required");
        }
        if (rewards == null) throw new IllegalArgumentException("release rewards are required");
        if (confirmationToken == null || confirmationToken.isBlank()) {
            throw new IllegalArgumentException("release confirmation token is required");
        }
    }
}
