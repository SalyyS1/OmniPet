package io.github.salyvn.omnipet.paper.feedback;

/**
 * The four outcomes a player can experience. Sound is configured per category, not per event, so
 * adding a feature never obliges an operator to configure a new sound.
 */
public enum FeedbackCategory {
    /** The thing the player asked for happened. */
    SUCCESS,
    /** The request was understood but refused: too expensive, wrong state, rejected by a service. */
    FAILURE,
    /** The request could not even be attempted: locked, already in flight, no permission. */
    BLOCKED,
    /**
     * Something is underway or a normal non-event occurred. A missed skill proc belongs here rather
     * than in {@link #FAILURE}: it is an expected outcome and should not sound like an error.
     */
    PROGRESS
}
