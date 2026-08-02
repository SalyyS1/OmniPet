package io.github.salyvn.omnipet.core.progression;

import java.util.Objects;
import java.util.UUID;

import io.github.salyvn.omnipet.core.incubation.EggItemIdentity;

public record CultivationItemActionTransaction(
        UUID actionToken,
        UUID playerId,
        UUID petId,
        long expectedPlayerRevision,
        EggItemIdentity item,
        CultivationItemActionKind kind,
        double experienceAmount,
        int requiredLevel,
        int requiredEvolution,
        CultivationItemActionStage stage) {
    public CultivationItemActionTransaction {
        Objects.requireNonNull(actionToken, "cultivation action token");
        Objects.requireNonNull(playerId, "cultivation player ID");
        Objects.requireNonNull(petId, "cultivation pet ID");
        if (expectedPlayerRevision < 0) throw new IllegalArgumentException("cultivation revision is invalid");
        Objects.requireNonNull(item, "cultivation item identity");
        Objects.requireNonNull(kind, "cultivation action kind");
        Objects.requireNonNull(stage, "cultivation action stage");
        if (!actionToken.equals(item.itemNonce())) {
            throw new IllegalArgumentException("cultivation action token must equal the unique item nonce");
        }
        if (kind == CultivationItemActionKind.EXPERIENCE_CANDY
                && (!Double.isFinite(experienceAmount) || experienceAmount <= 0)) {
            throw new IllegalArgumentException("cultivation experience amount is invalid");
        }
        if (kind == CultivationItemActionKind.BREAKTHROUGH_STONE
                && (requiredLevel < 1 || requiredEvolution < 0)) {
            throw new IllegalArgumentException("cultivation breakthrough requirements are invalid");
        }
    }

    public CultivationItemActionTransaction withStage(CultivationItemActionStage next) {
        return new CultivationItemActionTransaction(
                actionToken, playerId, petId, expectedPlayerRevision, item, kind,
                experienceAmount, requiredLevel, requiredEvolution, next);
    }

    public boolean sameIdentity(CultivationItemActionTransaction other) {
        return other != null
                && actionToken.equals(other.actionToken)
                && playerId.equals(other.playerId)
                && petId.equals(other.petId)
                && expectedPlayerRevision == other.expectedPlayerRevision
                && item.equals(other.item)
                && kind == other.kind
                && Double.compare(experienceAmount, other.experienceAmount) == 0
                && requiredLevel == other.requiredLevel
                && requiredEvolution == other.requiredEvolution;
    }
}
