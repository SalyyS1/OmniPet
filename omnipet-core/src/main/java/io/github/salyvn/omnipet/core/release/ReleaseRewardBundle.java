package io.github.salyvn.omnipet.core.release;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import io.github.salyvn.omnipet.core.domain.RawNodeValues;
import io.github.salyvn.omnipet.core.domain.StableId;

public record ReleaseRewardBundle(
        List<InternalReward> internalRewards,
        List<ExternalReward> externalRewards) {
    public static final int MAX_REWARDS_PER_KIND = 32;
    public static final long MAX_INTERNAL_AMOUNT = 1_000_000_000L;
    public static final BigDecimal MAX_EXTERNAL_AMOUNT = new BigDecimal("1000000000000000");
    public static final int MAX_PAYLOAD_BYTES = 8 * 1024;

    public ReleaseRewardBundle {
        internalRewards = List.copyOf(internalRewards == null ? List.of() : internalRewards);
        externalRewards = List.copyOf(externalRewards == null ? List.of() : externalRewards);
        if (internalRewards.size() > MAX_REWARDS_PER_KIND || externalRewards.size() > MAX_REWARDS_PER_KIND) {
            throw new IllegalArgumentException("release reward count exceeds the supported bound");
        }
    }

    public record InternalReward(String rewardId, long amount, Map<String, Object> payload) {
        public InternalReward {
            rewardId = StableId.requireValid(rewardId);
            if (amount < 1 || amount > MAX_INTERNAL_AMOUNT) {
                throw new IllegalArgumentException("internal reward amount is outside the supported range");
            }
            payload = boundedPayload(payload);
        }
    }

    public record ExternalReward(
            String provider,
            String rewardId,
            BigDecimal amount,
            Map<String, Object> payload) {
        public ExternalReward {
            provider = StableId.requireValid(provider);
            rewardId = StableId.requireValid(rewardId);
            if (amount == null || amount.signum() <= 0 || amount.compareTo(MAX_EXTERNAL_AMOUNT) > 0) {
                throw new IllegalArgumentException("external reward amount is outside the supported range");
            }
            if (amount.scale() > 8) throw new IllegalArgumentException("external reward precision exceeds 8 decimals");
            amount = amount.stripTrailingZeros();
            payload = boundedPayload(payload);
        }
    }

    private static Map<String, Object> boundedPayload(Map<String, Object> payload) {
        Map<String, Object> frozen = RawNodeValues.immutableMap(payload == null ? Map.of() : payload);
        RawNodeValues.rejectNonFinite(frozen, "releaseReward.payload");
        if (RawNodeValues.semanticBytes(frozen).length > MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("release reward payload exceeds 8 KiB");
        }
        return frozen;
    }
}
