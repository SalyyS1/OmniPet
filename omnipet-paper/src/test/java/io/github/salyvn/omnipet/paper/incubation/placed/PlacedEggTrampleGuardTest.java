package io.github.salyvn.omnipet.paper.incubation.placed;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/**
 * A placed egg may only be removed by a player breaking it.
 *
 * <p>The shipped egg block is a turtle egg, and vanilla destroys a turtle egg when an entity walks over
 * it. That arrived as a plain block change rather than a break, so nothing returned the egg and nothing
 * deleted the record: the countdown ran on against a block that no longer existed, the hologram floated
 * over air, and the egg could never be reclaimed. Its owner lost it outright.
 *
 * <p>Pistons, block physics, and explosions reach the same block the same way. Each is refused, because a
 * placed egg is a durable record with an owner rather than scenery.
 *
 * <p>The guards themselves need Bukkit events, so their wiring is asserted at the source level — the same
 * approach the incubation and renderer contract tests already use for behaviour a unit test cannot reach
 * without a running server. The material predicate underneath them is pure and is exercised directly.
 */
class PlacedEggTrampleGuardTest {
    @Test
    void everyVanillaPathThatDestroysAnEggIsHandled() throws Exception {
        String listener = source("PlacedEggListener.java");

        // Trampling is the one the report was about: walking over a turtle egg removes it in vanilla.
        assertTrue(listener.contains("EntityChangeBlockEvent"),
                "an entity walking onto a turtle egg destroys it and must be refused");
        // The rest reach the same block by other routes and would orphan the record identically.
        assertTrue(listener.contains("BlockPhysicsEvent"), "the block-update path must be refused");
        assertTrue(listener.contains("EntityExplodeEvent"), "an entity explosion must not take the egg");
        assertTrue(listener.contains("BlockExplodeEvent"), "a block explosion must not take the egg");
        assertTrue(listener.contains("BlockPistonExtendEvent"), "a piston must not push the egg");
        assertTrue(listener.contains("BlockPistonRetractEvent"), "a piston must not pull the egg");

        // Breaking still works and still returns the exact egg: that is the intended way to collect it.
        assertTrue(listener.contains("BlockBreakEvent"));
        assertTrue(listener.contains("coordinator.reclaim"));
    }

    @Test
    void aFinishedEggClearsItsOwnBlock() throws Exception {
        // The other half of the report: the timer finished, the pet arrived, and the egg block stayed in
        // the world looking like an egg that had stopped incubating. Worse, breaking that orphan dropped a
        // vanilla turtle egg, because the no-drop path only covers blocks that still have a record.
        String view = source("PlacedEggView.java");

        assertTrue(view.contains("clearBlock"), "consuming a record must also clear its block");
        assertTrue(view.contains("Material.AIR"), "the block is set back to air");
        // Guarded, not unconditional: a block someone has since replaced is not ours to delete.
        assertTrue(view.contains("PlacedEggBlocks.isEggBlock"),
                "the block is only cleared when it is still an egg");
    }

    @Test
    void onlyEggShapedBlocksCountAsOurs() {
        // The predicate decides whether a block at a recorded position is still the egg. Permissive here
        // and the cleanup would delete whatever a player had put in its place.
        assertTrue(PlacedEggBlocks.eggMaterial(org.bukkit.Material.TURTLE_EGG));
        assertTrue(PlacedEggBlocks.eggMaterial(org.bukkit.Material.DRAGON_EGG));
        assertTrue(PlacedEggBlocks.eggMaterial(org.bukkit.Material.SNIFFER_EGG));

        assertFalse(PlacedEggBlocks.eggMaterial(org.bukkit.Material.STONE));
        assertFalse(PlacedEggBlocks.eggMaterial(org.bukkit.Material.CHEST),
                "a chest a player put where their egg was must never be deleted");
        assertFalse(PlacedEggBlocks.eggMaterial(org.bukkit.Material.AIR));
        assertFalse(PlacedEggBlocks.eggMaterial(null));
    }

    @Test
    void onlyTheTramplableMaterialTriggersTheTrampleGuard() {
        // Kept narrower than eggMaterial on purpose. Only turtle eggs are destroyed by being stood on, so
        // blanket-cancelling every interaction with every egg-shaped block would refuse things vanilla
        // allows and that no report asked for.
        assertTrue(PlacedEggBlocks.tramplableMaterial(org.bukkit.Material.TURTLE_EGG));

        assertFalse(PlacedEggBlocks.tramplableMaterial(org.bukkit.Material.DRAGON_EGG),
                "a dragon egg is not destroyed by being walked on");
        assertFalse(PlacedEggBlocks.tramplableMaterial(org.bukkit.Material.STONE));
        assertFalse(PlacedEggBlocks.tramplableMaterial(null));
    }

    private static String source(String file) throws Exception {
        Path direct = Path.of("src/main/java/io/github/salyvn/omnipet/paper/incubation/placed").resolve(file);
        Path path = Files.exists(direct) ? direct : Path.of("omnipet-paper").resolve(direct);
        return Files.readString(path)
                // Comments explain the bug by name, and a comment naming an event is not a handler for it.
                .replaceAll("(?s)/\\*.*?\\*/", "")
                .replaceAll("(?m)//.*$", "");
    }
}
