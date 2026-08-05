package io.github.salyvn.omnipet.paper.management;

import io.github.salyvn.omnipet.paper.text.MessageKey;

/**
 * Plain-language text for a management outcome that carried no detail of its own.
 *
 * <p>Without this a status reached the player as its lowercased enum name. "Persisted consumption
 * pending" tells a player nothing and leaks internal vocabulary; worse, it cannot be translated, because
 * it is generated from the constant rather than read from the catalog.
 */
public final class PetManagementStatusText {
    private PetManagementStatusText() {}

    /**
     * The line to show, or null for a status the player should never be shown.
     *
     * <p>The success and view-ready statuses return null: those are reported by the menu redrawing, and a
     * message saying "persisted" alongside the visibly updated pet would be noise.
     */
    public static MessageKey of(PetManagementOutcome.Status status) {
        if (status == null) return MessageKey.STATUS_ERROR;
        return switch (status) {
            case REJECTED -> MessageKey.STATUS_REJECTED;
            case PET_NOT_FOUND -> MessageKey.STATUS_PET_NOT_FOUND;
            case UNAUTHORIZED -> MessageKey.STATUS_UNAUTHORIZED;
            case STALE_SESSION -> MessageKey.STATUS_STALE_SESSION;
            case BUSY -> MessageKey.STATUS_BUSY;
            case CONSUMABLE_UNAVAILABLE -> MessageKey.STATUS_CONSUMABLE_UNAVAILABLE;
            case PERSISTED_CONSUMPTION_PENDING -> MessageKey.STATUS_CONSUMPTION_PENDING;
            case RELEASE_COMMITTED_OUTBOX_PENDING -> MessageKey.STATUS_OUTBOX_PENDING;
            case ERROR -> MessageKey.STATUS_ERROR;
            case VIEW_READY, PERSISTED, RELEASE_PREVIEW_READY, OUTBOX_RECOVERED -> null;
        };
    }
}
