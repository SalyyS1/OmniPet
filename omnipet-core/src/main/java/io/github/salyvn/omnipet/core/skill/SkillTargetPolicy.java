package io.github.salyvn.omnipet.core.skill;

/**
 * What a pet's skill is aimed at.
 *
 * <p>Resolved by the platform and handed to the provider as explicit targets, rather than left for the
 * provider to work out. MythicMobs will pick its own target from the caster, but the caster is the owning
 * player, so a skill authored with {@code @Target} aims at whatever the *player* is targeting under
 * MythicMobs' own rules — which is not the same as what the pet should hit, and is invisible to whoever
 * wrote the pet definition.
 */
public enum SkillTargetPolicy {
    /**
     * What the owner is looking at, and failing that the nearest hostile mob.
     *
     * <p>The default, and the one to reach for. Looking at something is an unambiguous statement of intent,
     * so it wins; but a player mid-fight is often not looking straight at anything, and refusing the cast
     * then would make the skill feel unreliable in exactly the situation it exists for.
     */
    LOOK_THEN_NEAREST,
    /** Only what the owner is looking at. Refuses the cast when they are looking at nothing. */
    LOOK_TARGET,
    /** The nearest hostile mob in range, whatever the owner is looking at. */
    NEAREST_HOSTILE,
    /** The owner themself, for a heal or a buff. */
    OWNER,
    /** The pet itself. */
    PET,
    /** Every hostile mob within range of the owner. */
    AREA_AROUND_OWNER,
    /** Every hostile mob within range of the pet. */
    AREA_AROUND_PET,
    /**
     * No targets at all: whatever the skill's own {@code @Target} clause selects.
     *
     * <p>For an author who wants MythicMobs' targeters instead of these. Nothing is passed, so a skill with
     * no targeter of its own does nothing.
     */
    PROVIDER_DEFAULT
}
