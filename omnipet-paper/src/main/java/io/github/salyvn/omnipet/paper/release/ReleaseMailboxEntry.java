package io.github.salyvn.omnipet.paper.release;

import java.util.List;
import java.util.UUID;

public record ReleaseMailboxEntry(
        UUID transactionId,
        UUID playerId,
        State state,
        List<PaperMaterialReward> rewards,
        String detail) {
    public ReleaseMailboxEntry {
        if (transactionId == null || playerId == null || state == null) {
            throw new IllegalArgumentException("release mailbox identity and state are required");
        }
        rewards = List.copyOf(rewards == null ? List.of() : rewards);
        if (rewards.size() > 32) throw new IllegalArgumentException("release mailbox reward count exceeds 32");
        detail = detail == null ? "" : detail;
        if (detail.length() > 512) throw new IllegalArgumentException("release mailbox detail is too long");
    }

    public ReleaseMailboxEntry withState(State next, String nextDetail) {
        return new ReleaseMailboxEntry(transactionId, playerId, next, rewards, nextDetail);
    }

    public enum State {
        MAILBOX_PENDING,
        INVENTORY_CLAIMING,
        INVENTORY_DELIVERED,
        UNKNOWN_REQUIRES_RECONCILIATION
    }
}
