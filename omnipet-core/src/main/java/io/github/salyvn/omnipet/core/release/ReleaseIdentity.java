package io.github.salyvn.omnipet.core.release;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.salyvn.omnipet.core.domain.PetInstance;
import io.github.salyvn.omnipet.core.domain.RawNodeValues;

final class ReleaseIdentity {
    private ReleaseIdentity() {}

    static String petFingerprint(PetInstance pet) {
        LinkedHashMap<String, Object> identity = new LinkedHashMap<>();
        identity.put("id", pet.id().toString());
        identity.put("definitionId", pet.definitionId());
        identity.put("definitionRevision", pet.definitionRevision());
        identity.put("components", pet.rawComponents());
        identity.put("extensions", pet.extensions());
        return sha256(RawNodeValues.semanticBytes(identity));
    }

    static String confirmationToken(
            java.util.UUID transactionId,
            java.util.UUID playerId,
            java.util.UUID petId,
            long expectedRevision,
            String petFingerprint,
            ReleaseRewardBundle rewards) {
        LinkedHashMap<String, Object> identity = new LinkedHashMap<>();
        identity.put("format", "omnipet-release-preview-v1");
        identity.put("transactionId", transactionId.toString());
        identity.put("playerId", playerId.toString());
        identity.put("petId", petId.toString());
        identity.put("expectedRevision", expectedRevision);
        identity.put("petFingerprint", petFingerprint);
        identity.put("rewards", rewardMap(rewards));
        return sha256(RawNodeValues.semanticBytes(identity));
    }

    static boolean valid(ReleasePreview preview) {
        return MessageDigest.isEqual(
                preview.confirmationToken().getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                confirmationToken(
                        preview.transactionId(),
                        preview.playerId(),
                        preview.petId(),
                        preview.expectedRevision(),
                        preview.petFingerprint(),
                        preview.rewards()).getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    }

    static Map<String, Object> rewardMap(ReleaseRewardBundle rewards) {
        List<Map<String, Object>> internal = rewards.internalRewards().stream().map(reward -> Map.<String, Object>of(
                "rewardId", reward.rewardId(),
                "amount", reward.amount(),
                "payload", reward.payload())).toList();
        List<Map<String, Object>> external = rewards.externalRewards().stream().map(reward -> Map.<String, Object>of(
                "provider", reward.provider(),
                "rewardId", reward.rewardId(),
                "amount", reward.amount().toPlainString(),
                "payload", reward.payload())).toList();
        return Map.of("internal", internal, "external", external);
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
