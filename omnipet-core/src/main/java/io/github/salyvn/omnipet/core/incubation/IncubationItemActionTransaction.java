package io.github.salyvn.omnipet.core.incubation;

import java.util.Objects;
import java.util.UUID;

public record IncubationItemActionTransaction(
        UUID actionToken,
        UUID playerId,
        UUID incubationId,
        long expectedPlayerRevision,
        EggItemIdentity item,
        IncubationItemActionType type,
        long effectMillis,
        IncubationItemActionStage stage) {
    public IncubationItemActionTransaction {
        Objects.requireNonNull(actionToken, "incubation item action token");
        Objects.requireNonNull(playerId, "incubation item action player");
        Objects.requireNonNull(incubationId, "incubation item action incubation");
        if (expectedPlayerRevision < 0) throw new IllegalArgumentException("expected player revision cannot be negative");
        Objects.requireNonNull(item, "incubation item identity");
        Objects.requireNonNull(type, "incubation item action type");
        Objects.requireNonNull(stage, "incubation item action stage");
        if (type == IncubationItemActionType.REDUCE && effectMillis <= 0) {
            throw new IllegalArgumentException("REDUCE effect must be positive");
        }
        if (type == IncubationItemActionType.COMPLETE && effectMillis != 0) {
            throw new IllegalArgumentException("COMPLETE effect amount must be zero");
        }
        IncubationItemActionItemContract.requireMatches(item, type, effectMillis);
    }

    public IncubationItemActionTransaction withStage(IncubationItemActionStage next) {
        return new IncubationItemActionTransaction(
                actionToken, playerId, incubationId, expectedPlayerRevision, item, type, effectMillis, next);
    }

    public boolean sameIdentity(IncubationItemActionTransaction other) {
        return other != null
                && actionToken.equals(other.actionToken)
                && playerId.equals(other.playerId)
                && incubationId.equals(other.incubationId)
                && expectedPlayerRevision == other.expectedPlayerRevision
                && item.equals(other.item)
                && type == other.type
                && effectMillis == other.effectMillis;
    }
}
