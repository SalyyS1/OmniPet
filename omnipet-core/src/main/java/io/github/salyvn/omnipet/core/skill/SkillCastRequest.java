package io.github.salyvn.omnipet.core.skill;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import io.github.salyvn.omnipet.core.domain.RawNodeValues;

/**
 * One cast, with its targets already chosen.
 *
 * <p>Targets are resolved before this reaches a provider, and carried as entity IDs so the core stays
 * platform-free. The provider used to receive only a {@link SkillTargetPolicy} and ignore it, which meant
 * every skill fell through to the provider's own targeter with the owning <em>player</em> as caster — so a
 * pet's attack skill aimed at whatever the player's own targeting rules picked, if anything. That is the
 * "skills do not really work with MythicMobs" report: the cast happened, and hit nothing.
 *
 * @param actorId whose action caused this, which is the owner for every trigger there is today
 * @param entityTargets the entities to aim at, empty when the policy resolves to none
 * @param power the provider's power multiplier for this cast
 */
public record SkillCastRequest(
        UUID actionId,
        UUID actorId,
        UUID ownerId,
        UUID petInstanceId,
        String skillId,
        SkillTargetPolicy targetPolicy,
        List<UUID> entityTargets,
        double power,
        Map<String, Object> context) {
    public SkillCastRequest {
        actionId = Objects.requireNonNull(actionId, "skill action ID");
        actorId = Objects.requireNonNull(actorId, "skill actor ID");
        ownerId = Objects.requireNonNull(ownerId, "skill owner ID");
        petInstanceId = Objects.requireNonNull(petInstanceId, "skill pet instance ID");
        if (skillId == null || skillId.isBlank()) throw new IllegalArgumentException("skill ID is required");
        targetPolicy = Objects.requireNonNull(targetPolicy, "skill target policy");
        entityTargets = List.copyOf(entityTargets == null ? List.of() : entityTargets);
        if (entityTargets.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("skill entity targets cannot contain null");
        }
        if (!Double.isFinite(power) || power <= 0) {
            throw new IllegalArgumentException("skill power must be positive and finite");
        }
        context = RawNodeValues.immutableMap(context == null ? Map.of() : context);
    }

    /**
     * A cast with no explicit targets and ordinary power.
     *
     * <p>Kept for the call sites that predate targeting, where the provider's own targeter is the intent.
     */
    public SkillCastRequest(
            UUID actionId,
            UUID actorId,
            UUID ownerId,
            UUID petInstanceId,
            String skillId,
            SkillTargetPolicy targetPolicy,
            Map<String, Object> context) {
        this(actionId, actorId, ownerId, petInstanceId, skillId, targetPolicy, List.of(), 1, context);
    }

    /** Whether this cast names its own targets, rather than leaving the choice to the provider. */
    public boolean targeted() {
        return !entityTargets.isEmpty();
    }
}
