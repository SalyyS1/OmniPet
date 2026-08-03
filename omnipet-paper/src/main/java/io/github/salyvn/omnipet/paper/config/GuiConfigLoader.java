package io.github.salyvn.omnipet.paper.config;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Parses the optional {@code gui:} section leniently.
 *
 * <p>Deliberately not the strict {@code storage:} path. {@link OmniPetConfigLoader}'s
 * {@code rejectUnknown} throws on any unexpected key, which is right for storage — a misread slot
 * limit corrupts data. It is wrong here: a typo in a page size or a sound name would stop players
 * using their pets over a display concern. So every problem in this section warns and falls back to
 * the default value for that key alone.
 */
public final class GuiConfigLoader {
    private static final Set<String> ROOT = Set.of("feedback", "vault", "help", "studio");
    private static final Set<String> FEEDBACK =
            Set.of("enabled", "actionBar", "minIntervalMillis", "success", "failure", "blocked", "progress");
    private static final Set<String> CUE = Set.of("sound", "volume", "pitch");

    /** Parses {@code gui:}. An absent or unusable section yields {@link GuiConfig#defaults()}. */
    public GuiConfig parse(Object section, Consumer<String> warnings) {
        Consumer<String> warn = warnings == null ? message -> {} : warnings;
        GuiConfig defaults = GuiConfig.defaults();
        if (section == null) return defaults;
        Map<String, Object> values = map(section, "gui", warn);
        if (values.isEmpty()) return defaults;
        warnUnknown(values, ROOT, "gui", warn);

        GuiConfig.Feedback feedback = feedback(values.get("feedback"), defaults.feedback(), warn);
        int petsPerPage = integer(
                map(values.get("vault"), "gui.vault", warn).get("petsPerPage"),
                defaults.vaultPetsPerPage(), "gui.vault.petsPerPage", warn);
        int linesPerPage = integer(
                map(values.get("help"), "gui.help", warn).get("linesPerPage"),
                defaults.helpLinesPerPage(), "gui.help.linesPerPage", warn);
        long promptSeconds = integer(
                map(values.get("studio"), "gui.studio", warn).get("promptTimeoutSeconds"),
                (int) defaults.studioPromptTimeout().toSeconds(), "gui.studio.promptTimeoutSeconds", warn);
        if (promptSeconds <= 0) {
            warn.accept("gui.studio.promptTimeoutSeconds must be positive; using "
                    + defaults.studioPromptTimeout().toSeconds());
            promptSeconds = defaults.studioPromptTimeout().toSeconds();
        }

        warnClamp(petsPerPage, 1, GuiConfig.MAX_VAULT_PETS_PER_PAGE, "gui.vault.petsPerPage", warn);
        warnClamp(linesPerPage, 1, GuiConfig.MAX_HELP_LINES_PER_PAGE, "gui.help.linesPerPage", warn);
        // The record clamps; warning here keeps the operator informed of what was actually applied.
        return new GuiConfig(feedback, petsPerPage, linesPerPage, Duration.ofSeconds(promptSeconds));
    }

    /** Serialises the section so a legacy migration round-trip cannot silently drop it. */
    public Map<String, Object> encode(GuiConfig config) {
        LinkedHashMap<String, Object> feedback = new LinkedHashMap<>();
        feedback.put("enabled", config.feedback().enabled());
        feedback.put("minIntervalMillis", config.feedback().minimumInterval().toMillis());
        feedback.put("actionBar", config.feedback().actionBar());
        feedback.put("success", cue(config.feedback().success()));
        feedback.put("failure", cue(config.feedback().failure()));
        feedback.put("blocked", cue(config.feedback().blocked()));
        feedback.put("progress", cue(config.feedback().progress()));
        LinkedHashMap<String, Object> root = new LinkedHashMap<>();
        root.put("feedback", feedback);
        root.put("vault", Map.of("petsPerPage", config.vaultPetsPerPage()));
        root.put("help", Map.of("linesPerPage", config.helpLinesPerPage()));
        root.put("studio", Map.of("promptTimeoutSeconds", config.studioPromptTimeout().toSeconds()));
        return root;
    }

    private static Map<String, Object> cue(GuiConfig.Cue cue) {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        values.put("sound", cue.sound());
        values.put("volume", (double) cue.volume());
        values.put("pitch", (double) cue.pitch());
        return values;
    }

    private GuiConfig.Feedback feedback(Object section, GuiConfig.Feedback defaults, Consumer<String> warn) {
        Map<String, Object> values = map(section, "gui.feedback", warn);
        if (values.isEmpty()) return defaults;
        warnUnknown(values, FEEDBACK, "gui.feedback", warn);
        long interval = integer(values.get("minIntervalMillis"),
                (int) defaults.minimumInterval().toMillis(), "gui.feedback.minIntervalMillis", warn);
        if (interval < 0) {
            warn.accept("gui.feedback.minIntervalMillis cannot be negative; using "
                    + defaults.minimumInterval().toMillis());
            interval = defaults.minimumInterval().toMillis();
        }
        return new GuiConfig.Feedback(
                bool(values.get("enabled"), defaults.enabled(), "gui.feedback.enabled", warn),
                bool(values.get("actionBar"), defaults.actionBar(), "gui.feedback.actionBar", warn),
                Duration.ofMillis(interval),
                cue(values.get("success"), defaults.success(), "gui.feedback.success", warn),
                cue(values.get("failure"), defaults.failure(), "gui.feedback.failure", warn),
                cue(values.get("blocked"), defaults.blocked(), "gui.feedback.blocked", warn),
                cue(values.get("progress"), defaults.progress(), "gui.feedback.progress", warn));
    }

    private GuiConfig.Cue cue(Object section, GuiConfig.Cue defaults, String path, Consumer<String> warn) {
        Map<String, Object> values = map(section, path, warn);
        if (values.isEmpty()) return defaults;
        warnUnknown(values, CUE, path, warn);
        Object rawSound = values.get("sound");
        String sound = defaults.sound();
        if (rawSound instanceof String text && !text.isBlank()) {
            sound = text;
        } else if (rawSound != null) {
            warn.accept(path + ".sound must be text; using " + defaults.sound());
        }
        return new GuiConfig.Cue(
                sound,
                decimal(values.get("volume"), defaults.volume(), path + ".volume", warn),
                decimal(values.get("pitch"), defaults.pitch(), path + ".pitch", warn));
    }

    private static Map<String, Object> map(Object value, String path, Consumer<String> warn) {
        if (value == null) return Map.of();
        if (!(value instanceof Map<?, ?> source)) {
            warn.accept(path + " must be a map; ignoring it and using defaults");
            return Map.of();
        }
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, nested) -> result.put(String.valueOf(key), nested));
        return result;
    }

    private static void warnUnknown(
            Map<String, Object> values, Set<String> allowed, String path, Consumer<String> warn) {
        values.keySet().stream().filter(key -> !allowed.contains(key))
                .forEach(key -> warn.accept("unknown config key " + path + "." + key + " was ignored"));
    }

    private static void warnClamp(int value, int minimum, int maximum, String path, Consumer<String> warn) {
        if (value < minimum) warn.accept(path + " was raised to " + minimum + " from " + value);
        if (value > maximum) warn.accept(path + " was capped at " + maximum + " from " + value);
    }

    private static boolean bool(Object value, boolean fallback, String path, Consumer<String> warn) {
        if (value == null) return fallback;
        if (value instanceof Boolean result) return result;
        if (value instanceof String text) {
            String normalised = text.trim().toLowerCase(Locale.ROOT);
            if (normalised.equals("true") || normalised.equals("false")) return Boolean.parseBoolean(normalised);
        }
        warn.accept(path + " must be true or false; using " + fallback);
        return fallback;
    }

    private static int integer(Object value, int fallback, String path, Consumer<String> warn) {
        if (value == null) return fallback;
        if (value instanceof Number number) {
            double raw = number.doubleValue();
            if (Double.isFinite(raw) && raw >= Integer.MIN_VALUE && raw <= Integer.MAX_VALUE) {
                return (int) raw;
            }
        }
        warn.accept(path + " must be a whole number; using " + fallback);
        return fallback;
    }

    private static float decimal(Object value, float fallback, String path, Consumer<String> warn) {
        if (value == null) return fallback;
        if (value instanceof Number number && Double.isFinite(number.doubleValue())) {
            return number.floatValue();
        }
        warn.accept(path + " must be a number; using " + fallback);
        return fallback;
    }
}
