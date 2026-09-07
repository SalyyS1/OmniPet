package io.github.salyvn.omnipet.core.release;

import java.util.Map;
import java.util.UUID;

import io.github.salyvn.omnipet.core.domain.RawNodeValues;

public record ReleaseOutboxEntry(
        UUID transactionId,
        UUID playerId,
        UUID petId,
        String petFingerprint,
        String previewToken,
        ReleaseRewardBundle rewards,
        InternalState internalState,
        ExternalState externalState,
        String externalEvidence,
        Map<String, Object> extensions) {
    public ReleaseOutboxEntry {
        if (transactionId == null || playerId == null || petId == null) {
            throw new IllegalArgumentException("release outbox identities are required");
        }
        if (petFingerprint == null || petFingerprint.isBlank() || previewToken == null || previewToken.isBlank()) {
            throw new IllegalArgumentException("release outbox identity proof is required");
        }
        if (rewards == null || internalState == null || externalState == null) {
            throw new IllegalArgumentException("release outbox state is incomplete");
        }
        externalEvidence = externalEvidence == null ? "" : externalEvidence;
        if (externalEvidence.length() > 512) throw new IllegalArgumentException("external evidence is too long");
        extensions = RawNodeValues.immutableMap(extensions == null ? Map.of() : extensions);
    }

    public ReleaseOutboxEntry withInternalState(InternalState state) {
        return new ReleaseOutboxEntry(transactionId, playerId, petId, petFingerprint, previewToken,
                rewards, state, externalState, externalEvidence, extensions);
    }

    public ReleaseOutboxEntry withExternalState(ExternalState state, String evidence) {
        return new ReleaseOutboxEntry(transactionId, playerId, petId, petFingerprint, previewToken,
                rewards, internalState, state, evidence, extensions);
    }

    /**
     * @see ReleaseOutboxDeliveryService#recoverInternal for why ATTEMPTING is written before the port is
     *     called rather than after it answers
     */
    public enum InternalState { PENDING, ATTEMPTING, ACKNOWLEDGED }

    public enum ExternalState {
        NOT_REQUIRED,
        PENDING,
        ATTEMPTING,
        DELIVERED,
        FAILED,
        UNKNOWN_REQUIRES_RECONCILIATION
    }
}
