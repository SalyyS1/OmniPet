package io.github.salyvn.omnipet.paper.render;

import java.util.EnumMap;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import io.github.salyvn.omnipet.core.runtime.IdleBehaviour;
import io.github.salyvn.omnipet.core.runtime.MovementGait;

/**
 * Which ModelEngine clip each gait plays.
 *
 * <p>Defaults match the names Blockbench rigs conventionally use, so a typical model animates with no
 * configuration. A definition overrides any of them under {@code behavior.animations}; naming a clip the
 * model does not have costs that gait its animation and nothing else.
 *
 * <p>A blank name disables animation for that gait, which is how an operator keeps a model that only has
 * an idle loop from being asked for a walk clip it does not contain.
 */
public record ModelEngineAnimations(
        String idle, String walk, String run, String rest,
        Map<IdleBehaviour.OneShot, String> flourishes) {
    public ModelEngineAnimations {
        idle = normalize(idle);
        rest = normalize(rest);
        walk = normalize(walk);
        run = normalize(run);
        flourishes = copyFlourishes(flourishes);
    }

    public static ModelEngineAnimations defaults() {
        // "sit" is the conventional Blockbench name for a settled pose; a model without it simply
        // keeps its idle loop, because forGait falls back rather than asking for a missing clip.
        return new ModelEngineAnimations("idle", "walk", "run", "sit", defaultFlourishes());
    }

    /**
     * Reads {@code behavior.animations} from a definition's raw node.
     *
     * <p>Lenient, like the rest of the optional behavior block: an unusable value falls back to the
     * default for that gait rather than refusing the definition, because a missing animation is cosmetic.
     */
    public static ModelEngineAnimations from(Map<String, Object> rawNode) {
        Map<?, ?> animations = nested(nested(rawNode, "behavior"), "animations");
        ModelEngineAnimations defaults = defaults();
        if (animations == null) return defaults;
        Map<IdleBehaviour.OneShot, String> flourishes =
                new EnumMap<>(IdleBehaviour.OneShot.class);
        for (IdleBehaviour.OneShot shot : IdleBehaviour.OneShot.values()) {
            flourishes.put(shot, text(animations, key(shot), defaults.flourishes().get(shot)));
        }
        return new ModelEngineAnimations(
                text(animations, "idle", defaults.idle()),
                text(animations, "walk", defaults.walk()),
                text(animations, "run", defaults.run()),
                text(animations, "rest", defaults.rest()),
                flourishes);
    }

    /** The clip for one gait, or null when that gait should not drive an animation. */
    public String forGait(MovementGait gait) {
        Objects.requireNonNull(gait, "movement gait");
        return switch (gait) {
            case IDLE -> idle;
            // A model with no rest clip keeps standing there rather than losing its animation entirely.
            case REST -> rest == null ? idle : rest;
            case WALK -> walk;
            case RUN -> run;
        };
    }

    /**
     * The clip for an idle flourish, or null when this model has none for it.
     *
     * <p>Null rather than a fallback on purpose: an unmapped flourish must leave the looping idle clip
     * playing. Substituting the idle clip here would restart the loop and make a settled pet twitch.
     */
    public String forFlourish(IdleBehaviour.OneShot flourish) {
        return flourish == null ? null : flourishes.get(flourish);
    }

    /** The {@code behavior.animations} key an operator writes to override one flourish. */
    private static String key(IdleBehaviour.OneShot flourish) {
        return flourish.name().toLowerCase(Locale.ROOT);
    }

    private static Map<IdleBehaviour.OneShot, String> defaultFlourishes() {
        Map<IdleBehaviour.OneShot, String> defaults = new EnumMap<>(IdleBehaviour.OneShot.class);
        for (IdleBehaviour.OneShot shot : IdleBehaviour.OneShot.values()) defaults.put(shot, key(shot));
        return defaults;
    }

    private static Map<IdleBehaviour.OneShot, String> copyFlourishes(
            Map<IdleBehaviour.OneShot, String> source) {
        Map<IdleBehaviour.OneShot, String> copy = new EnumMap<>(IdleBehaviour.OneShot.class);
        if (source != null) {
            source.forEach((shot, clip) -> {
                String normalized = normalize(clip);
                if (shot != null && normalized != null) copy.put(shot, normalized);
            });
        }
        return Collections.unmodifiableMap(copy);
    }

    private static String normalize(String value) {
        if (value == null) return null;
        String trimmed = value.trim().toLowerCase(Locale.ROOT);
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static Map<?, ?> nested(Map<?, ?> source, String key) {
        Object value = source == null ? null : source.get(key);
        return value instanceof Map<?, ?> map ? map : null;
    }

    private static String text(Map<?, ?> source, String key, String fallback) {
        Object value = source.get(key);
        if (value == null) return fallback;
        // An explicit empty string means "this gait has no clip", which is not the same as absent.
        return value instanceof String string ? string : fallback;
    }
}
