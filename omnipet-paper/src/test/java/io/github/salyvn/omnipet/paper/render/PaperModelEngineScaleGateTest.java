package io.github.salyvn.omnipet.paper.render;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * The ModelEngine renderer must not rewrite an unchanged scale every tick.
 *
 * <p>Two different costs hide behind one call site: {@code setScale} is a reflective invoke, and the
 * interaction's width and height are data-watcher fields, so re-applying the same scale sends a metadata
 * packet per pet per tick to every nearby player and changes nothing on screen. The built-in renderer was
 * gated for exactly this reason; this adapter was not, so it kept paying.
 *
 * <p>Asserted against the source because {@link PaperModelEngineRenderer} cannot be constructed without
 * ModelEngine on the classpath, and it is not on the test classpath by design.
 */
class PaperModelEngineScaleGateTest {
    @Test
    void anUnchangedScaleIsNotReappliedEveryTick() throws Exception {
        String source = source("PaperModelEngineRenderer.java");

        // Both writes have to sit behind the gate, not just the reflective one.
        Pattern gated = Pattern.compile(
                "if \\(handle\\.scaleChanged\\(transform\\)\\) \\{.*?bindings\\.scale\\(.*?"
                        + "setInteractionScale\\(.*?\\n\\s*\\}",
                Pattern.DOTALL);
        assertTrue(gated.matcher(source).find(),
                "bindings.scale and setInteractionScale must both be gated on a real scale change");
    }

    @Test
    void theGateComparesTheScaleItWasLastGiven() {
        String source = sourceOrEmpty("ModelEngineRendererHandle.java");

        assertTrue(source.contains("boolean scaleChanged(RuntimeTransform next)"));
        // A null previous transform must report changed, or the first update after a spawn would skip the
        // write that establishes the model's size.
        assertTrue(source.contains("transform == null"),
                "the first update after a spawn must still apply the scale");
    }

    @Test
    void theBuiltInRendererKeepsItsOwnGate() throws Exception {
        // Guarding against a fix that moves the gate here and quietly drops it there. Only the update path
        // is gated: spawn and appearance changes must write the scale unconditionally, because there is no
        // previous value to compare against.
        String source = source("PaperHeadRenderer.java");

        Pattern gatedUpdate = Pattern.compile(
                "if \\(handle\\.displayTransformChanged\\(transform\\)\\) \\{\\s*"
                        + "backend\\.updateScale\\(handle\\.visual\\(\\), handle\\.interaction\\(\\), transform, settings\\);",
                Pattern.DOTALL);
        assertTrue(gatedUpdate.matcher(source).find(),
                "the per-tick display transform write must stay behind displayTransformChanged");
    }

    private static String source(String name) throws Exception {
        Path relative = Path.of("src/main/java/io/github/salyvn/omnipet/paper/render").resolve(name);
        Path path = Files.exists(relative) ? relative : Path.of("omnipet-paper").resolve(relative);
        return Files.readString(path);
    }

    private static String sourceOrEmpty(String name) {
        try {
            return source(name);
        } catch (Exception unreadable) {
            throw new AssertionError("could not read " + name, unreadable);
        }
    }
}
