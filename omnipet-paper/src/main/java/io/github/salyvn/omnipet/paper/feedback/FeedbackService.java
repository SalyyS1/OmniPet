package io.github.salyvn.omnipet.paper.feedback;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

import org.bukkit.entity.Player;

import io.github.salyvn.omnipet.paper.text.MessageKey;
import io.github.salyvn.omnipet.paper.text.Messages;

/**
 * The one place that turns "something happened" into a sound the player hears.
 *
 * <p>Call sites state intent — {@code success(player, PET_ACTIVATED)} — and never name a {@code Sound}
 * constant, so the event-to-sound mapping stays in the operator's config and cannot drift from the
 * config schema at twenty separate call sites.
 *
 * <p>Rate limiting lives here rather than at those call sites. A per-player floor cannot be enforced
 * consistently if each caller has to remember it, and a player holding down a menu click would
 * otherwise become an audible nuisance. The limiter map is evicted on quit; without that it retains a
 * UUID per player for the server's lifetime.
 *
 * <p>Feedback never replaces a chat message. Text stays the accessible channel and the sound is
 * additive, which is also why a single {@code enabled: false} can turn all of this off.
 */
public final class FeedbackService {
    private final FeedbackOutput output;
    private final LongSupplier clock;
    private final Map<UUID, Long> lastPlayed = new ConcurrentHashMap<>();
    private volatile FeedbackSettings settings;

    public FeedbackService(FeedbackOutput output, FeedbackSettings settings) {
        this(output, settings, System::currentTimeMillis);
    }

    FeedbackService(FeedbackOutput output, FeedbackSettings settings, LongSupplier clock) {
        this.output = Objects.requireNonNull(output, "feedback output");
        this.settings = Objects.requireNonNull(settings, "feedback settings");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Swaps in reloaded settings. Staged by the caller, so a config whose sound names fail to resolve
     * never reaches this method and the previous settings stay live.
     */
    public void apply(FeedbackSettings next) {
        settings = Objects.requireNonNull(next, "feedback settings");
    }

    public void success(Player player, FeedbackEvent event) {
        emit(player, event, FeedbackCategory.SUCCESS);
    }

    public void failure(Player player, FeedbackEvent event) {
        emit(player, event, FeedbackCategory.FAILURE);
    }

    public void blocked(Player player, FeedbackEvent event) {
        emit(player, event, FeedbackCategory.BLOCKED);
    }

    public void progress(Player player, FeedbackEvent event) {
        emit(player, event, FeedbackCategory.PROGRESS);
    }

    /** Emits using the event's own category, for call sites that already hold the event. */
    public void emit(Player player, FeedbackEvent event) {
        emit(player, event, event == null ? null : event.category());
    }

    /** Frees the rate-limit entry. Called on quit, so the map cannot grow for the server's lifetime. */
    public void release(UUID playerId) {
        if (playerId != null) lastPlayed.remove(playerId);
    }

    public void clear() {
        lastPlayed.clear();
    }

    int trackedPlayers() {
        return lastPlayed.size();
    }

    private void emit(Player player, FeedbackEvent event, FeedbackCategory category) {
        FeedbackSettings current = settings;
        if (player == null || event == null || category == null || !current.enabled()) return;
        if (!allow(player.getUniqueId(), current.minimumInterval())) return;
        current.sound(category).ifPresent(sound -> output.sound(player, sound));
        // Reserved for moments a player waited for. Shares the rate limiter with the sound, so a
        // spammable click cannot turn into a particle fountain.
        if (event.celebrated()) {
            current.celebration().ifPresent(particle -> output.particle(player, particle));
        }
        if (!current.actionBar()) return;
        MessageKey key = event.actionBar();
        if (key != null) output.actionBar(player, Messages.line(key));
    }

    /**
     * True when enough time has passed since this player's last cue. Records the timestamp only when
     * allowing, so a burst of suppressed clicks cannot push the next permitted sound further away.
     */
    private boolean allow(UUID playerId, Duration minimumInterval) {
        long interval = minimumInterval.toMillis();
        if (interval <= 0) return true;
        long now = clock.getAsLong();
        Long previous = lastPlayed.get(playerId);
        if (previous != null && now - previous < interval) return false;
        lastPlayed.put(playerId, now);
        return true;
    }
}
