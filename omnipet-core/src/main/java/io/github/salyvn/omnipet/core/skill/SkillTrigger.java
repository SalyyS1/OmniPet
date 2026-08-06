package io.github.salyvn.omnipet.core.skill;

/**
 * What makes a pet cast a skill.
 *
 * <p>Two families. A {@link Kind#ACTIVE} trigger is something the owner deliberately does — a key they
 * press — and a {@link Kind#PASSIVE} one is something that happens to them or around them. The distinction
 * is not cosmetic: an active cast that cannot fire tells the player why, because they asked for it and are
 * owed an answer, while a passive one stays silent, because a message on every hit would be unreadable.
 *
 * <p>Only triggers a Paper server can actually observe are listed. A critical hit and a dodge were asked
 * for and are deliberately absent: vanilla exposes no event for either, and the usual workarounds —
 * inferring a crit from fall distance and sprint state, or a dodge from a damage event that never arrived —
 * are guesses that would fire wrongly often enough to look broken. {@link #ON_DAMAGE_TAKEN} with a chance
 * covers most of what a dodge trigger would have been for.
 *
 * <p>The old {@code WALK}, {@code KILL}, {@code DAMAGE}, and {@code INTERACT} names are gone. None of them
 * was ever dispatched — the runtime rejected everything except {@code ACTIVE} — so no server can have a
 * working definition using them, and keeping the names would have implied a behaviour that never existed.
 * {@link #ON_KILL}, {@link #ON_DAMAGE_TAKEN}, and the click triggers are what they should have meant.
 *
 * <p>A "pet released" trigger is absent for a structural reason rather than a missing event: every cast is
 * gated on the pet being active, so a skill that fires as the pet leaves would be refused by the step that
 * charges its stamina. Making it work would mean a second, ungated cast path, which is a much larger change
 * than the trigger is worth.
 */
public enum SkillTrigger {
    // --- active: the owner presses something ---------------------------------------------------
    /** Cast by hand with {@code /pet skill}, which is also how a macro or a menu button casts. */
    ACTIVE(Kind.ACTIVE),
    /** Right-click while sneaking. The least likely to collide with ordinary play. */
    SHIFT_RIGHT_CLICK(Kind.ACTIVE),
    /** Left-click while sneaking. */
    SHIFT_LEFT_CLICK(Kind.ACTIVE),
    /** Right-click without sneaking. Collides with placing blocks and using items; pair it with a cooldown. */
    RIGHT_CLICK(Kind.ACTIVE),
    /** Left-click without sneaking, which is also the ordinary attack swing. */
    LEFT_CLICK(Kind.ACTIVE),
    /** The drop key, Q by default. Free of collisions, because the drop itself is cancelled. */
    DROP_KEY(Kind.ACTIVE),
    /** The off-hand swap key, F by default. The swap is cancelled so nothing moves. */
    SWAP_HAND_KEY(Kind.ACTIVE),
    /** Jumping, space by default. Fires often, so it needs a cooldown. */
    JUMP(Kind.ACTIVE),
    /** Beginning to sneak. The press, not the release. */
    SNEAK(Kind.ACTIVE),
    /** Beginning to sprint. */
    SPRINT(Kind.ACTIVE),

    // --- passive: something happens ------------------------------------------------------------
    /** The owner landed a hit on something. */
    ON_ATTACK(Kind.PASSIVE),
    /** The owner took damage from anything. */
    ON_DAMAGE_TAKEN(Kind.PASSIVE),
    /** The owner's health crossed below a threshold. Re-arms only when they heal back above it. */
    ON_LOW_HEALTH(Kind.PASSIVE),
    /** The owner killed something. */
    ON_KILL(Kind.PASSIVE),
    /** A mob started targeting the owner. */
    ON_TARGETED(Kind.PASSIVE),
    /** The owner died, cast before they drop. */
    ON_DEATH(Kind.PASSIVE),
    /** On a fixed period while the pet is out. */
    INTERVAL(Kind.PASSIVE);

    private final Kind kind;

    SkillTrigger(Kind kind) {
        this.kind = kind;
    }

    /** Whether the owner asked for this cast, which decides whether a refusal is worth reporting. */
    public Kind kind() {
        return kind;
    }

    /** Whether a refused cast should tell the player why. */
    public boolean announces() {
        return kind == Kind.ACTIVE;
    }

    public enum Kind {
        ACTIVE,
        PASSIVE
    }
}
