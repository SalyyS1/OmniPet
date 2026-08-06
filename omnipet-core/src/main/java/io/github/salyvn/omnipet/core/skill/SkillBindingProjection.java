package io.github.salyvn.omnipet.core.skill;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * A pet definition's {@code skills:} block, read into typed bindings.
 *
 * <p>Every key is optional except {@code provider} and {@code id}, so the shortest usable entry is two
 * lines and everything else has a default worth having.
 *
 * <pre>
 * skills:
 *   - provider: MYTHICMOBS
 *     id: Fireball
 *     trigger: SHIFT_RIGHT_CLICK
 *     cooldown: 8s
 *     targetPolicy: LOOK_THEN_NEAREST
 *     chance: 1.0
 *     staminaCost: 5
 *     power: 1.5
 *   - provider: MYTHICMOBS
 *     id: EmergencyHeal
 *     trigger: ON_LOW_HEALTH
 *     healthThreshold: 0.35
 *     cooldown: 60s
 *     targetPolicy: OWNER
 * </pre>
 *
 * <p>Durations accept {@code 8s}, {@code 2m}, {@code 500ms}, a bare number of seconds, or ISO-8601
 * {@code PT8S}. Only ISO-8601 was accepted before, which is a format nobody writing a config guesses, so
 * every hand-written cooldown was silently disabling its whole binding.
 *
 * <p>An invalid binding is skipped rather than failing the definition — one bad skill must not cost a pet
 * its model, its stats, and its nameplate. But it is now <em>reported</em> through the sink, because
 * skipping silently is what made a typo indistinguishable from a skill that simply never fired.
 */
public final class SkillBindingProjection {
    public static final int MAX_BINDINGS = 64;

    private SkillBindingProjection() {}

    /** The bindings in a definition, with anything unreadable dropped in silence. */
    public static List<SkillBinding> read(io.github.salyvn.omnipet.core.domain.PetDefinition definition) {
        return read(definition, (index, problem) -> {});
    }

    /**
     * The bindings in a definition, reporting what could not be read.
     *
     * @param problems given the index within {@code skills:} and why that entry was dropped
     */
    public static List<SkillBinding> read(
            io.github.salyvn.omnipet.core.domain.PetDefinition definition,
            BiConsumer<Integer, String> problems) {
        Object raw = definition.rawNode().get("skills");
        if (raw == null) return List.of();
        if (!(raw instanceof List<?> nodes)) {
            problems.accept(-1, "skills: must be a list");
            return List.of();
        }
        List<SkillBinding> result = new ArrayList<>();
        int readable = Math.min(nodes.size(), MAX_BINDINGS);
        if (nodes.size() > MAX_BINDINGS) {
            problems.accept(MAX_BINDINGS, "only the first " + MAX_BINDINGS + " skills are read");
        }
        for (int index = 0; index < readable; index++) {
            if (!(nodes.get(index) instanceof Map<?, ?> node)) {
                problems.accept(index, "entry is not a map");
                continue;
            }
            try {
                result.add(read(node, index));
            } catch (RuntimeException invalid) {
                // Kept on disk for the Studio to repair, disabled at runtime, and named here so an
                // operator can find out why their skill does nothing.
                problems.accept(index, detail(invalid));
            }
        }
        return List.copyOf(result);
    }

    private static SkillBinding read(Map<?, ?> node, int index) {
        String provider = text(node.get("provider"), "provider");
        String skillId = text(node.get("id"), "skill ID");
        String bindingId = node.get("bindingId") instanceof String value && !value.isBlank()
                ? value
                : "skill_" + index;
        SkillTrigger trigger = enumValue(node.get("trigger"), SkillTrigger.class, SkillTrigger.ACTIVE);
        Duration cooldown = duration(node.get("cooldown"), Duration.ZERO, "cooldown");
        double chance = number(node.get("chance"), 1.0);
        double stamina = number(node.get("staminaCost"), 0.0);
        SkillTargetPolicy target = enumValue(
                node.get("targetPolicy"), SkillTargetPolicy.class, SkillTargetPolicy.LOOK_THEN_NEAREST);
        boolean persist = Boolean.TRUE.equals(node.get("persistCooldown"));
        double threshold = number(node.get("healthThreshold"), SkillBinding.DEFAULT_HEALTH_THRESHOLD);
        double range = number(node.get("targetRange"), SkillBinding.DEFAULT_TARGET_RANGE);
        Duration interval = duration(node.get("interval"), SkillBinding.DEFAULT_INTERVAL, "interval");
        double power = number(node.get("power"), 1.0);
        return new SkillBinding(bindingId, provider, skillId, trigger, cooldown, chance, stamina, target,
                persist, threshold, range, interval, power);
    }

    /**
     * A duration written the way an operator would write one.
     *
     * <p>{@code 8s}, {@code 2m}, {@code 500ms}, a bare {@code 8} meaning seconds, or ISO-8601 {@code PT8S}.
     * A plain number is seconds rather than milliseconds because a cooldown is a gameplay number and
     * {@code cooldown: 8} obviously means eight seconds — reading it as 8ms would be technically defensible
     * and useless.
     */
    private static Duration duration(Object value, Duration fallback, String label) {
        if (value == null) return fallback;
        if (value instanceof Number seconds) return fromSeconds(seconds.doubleValue(), label);
        String text = text(value, label).toLowerCase(Locale.ROOT).replace(" ", "");
        try {
            if (text.startsWith("pt") || text.startsWith("p")) return Duration.parse(text.toUpperCase(Locale.ROOT));
            if (text.endsWith("ms")) return Duration.ofMillis(Long.parseLong(text.substring(0, text.length() - 2)));
            if (text.endsWith("s")) return fromSeconds(Double.parseDouble(text.substring(0, text.length() - 1)), label);
            if (text.endsWith("m")) return Duration.ofSeconds(
                    Math.round(Double.parseDouble(text.substring(0, text.length() - 1)) * 60));
            if (text.endsWith("h")) return Duration.ofSeconds(
                    Math.round(Double.parseDouble(text.substring(0, text.length() - 1)) * 3600));
            if (text.endsWith("t")) return Duration.ofMillis(
                    Math.round(Double.parseDouble(text.substring(0, text.length() - 1)) * 50));
            return fromSeconds(Double.parseDouble(text), label);
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException(
                    label + " is not a duration: " + value + " (try 8s, 2m, 500ms, or 20t)");
        }
    }

    private static Duration fromSeconds(double seconds, String label) {
        if (!Double.isFinite(seconds)) throw new IllegalArgumentException(label + " must be finite");
        return Duration.ofMillis(Math.round(seconds * 1000));
    }

    private static String text(Object value, String label) {
        if (!(value instanceof String result) || result.isBlank()) {
            throw new IllegalArgumentException(label + " is required");
        }
        return result.trim();
    }

    private static double number(Object value, double fallback) {
        if (value == null) return fallback;
        if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())) {
            throw new IllegalArgumentException("skill numeric field must be finite");
        }
        return number.doubleValue();
    }

    /**
     * An enum value, named in the failure when it is wrong.
     *
     * <p>{@code Enum.valueOf}'s own message is "No enum constant ...SkillTrigger.SHIFT_RIGHTCLICK", which
     * says what is wrong but not what to write instead — and with twenty triggers, that is the part an
     * operator needs.
     */
    private static <T extends Enum<T>> T enumValue(Object value, Class<T> type, T fallback) {
        if (value == null) return fallback;
        String name = text(value, type.getSimpleName()).toUpperCase(Locale.ROOT).replace('-', '_');
        for (T candidate : type.getEnumConstants()) {
            if (candidate.name().equals(name)) return candidate;
        }
        StringBuilder known = new StringBuilder();
        for (T candidate : type.getEnumConstants()) {
            if (known.length() > 0) known.append(", ");
            known.append(candidate.name());
        }
        throw new IllegalArgumentException(
                "unknown " + type.getSimpleName() + " '" + value + "'; expected one of: " + known);
    }

    private static String detail(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }
}
