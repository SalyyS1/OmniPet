package io.github.salyvn.omnipet.paper.config;

import java.time.Duration;
import java.util.Objects;

/**
 * Operator-tunable GUI numbers and feedback settings.
 *
 * <p>Every value here was a Java literal before this record existed, so changing a page size or a
 * prompt timeout meant a rebuild. The whole section is optional: {@link #defaults()} reproduces the
 * previous hardcoded values exactly, so a config with no {@code gui:} section behaves as it did.
 *
 * <p>Values are clamped rather than rejected. A bad page size must not stop players using their pets,
 * which is why this follows the lenient message-catalog precedent instead of the strict storage one.
 */
public record GuiConfig(
        Feedback feedback,
        int vaultPetsPerPage,
        int helpLinesPerPage,
        Duration studioPromptTimeout,
        boolean studioAutoCreateEgg,
        boolean firstJoinGreeting,
        String locale,
        java.util.Map<String, MenuStyle> menus) {
    /** A vault page cannot exceed this: the 54-slot layout reserves the bottom row for controls. */
    public static final int MAX_VAULT_PETS_PER_PAGE = 45;
    public static final int MAX_HELP_LINES_PER_PAGE = 20;

    public GuiConfig {
        feedback = Objects.requireNonNull(feedback, "feedback settings");
        // Blank means the built-in English text, which is what an unset key should give.
        locale = locale == null || locale.isBlank() ? "en" : locale.trim();
        menus = menus == null ? java.util.Map.of() : java.util.Map.copyOf(menus);
        vaultPetsPerPage = clamp(vaultPetsPerPage, 1, MAX_VAULT_PETS_PER_PAGE);
        helpLinesPerPage = clamp(helpLinesPerPage, 1, MAX_HELP_LINES_PER_PAGE);
        Objects.requireNonNull(studioPromptTimeout, "studio prompt timeout");
        if (studioPromptTimeout.isNegative() || studioPromptTimeout.isZero()) {
            throw new IllegalArgumentException("studio prompt timeout must be positive");
        }
    }

    /**
     * The values the plugin used before {@code gui:} existed, plus companion-egg creation on, which is
     * what makes a Studio-created pet reachable in game without a second command.
     */
    public static GuiConfig defaults() {
        return new GuiConfig(Feedback.defaults(), MAX_VAULT_PETS_PER_PAGE, 8, Duration.ofMinutes(2), true,
                true, "en", java.util.Map.of());
    }

    /** The operator's layout for one menu, or an all-defaults style when they configured none. */
    public MenuStyle menu(String name) {
        return menus.getOrDefault(Objects.requireNonNull(name, "menu name"), MenuStyle.defaults());
    }

    /**
     * The studio prompt timeout in ticks, derived from {@link #studioPromptTimeout} rather than
     * maintained beside it. Six call sites previously carried a hand-written {@code 2 * 60 * 20L}
     * next to a {@code Duration.ofMinutes(2)}; deriving one from the other removes six chances for
     * the two to disagree.
     */
    public long studioPromptTimeoutTicks() {
        return studioPromptTimeout.toSeconds() * 20L;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    /**
     * Sound and action-bar settings per outcome category.
     *
     * <p>Sound names are held as text and resolved once at load by the feedback layer, not here:
     * config parsing must not depend on a live Bukkit registry, and an unknown name must degrade to
     * silence for that one category rather than failing the config.
     */
    public record Feedback(
            boolean enabled,
            boolean actionBar,
            boolean particles,
            String particleName,
            int particleCount,
            Duration minimumInterval,
            Cue success,
            Cue failure,
            Cue blocked,
            Cue progress) {
        public Feedback {
            // Clamped rather than rejected, like every other display value: a silly particle count must
            // not stop players using their pets.
            particleName = particleName == null || particleName.isBlank() ? "HAPPY_VILLAGER" : particleName.trim();
            particleCount = Math.max(1, Math.min(particleCount, MAX_PARTICLE_COUNT));
            Objects.requireNonNull(minimumInterval, "feedback minimum interval");
            if (minimumInterval.isNegative()) throw new IllegalArgumentException("feedback interval cannot be negative");
            success = Objects.requireNonNull(success, "success cue");
            failure = Objects.requireNonNull(failure, "failure cue");
            blocked = Objects.requireNonNull(blocked, "blocked cue");
            progress = Objects.requireNonNull(progress, "progress cue");
        }

        /** Bounded so a mistyped count cannot ask the server to send thousands of particles. */
        public static final int MAX_PARTICLE_COUNT = 64;

        public static Feedback defaults() {
            return new Feedback(
                    true,
                    true,
                    true,
                    "HAPPY_VILLAGER",
                    12,
                    Duration.ofMillis(150),
                    new Cue("ENTITY_EXPERIENCE_ORB_PICKUP", 0.6f, 1.2f),
                    new Cue("BLOCK_NOTE_BLOCK_BASS", 0.6f, 0.8f),
                    new Cue("BLOCK_CHEST_LOCKED", 0.6f, 1.0f),
                    new Cue("UI_BUTTON_CLICK", 0.4f, 1.0f));
        }
    }

    /** One sound with its volume and pitch. Volume and pitch are clamped to the Bukkit-sane range. */
    public record Cue(String sound, float volume, float pitch) {
        public Cue {
            if (sound == null || sound.isBlank()) throw new IllegalArgumentException("sound name is required");
            sound = sound.trim().toUpperCase(java.util.Locale.ROOT);
            volume = clamp(volume, 0.0f, 10.0f);
            pitch = clamp(pitch, 0.5f, 2.0f);
        }

        private static float clamp(float value, float minimum, float maximum) {
            if (!Float.isFinite(value)) return minimum;
            return Math.max(minimum, Math.min(maximum, value));
        }
    }
}
