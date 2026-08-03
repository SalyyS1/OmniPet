package io.github.salyvn.omnipet.paper.feedback;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import org.bukkit.Sound;

import io.github.salyvn.omnipet.paper.config.GuiConfig;

/**
 * Sound names resolved to real {@link Sound} constants, once, at load.
 *
 * <p>Resolution is fail-soft and never happens per click. An operator naming a sound that this server
 * version does not have gets a warning and silence for that one category — an exception thrown into a
 * click handler would be a far worse outcome than a missing noise.
 *
 * <p>Immutable, so a reload builds a new instance and swaps it in. A bad name in the new config leaves
 * the previous settings live rather than half-applying.
 */
public final class FeedbackSettings {
    private final boolean enabled;
    private final boolean actionBar;
    private final Duration minimumInterval;
    private final Map<FeedbackCategory, ResolvedSound> sounds;

    private FeedbackSettings(
            boolean enabled,
            boolean actionBar,
            Duration minimumInterval,
            Map<FeedbackCategory, ResolvedSound> sounds) {
        this.enabled = enabled;
        this.actionBar = actionBar;
        this.minimumInterval = Objects.requireNonNull(minimumInterval, "minimum interval");
        this.sounds = new EnumMap<>(sounds);
    }

    /**
     * Resolves every configured cue. Unresolvable names are reported through {@code warnings} and
     * simply absent from the result, which the service reads as "this category is silent".
     */
    public static FeedbackSettings resolve(GuiConfig.Feedback config, Consumer<String> warnings) {
        Objects.requireNonNull(config, "feedback config");
        Consumer<String> warn = warnings == null ? message -> {} : warnings;
        Map<FeedbackCategory, ResolvedSound> resolved = new EnumMap<>(FeedbackCategory.class);
        put(resolved, FeedbackCategory.SUCCESS, config.success(), warn);
        put(resolved, FeedbackCategory.FAILURE, config.failure(), warn);
        put(resolved, FeedbackCategory.BLOCKED, config.blocked(), warn);
        put(resolved, FeedbackCategory.PROGRESS, config.progress(), warn);
        return new FeedbackSettings(config.enabled(), config.actionBar(), config.minimumInterval(), resolved);
    }

    /** Everything off. Used when feedback is disabled and as the safe state before binding. */
    public static FeedbackSettings silent() {
        return new FeedbackSettings(false, false, Duration.ZERO, Map.of());
    }

    private static void put(
            Map<FeedbackCategory, ResolvedSound> target,
            FeedbackCategory category,
            GuiConfig.Cue cue,
            Consumer<String> warn) {
        Sound sound;
        try {
            sound = Sound.valueOf(cue.sound());
        } catch (IllegalArgumentException unknown) {
            warn.accept("unknown sound name '" + cue.sound() + "' for gui.feedback."
                    + category.name().toLowerCase(java.util.Locale.ROOT)
                    + "; that category will be silent");
            return;
        }
        target.put(category, new ResolvedSound(sound, cue.volume(), cue.pitch()));
    }

    public boolean enabled() {
        return enabled;
    }

    public boolean actionBar() {
        return actionBar;
    }

    public Duration minimumInterval() {
        return minimumInterval;
    }

    /** The sound for a category, or empty when it is unconfigured or failed to resolve. */
    public Optional<ResolvedSound> sound(FeedbackCategory category) {
        return Optional.ofNullable(sounds.get(category));
    }
}
