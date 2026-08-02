package io.github.salyvn.omnipet.core.release;

import java.util.List;
import java.util.UUID;

@FunctionalInterface
public interface InternalRewardDeliveryPort {
    /** Implementations must treat transactionId as an idempotency key. */
    Outcome deliver(UUID playerId, UUID transactionId, List<ReleaseRewardBundle.InternalReward> rewards);

    record Outcome(Status status, String detail) {
        public Outcome {
            if (status == null) throw new IllegalArgumentException("internal delivery status is required");
            detail = detail == null ? "" : detail;
        }

        public static Outcome delivered(String detail) { return new Outcome(Status.DELIVERED, detail); }
        public static Outcome capacityFull(String detail) { return new Outcome(Status.CAPACITY_FULL, detail); }
        public static Outcome failed(String detail) { return new Outcome(Status.FAILED, detail); }
    }

    enum Status { DELIVERED, CAPACITY_FULL, FAILED }
}
