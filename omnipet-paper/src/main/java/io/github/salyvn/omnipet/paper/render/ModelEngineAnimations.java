package io.github.salyvn.omnipet.paper.render;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;

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
public record ModelEngineAnimations(String idle, String walk, String run) {
    public ModelEngineAnimations {
        idle = normalize(idle);
        walk = normalize(walk);
        run = normalize(run);
    }

    public static ModelEngineAnimations defaults() {
        return new ModelEngineAnimations("idle", "walk", "run");
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
        return new ModelEngineAnimations(
                text(animations, "idle", defaults.idle()),
                text(animations, "walk", defaults.walk()),
                text(animations, "run", defaults.run()));
    }

    /** The clip for one gait, or null when that gait should not drive an animation. */
    public String forGait(MovementGait gait) {
        Objects.requireNonNull(gait, "movement gait");
        return switch (gait) {
            case IDLE -> idle;
            case WALK -> walk;
            case RUN -> run;
        };
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
