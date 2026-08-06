package io.github.salyvn.omnipet.paper.skill;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;

import io.github.salyvn.omnipet.core.skill.SkillTargetPolicy;

/**
 * Turns a {@link SkillTargetPolicy} into the entities a cast should aim at.
 *
 * <p>Lives on the Paper side because every one of these questions — what is the player looking at, what is
 * nearby, is it hostile — is a world query. The core carries only the answer, as entity IDs.
 *
 * <p>Hostility is "a {@link Mob} that is not a pet of this owner". Checking for {@code Monster} instead
 * would exclude a great deal of what a player actually fights: a Ghast, a Slime, a Phantom, a Hoglin and an
 * angry Piglin are all mobs and none is a {@code Monster}. Excluding tame animals would then be a separate
 * problem; the pet exclusion covers the case that matters, which is a pet's skill hitting its own side.
 */
public final class PaperSkillTargetResolver {
    /** How far past the target range a look ray may reach. Zero: the range is the range. */
    private static final double LOOK_RAY_TOLERANCE = 0;

    private PaperSkillTargetResolver() {}

    /**
     * The entity IDs to aim at.
     *
     * @param petEntities the owner's own pet entities, never targeted and never counted as hostile
     * @return possibly empty, which for every policy but {@link SkillTargetPolicy#PROVIDER_DEFAULT} means
     *     the cast found nothing to hit
     */
    public static List<UUID> resolve(
            SkillTargetPolicy policy, Player owner, Entity petEntity, double range, List<UUID> petEntities) {
        Objects.requireNonNull(policy, "skill target policy");
        Objects.requireNonNull(owner, "skill owner");
        List<UUID> pets = List.copyOf(petEntities == null ? List.of() : petEntities);
        return switch (policy) {
            case PROVIDER_DEFAULT -> List.of();
            case OWNER -> List.of(owner.getUniqueId());
            case PET -> petEntity == null ? List.of() : List.of(petEntity.getUniqueId());
            case LOOK_TARGET -> single(lookTarget(owner, range, pets));
            case NEAREST_HOSTILE -> single(nearestHostile(owner, owner.getLocation(), range, pets));
            case LOOK_THEN_NEAREST -> {
                LivingEntity looked = lookTarget(owner, range, pets);
                yield single(looked != null ? looked : nearestHostile(owner, owner.getLocation(), range, pets));
            }
            case AREA_AROUND_OWNER -> hostilesAround(owner, owner.getLocation(), range, pets);
            case AREA_AROUND_PET -> petEntity == null
                    ? List.of()
                    : hostilesAround(owner, petEntity.getLocation(), range, pets);
        };
    }

    /**
     * What the owner is looking at, by ray trace rather than by angle.
     *
     * <p>A ray trace is what the player's own crosshair does, so "looking at" means the same thing to the
     * skill as it does to the player. Blocks are ignored: a mob behind a fence post is still what the
     * player is aiming at, and a skill refusing to fire because of a fence post reads as broken.
     */
    private static LivingEntity lookTarget(Player owner, double range, List<UUID> pets) {
        RayTraceResult hit = owner.getWorld().rayTraceEntities(
                owner.getEyeLocation(),
                owner.getEyeLocation().getDirection(),
                range + LOOK_RAY_TOLERANCE,
                candidate -> targetable(candidate, owner, pets));
        return hit != null && hit.getHitEntity() instanceof LivingEntity living ? living : null;
    }

    private static LivingEntity nearestHostile(Player owner, Location around, double range, List<UUID> pets) {
        return hostiles(owner, around, range, pets).stream()
                .min(Comparator.comparingDouble(entity -> entity.getLocation().distanceSquared(around)))
                .orElse(null);
    }

    private static List<UUID> hostilesAround(Player owner, Location around, double range, List<UUID> pets) {
        return hostiles(owner, around, range, pets).stream().map(Entity::getUniqueId).toList();
    }

    private static List<LivingEntity> hostiles(Player owner, Location around, double range, List<UUID> pets) {
        List<LivingEntity> found = new ArrayList<>();
        for (Entity candidate : around.getWorld().getNearbyEntities(around, range, range, range)) {
            if (candidate instanceof Mob mob && targetable(candidate, owner, pets)) found.add(mob);
        }
        return found;
    }

    /** Whether a skill may aim at this entity: alive, not the owner, and not one of the owner's pets. */
    private static boolean targetable(Entity candidate, Player owner, List<UUID> pets) {
        if (!(candidate instanceof LivingEntity living) || living.isDead()) return false;
        if (candidate.getUniqueId().equals(owner.getUniqueId())) return false;
        return !pets.contains(candidate.getUniqueId());
    }

    private static List<UUID> single(LivingEntity entity) {
        return entity == null ? List.of() : List.of(entity.getUniqueId());
    }
}
