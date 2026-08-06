package io.github.salyvn.omnipet.paper.feedback;

import java.util.Objects;

import io.github.salyvn.omnipet.paper.text.MessageKey;

/**
 * What happened, at a finer grain than {@link FeedbackCategory}.
 *
 * <p>Call sites name an event, never a {@code Sound} constant: the event-to-sound mapping belongs to
 * the operator's config, and a call site that named a sound directly could drift from the schema.
 *
 * <p>The category drives the sound. The optional message key drives the action bar, so an event with
 * no key still gets its sound.
 */
public enum FeedbackEvent {
    PET_ACTIVATED(FeedbackCategory.SUCCESS, MessageKey.ACTION_BAR_PET_ACTIVATED, true),
    PET_RECALLED(FeedbackCategory.SUCCESS, MessageKey.ACTION_BAR_PET_RECALLED),
    PET_TOGGLE_REJECTED(FeedbackCategory.FAILURE),
    PET_REQUEST_IN_FLIGHT(FeedbackCategory.BLOCKED),
    PET_MANAGEMENT_OPENED(FeedbackCategory.PROGRESS),
    // Deliberately sound-only: cycling the vault sort is repeatable, and narrating every click would
    // make the action bar noise rather than confirmation.
    VAULT_VIEW_CHANGED(FeedbackCategory.PROGRESS),

    HATCH_QUEUED(FeedbackCategory.PROGRESS, MessageKey.ACTION_BAR_HATCH_QUEUED),
    HATCH_CLAIMED(FeedbackCategory.SUCCESS, MessageKey.ACTION_BAR_HATCH_CLAIMED, true),
    HATCH_REJECTED(FeedbackCategory.FAILURE),

    SLOT_UNLOCKED(FeedbackCategory.SUCCESS, MessageKey.ACTION_BAR_SLOT_UNLOCKED, true),
    SLOT_PURCHASE_REJECTED(FeedbackCategory.FAILURE),
    SLOT_PURCHASE_IN_FLIGHT(FeedbackCategory.BLOCKED),

    SKILL_SUCCEEDED(FeedbackCategory.SUCCESS),
    SKILL_REJECTED(FeedbackCategory.FAILURE),
    /**
     * A trigger fired and the pet cast. Narrated on the action bar rather than in chat, because a skill
     * bound to a keypress would otherwise write a chat line every time the player presses that key.
     */
    SKILL_TRIGGERED(FeedbackCategory.SUCCESS, MessageKey.ACTION_BAR_SKILL_CAST),
    /**
     * A trigger fired while the skill was still cooling down. The action bar text is supplied by the caller
     * rather than taken from the event, because the remaining time is only known at the refusal.
     */
    SKILL_COOLING_DOWN(FeedbackCategory.BLOCKED, MessageKey.ACTION_BAR_SKILL_COOLDOWN),
    /** A rolled-and-missed proc is a normal outcome, so it reads as progress rather than failure. */
    SKILL_CHANCE_MISSED(FeedbackCategory.PROGRESS);

    private final FeedbackCategory category;
    private final MessageKey actionBar;
    private final boolean celebrated;

    FeedbackEvent(FeedbackCategory category) {
        this(category, null, false);
    }

    FeedbackEvent(FeedbackCategory category, MessageKey actionBar) {
        this(category, actionBar, false);
    }

    FeedbackEvent(FeedbackCategory category, MessageKey actionBar, boolean celebrated) {
        this.category = Objects.requireNonNull(category, "feedback category");
        this.actionBar = actionBar;
        this.celebrated = celebrated;
    }

    public FeedbackCategory category() {
        return category;
    }

    /** The action-bar line for this event, or {@code null} when the event is sound-only. */
    public MessageKey actionBar() {
        return actionBar;
    }

    /**
     * Whether this moment earns a particle burst.
     *
     * <p>Reserved for the handful of things a player waited for — a hatch completing, a slot opening, a
     * pet appearing. A repeatable click that burst particles would stop reading as a reward and start
     * reading as clutter.
     */
    public boolean celebrated() {
        return celebrated;
    }
}
