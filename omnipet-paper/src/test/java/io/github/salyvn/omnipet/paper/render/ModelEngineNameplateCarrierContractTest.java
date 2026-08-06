package io.github.salyvn.omnipet.paper.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/**
 * A model pet's nameplate is written to its own entity, not to the one ModelEngine hides.
 *
 * <p>The defect this pins down was invisible to every unit test in the suite. Head pets showed a name and
 * model pets did not, while {@code Nameplate.apply} was called correctly at all three sites in both
 * renderers — spawn, per-tick status, and rename. Asserting the name on the carrier passed; the plate still
 * reached nobody.
 *
 * <p>The cause is in ModelEngine's own code. {@code ModeledEntity.setBaseEntityVisible(false)}, which the
 * adapter calls on attach so the placeholder stand does not show through the model, forwards to
 * {@code BukkitEntity.setVisible(false)} — and that sends a despawn packet to every tracking client rather
 * than making the entity transparent. The carrier stops existing client-side, so its {@code customName}
 * data-watcher field is never delivered. Vanilla's {@code setInvisible(true)}, which the head renderer
 * uses, does not do this, which is the whole difference between the two.
 *
 * <p>So the plate needs an entity ModelEngine does not own. It rides the carrier as a passenger, which
 * makes it follow the model for free, and it is invisible the vanilla way, which leaves its plate
 * renderable.
 *
 * <p>Asserted against the source rather than by driving the renderer, because reproducing this needs a real
 * client to observe a packet that was never sent. A stub cannot fail the way the live server did: the test
 * double for {@code ModeledEntity} tracks {@code baseEntityVisible} as a boolean and despawns nothing. That
 * is exactly why this went unnoticed, and asserting on the source is the only check available that would
 * have caught it.
 */
class ModelEngineNameplateCarrierContractTest {
    @Test
    void theNameplateIsNeverWrittenToTheEntityModelEngineDespawns() throws Exception {
        String renderer = code(source("PaperModelEngineRenderer.java"));

        assertTrue(renderer.contains("Nameplate.apply("),
                "a model pet has to get a plate at all");
        assertFalse(renderer.contains("Nameplate.apply(carrier"),
                "the carrier is despawned client-side by ModelEngine, so a plate on it reaches nobody");
        assertFalse(renderer.contains("Nameplate.apply(handle.carrier()"),
                "same reason, on the per-tick and rename paths");
        assertEquals(3, count(renderer, "Nameplate.apply("),
                "spawn, per-tick status, and rename all have to write the plate, or it goes stale");
        assertEquals(3, count(renderer, "Nameplate.apply(plate") + count(renderer, "Nameplate.apply(handle.plate()"),
                "every one of those three writes goes to the plate entity");
    }

    /**
     * The plate entity is not a marker.
     *
     * <p>A marker armor stand has a zero-height bounding box and the plate is drawn above that box, so the
     * name would sit inside the model rather than over it. The carrier is a marker for the opposite reason:
     * nothing is drawn from it at all.
     */
    @Test
    void thePlateStandIsSizedSoTheNameSitsAboveTheModel() throws Exception {
        String renderer = code(source("PaperModelEngineRenderer.java"));
        String spawnPlate = renderer.substring(renderer.indexOf("private static ArmorStand spawnPlate"));

        assertTrue(spawnPlate.contains("setInvisible(true)"),
                "invisible the vanilla way, which unlike ModelEngine's leaves the plate renderable");
        assertFalse(spawnPlate.substring(0, spawnPlate.indexOf('}')).contains("setMarker(true)"),
                "a marker has no bounding box for the plate to sit above");
    }

    /**
     * The plate is cleaned up and re-seated everywhere the interaction is.
     *
     * <p>Both are passengers of the carrier, so they share every hazard: a safety teleport ejects them, and
     * a torn-down renderer has to remove them. Missing the eject leaves a name floating where the pet used
     * to be; missing the removal leaks an armor stand per activation.
     */
    @Test
    void thePlateSharesTheInteractionsLifecycle() throws Exception {
        String renderer = code(source("PaperModelEngineRenderer.java"));

        assertTrue(renderer.contains("addPassenger(handle.plate())"),
                "an ejected plate has to be re-seated, or the name stays where the pet was");
        assertTrue(renderer.contains("handle.plate().teleport(target)"),
                "the plate moves with the carrier on a safety teleport");
        assertTrue(renderer.contains("handle.plate().remove()"),
                "a retired renderer removes its plate, or every activation leaks an armor stand");
        assertTrue(renderer.contains("plate != null) plate.remove()"),
                "a failed spawn removes the plate it already created");
        assertTrue(renderer.contains("!handle.plate().isValid()"),
                "a plate killed out from under the renderer invalidates it like the interaction does");
    }

    private static int count(String haystack, String needle) {
        int found = 0;
        for (int at = haystack.indexOf(needle); at >= 0; at = haystack.indexOf(needle, at + 1)) found++;
        return found;
    }

    /**
     * The file with its comments removed.
     *
     * <p>These assertions are about what the code does, and a comment naming a thing is not the code doing
     * it — the comment explaining why the plate is not on the carrier would otherwise trip the assertion
     * that forbids putting it there.
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
