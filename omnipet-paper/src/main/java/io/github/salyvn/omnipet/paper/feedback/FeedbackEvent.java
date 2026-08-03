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
    PET_ACTIVATED(FeedbackCategory.SUCCESS),
    PET_RECALLED(FeedbackCategory.SUCCESS),
    PET_TOGGLE_REJECTED(FeedbackCategory.FAILURE),
    PET_REQUEST_IN_FLIGHT(FeedbackCategory.BLOCKED),
    PET_MANAGEMENT_OPENED(FeedbackCategory.PROGRESS),
    VAULT_VIEW_CHANGED(FeedbackCategory.PROGRESS),

    HATCH_QUEUED(FeedbackCategory.PROGRESS),
    HATCH_CLAIMED(FeedbackCategory.SUCCESS),
    HATCH_REJECTED(FeedbackCategory.FAILURE),

    SLOT_UNLOCKED(FeedbackCategory.SUCCESS),
    SLOT_PURCHASE_REJECTED(FeedbackCategory.FAILURE),
    SLOT_PURCHASE_IN_FLIGHT(FeedbackCategory.BLOCKED),

    SKILL_SUCCEEDED(FeedbackCategory.SUCCESS),
    SKILL_REJECTED(FeedbackCategory.FAILURE),
    /** A rolled-and-missed proc is a normal outcome, so it reads as progress rather than failure. */
    SKILL_CHANCE_MISSED(FeedbackCategory.PROGRESS);

    private final FeedbackCategory category;
    private final MessageKey actionBar;

    FeedbackEvent(FeedbackCategory category) {
        this(category, null);
    }

    FeedbackEvent(FeedbackCategory category, MessageKey actionBar) {
        this.category = Objects.requireNonNull(category, "feedback category");
        this.actionBar = actionBar;
    }

    public FeedbackCategory category() {
        return category;
    }

    /** The action-bar line for this event, or {@code null} when the event is sound-only. */
    public MessageKey actionBar() {
        return actionBar;
    }
}
