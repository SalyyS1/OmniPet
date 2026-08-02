package io.github.salyvn.omnipet.core.release;

import java.util.List;
import java.util.UUID;

@FunctionalInterface
public interface ExternalRewardDeliveryPort {
    Outcome deliver(UUID playerId, UUID transactionId, List<ReleaseRewardBundle.ExternalReward> rewards);

    record Outcome(Status status, String evidence) {
        public Outcome {
            if (status == null) throw new IllegalArgumentException("external delivery status is required");
            evidence = evidence == null ? "" : evidence;
            if (evidence.length() > 512) throw new IllegalArgumentException("external evidence is too long");
        }

        public static Outcome succeeded(String evidence) { return new Outcome(Status.PROVEN_SUCCESS, evidence); }
        public static Outcome failed(String evidence) { return new Outcome(Status.PROVEN_FAILURE, evidence); }
        public static Outcome unknown(String evidence) { return new Outcome(Status.UNKNOWN_COMMIT, evidence); }
    }

    enum Status { PROVEN_SUCCESS, PROVEN_FAILURE, UNKNOWN_COMMIT }
}
