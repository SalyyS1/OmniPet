package io.github.salyvn.omnipet.paper.render;

import java.lang.reflect.Method;
import java.util.Objects;

/**
 * ModelEngine's animation methods, bound separately from the ones the renderer cannot work without.
 *
 * <p>Deliberately its own class. {@link ReflectiveModelEngineBindings} binds eagerly in its constructor
 * and a failure there quarantines the whole renderer, which is right for {@code createModeledEntity} — a
 * pet cannot be shown without it. It is wrong for animation: a build whose animation signature moved
 * would cost every ModelEngine pet its model rather than just its clips. Binding here fails soft, so the
 * pet still renders and only the animation is missing.
 *
 * <p>Playing a clip is also best-effort at runtime. A definition naming a clip its model does not have is
 * an authoring mistake that must not throw once per tick, so a failure disables animation for this
 * renderer after reporting it once.
 */
final class ReflectiveModelEngineAnimationBindings {
    private final Method animationHandler;
    private final Method playAnimation;
    private final Method stopAnimation;
    private final String unavailable;
    private String failed;

    ReflectiveModelEngineAnimationBindings(ClassLoader loader) {
        Method handler = null;
        Method play = null;
        Method stop = null;
        String detail = null;
        try {
            Class<?> active = Class.forName(
                    "com.ticxo.modelengine.api.model.ActiveModel", true, loader);
            Class<?> handlerType = Class.forName(
                    "com.ticxo.modelengine.api.animation.handler.AnimationHandler", true, loader);
            handler = active.getMethod("getAnimationHandler");
            // (animation, lerpIn, lerpOut, speed, force) — the long-standing shape of this call.
            play = handlerType.getMethod(
                    "playAnimation", String.class, double.class, double.class, double.class, boolean.class);
            stop = handlerType.getMethod("stopAnimation", String.class);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            detail = failure.getClass().getSimpleName()
                    + (failure.getMessage() == null ? "" : ": " + failure.getMessage());
        }
        animationHandler = handler;
        playAnimation = play;
        stopAnimation = stop;
        unavailable = detail;
    }

    /** Whether clips can be driven at all on this server's build. */
    boolean available() {
        return unavailable == null && failed == null;
    }

    /** Why animation is unavailable, or null when it is available. */
    String unavailableDetail() {
        return failed != null ? failed : unavailable;
    }

    /**
     * Switches the model to {@code animation}, stopping {@code previous} first.
     *
     * @return true when the clip was driven; false once animation has been given up on
     */
    boolean play(Object activeModel, String previous, String animation) {
        Objects.requireNonNull(activeModel, "active model");
        Objects.requireNonNull(animation, "animation name");
        if (!available()) return false;
        try {
            Object handler = animationHandler.invoke(activeModel);
            if (handler == null) {
                failed = "ModelEngine returned no animation handler";
                return false;
            }
            if (previous != null && !previous.equals(animation)) {
                stopAnimation.invoke(handler, previous);
            }
            // force=true so a gait change takes effect immediately rather than queueing behind a clip
            // that may loop forever.
            playAnimation.invoke(handler, animation, 0.2, 0.2, 1.0, true);
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            // Reported once by the caller, then never retried: a per-tick throw would flood the log for
            // what is almost always a misnamed clip in a pet definition.
            failed = failure.getClass().getSimpleName()
                    + (failure.getMessage() == null ? "" : ": " + failure.getMessage());
            return false;
        }
    }
}
