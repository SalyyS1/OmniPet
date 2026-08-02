package io.github.salyvn.omnipet.paper.render;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class PaperHeadRendererSourceContractTest {
    @Test
    void keepsRoutineMotionVendorFreeAndInteractionNeutral() throws Exception {
        String backend = Files.readString(source("BukkitPaperHeadRendererBackend.java"));
        String renderer = Files.readString(source("PaperHeadRenderer.java"));
        String combined = backend + renderer;

        assertTrue(backend.contains("ArmorStand.class"));
        assertTrue(backend.contains("ItemDisplay.class"));
        assertTrue(backend.contains("Interaction.class"));
        assertTrue(backend.contains("setVelocity"));
        assertTrue(backend.contains("void hardTeleport"));
        assertTrue(renderer.contains("PaperHeadMovementPolicy.decide"));
        assertTrue(renderer.contains("backend.smoothMove"));
        assertFalse(combined.contains("io.lumine"));
        assertFalse(combined.contains("ModelEngine"));
        assertFalse(combined.contains("MMOItems"));
        assertFalse(combined.contains("MythicLib"));
    }

    private static Path source(String file) {
        Path direct = Path.of("src/main/java/io/github/salyvn/omnipet/paper/render").resolve(file);
        return Files.exists(direct) ? direct : Path.of("omnipet-paper").resolve(direct);
    }
}
