package io.github.salyvn.omnipet.paper.feedback;

import java.util.Objects;
import java.util.UUID;

import org.bukkit.entity.Player;

/**
 * Static accessor for the active {@link FeedbackService}, mirroring {@code Messages}.
 *
 * <p>Threading a service through the controllers that need it would mean touching seven
 * {@code OmniPetCommand} constructors for a feature that only makes noise. Unlike {@code Messages},
 * an unbound read is a silent no-op rather than an exception: a missing sound must never break a
 * click, and tests that do not care about feedback should not have to bind anything.
 */
public final class Feedback {
    private static volatile FeedbackService active;

    private Feedback() {}

    public static void bind(FeedbackService service) {
        active = Objects.requireNonNull(service, "feedback service");
    }

    public static void unbind() {
        active = null;
    }

    public static void success(Player player, FeedbackEvent event) {
        FeedbackService current = active;
        if (current != null) current.success(player, event);
    }

    public static void failure(Player player, FeedbackEvent event) {
        FeedbackService current = active;
        if (current != null) current.failure(player, event);
    }

    public static void blocked(Player player, FeedbackEvent event) {
        FeedbackService current = active;
        if (current != null) current.blocked(player, event);
    }

    public static void progress(Player player, FeedbackEvent event) {
        FeedbackService current = active;
        if (current != null) current.progress(player, event);
    }

    /** Emits using the event's own category. */
    public static void emit(Player player, FeedbackEvent event) {
        FeedbackService current = active;
        if (current != null) current.emit(player, event);
    }

    /** Drops a player's rate-limit entry on quit. */
    public static void release(UUID playerId) {
        FeedbackService current = active;
        if (current != null) current.release(playerId);
    }
}
