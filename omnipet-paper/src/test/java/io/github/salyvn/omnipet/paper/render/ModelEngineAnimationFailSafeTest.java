package io.github.salyvn.omnipet.paper.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import io.github.salyvn.omnipet.core.runtime.MovementGait;

/**
 * Animation must never be able to cost a pet its model.
 *
 * <p>The core bindings are bound eagerly and a failure there quarantines the renderer, which is correct:
 * without {@code createModeledEntity} there is nothing to show. Applying that rule to animation would mean
 * a ModelEngine build that renamed one animation method took every pet's model with it. These tests pin
 * the separation, since it is a source-level property that no runtime test can reach without the vendor
 * plugin on the classpath.
 */
class ModelEngineAnimationFailSafeTest {
    @Test
    void animationIsBoundSeparatelyFromTheBindingsThatQuarantineTheRenderer() throws Exception {
        String core = source("ReflectiveModelEngineBindings.java");

        // The eager constructor is what quarantines the renderer, so nothing animation-related may be
        // resolved inside it.
        for (String animationApi : java.util.List.of(
                "getAnimationHandler", "playAnimation", "stopAnimation", "AnimationHandler")) {
            assertFalse(core.contains(animationApi),
                    animationApi + " is bound eagerly; a signature change would quarantine the renderer");
        }
    }

    @Test
    void theAnimationBindingsSwallowTheirOwnFailureInsteadOfThrowing() throws Exception {
        String animation = source("ReflectiveModelEngineAnimationBindings.java");

        assertTrue(animation.contains("catch (ReflectiveOperationException | RuntimeException | LinkageError"),
                "binding must catch the same failure set the renderer treats as fatal");
        assertFalse(animation.contains("throws ReflectiveOperationException"),
                "a binding failure must not propagate: the pet still has to render");
    }

    @Test
    void aBindingFailureLeavesAWorkingRendererThatReportsNoAnimation() {
        // No ModelEngine on the test classpath, so every animation class is missing. That is exactly the
        // shape of the failure this must survive.
        ReflectiveModelEngineAnimationBindings bindings =
                new ReflectiveModelEngineAnimationBindings(getClass().getClassLoader());

        assertFalse(bindings.available());
        assertNotNull(bindings.unavailableDetail(), "an operator has to be able to see why clips are off");
        assertFalse(bindings.play(new Object(), null, "walk"),
                "playing must report failure rather than throw into the tick loop");
    }

    @Test
    void capabilitiesReportAnimationSeparatelyFromModelSupport() {
        // A caller must be able to tell "renders a model" from "can animate it": the built-in renderer
        // does neither, and a ModelEngine instance may do the first without the second.
        assertFalse(io.github.salyvn.omnipet.core.runtime.RendererCapabilities.head().animation());
        assertFalse(new io.github.salyvn.omnipet.core.runtime.RendererCapabilities(true, false, true, true)
                .animation(), "the four-argument form must default to no animation");
        assertTrue(new io.github.salyvn.omnipet.core.runtime.RendererCapabilities(true, false, true, true, true)
                .animation());
    }

    @Test
    void clipNamesFallBackPerGaitRatherThanAllOrNothing() {
        ModelEngineAnimations defaults = ModelEngineAnimations.defaults();
        assertEquals("idle", defaults.forGait(MovementGait.IDLE));
        assertEquals("walk", defaults.forGait(MovementGait.WALK));
        assertEquals("run", defaults.forGait(MovementGait.RUN));

        ModelEngineAnimations partial = ModelEngineAnimations.from(java.util.Map.of(
                "behavior", java.util.Map.of("animations", java.util.Map.of("run", "sprint"))));
        assertEquals("sprint", partial.forGait(MovementGait.RUN));
        assertEquals("idle", partial.forGait(MovementGait.IDLE), "an unset gait keeps its default");

        // A blank name is how a model with only an idle loop avoids being asked for a walk clip.
        ModelEngineAnimations disabled = ModelEngineAnimations.from(java.util.Map.of(
                "behavior", java.util.Map.of("animations", java.util.Map.of("walk", "  "))));
        assertEquals(null, disabled.forGait(MovementGait.WALK));

        // Cosmetic and therefore lenient: a wrong type must not refuse the whole definition.
        ModelEngineAnimations wrongType = ModelEngineAnimations.from(java.util.Map.of(
                "behavior", java.util.Map.of("animations", java.util.Map.of("idle", 42))));
        assertEquals("idle", wrongType.forGait(MovementGait.IDLE));

        assertEquals(defaults, ModelEngineAnimations.from(java.util.Map.of()));
        assertEquals(defaults, ModelEngineAnimations.from(null));
    }

    private static String source(String name) throws Exception {
        Path path = Path.of("src/main/java/io/github/salyvn/omnipet/paper/render").resolve(name);
        return Files.readString(Files.exists(path) ? path : Path.of("omnipet-paper").resolve(path));
    }
}
