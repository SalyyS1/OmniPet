package io.github.salyvn.omnipet.paper.render;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class PaperHeadRendererSourceContractTest {
    @Test
    void keepsRoutineMotionVendorFreeAndInteractionNeutral() throws Exception {
        String backend = code(source("BukkitPaperHeadRendererBackend.java"));
        String renderer = code(source("PaperHeadRenderer.java"));
        String combined = backend + renderer;

        assertTrue(backend.contains("ArmorStand.class"));
        assertTrue(backend.contains("ItemDisplay.class"));
        assertTrue(backend.contains("Interaction.class"));
        /*
         * The carrier is moved by teleport, and this asserts it stays that way.
         *
         * It used to assert the opposite -- that `setVelocity` was present -- which pinned the defect in
         * place rather than the behaviour. The carrier is a marker armor stand with gravity disabled, so
         * it has no movement physics to integrate a velocity into: the pet never followed its owner and
         * the only motion anyone saw was the safety teleport firing once the gap grew large enough.
         * Smoothness comes from the display entity's own interpolation, not from carrier physics.
         */
        assertFalse(backend.contains("setVelocity"),
                "the carrier has no physics to integrate a velocity; move it by teleport");
        assertTrue(backend.contains("void hardTeleport"));
        assertTrue(renderer.contains("PaperHeadMovementPolicy.decide"));
        assertTrue(renderer.contains("backend.smoothMove"));
        assertFalse(combined.contains("io.lumine"));
        assertFalse(combined.contains("ModelEngine"));
        assertFalse(combined.contains("MMOItems"));
        assertFalse(combined.contains("MythicLib"));
    }

    /**
     * The file with its comments removed.
     *
     * <p>These assertions are about what the code does, and a comment naming a thing is not the code doing
     * it. Explaining in a comment why the carrier is no longer moved by {@code setVelocity} tripped the
     * very assertion that forbids it, and the vendor-name checks below carried the same latent trap: a
     * comment mentioning ModelEngine would have failed a test about imports.
     */
    private static String code(Path file) throws Exception {
        return Files.readString(file)
                .replaceAll("(?s)/\\*.*?\\*/", "")
                .replaceAll("(?m)//.*$", "");
    }

    private static Path source(String file) {
        Path direct = Path.of("src/main/java/io/github/salyvn/omnipet/paper/render").resolve(file);
        return Files.exists(direct) ? direct : Path.of("omnipet-paper").resolve(direct);
    }
}
