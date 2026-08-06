package io.github.salyvn.omnipet.paper.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/**
 * A model pet's nameplate and hitbox are entities of their own, positioned independently.
 *
 * <p>Two defects, one cause, and both invisible to every unit test in the suite.
 *
 * <p>{@code ModeledEntity.setBaseEntityVisible(false)}, which the adapter calls on attach so the placeholder
 * stand does not show through the model, forwards to {@code BukkitEntity.setVisible(false)} — and that sends
 * {@code ClientboundRemoveEntitiesPacket} to every tracking client rather than making the entity
 * transparent. The carrier stops existing client-side. Vanilla's {@code setInvisible(true)}, which the head
 * renderer uses, does not do this, which is the whole difference between the two renderers.
 *
 * <p>The first consequence was a plate written to the carrier reaching nobody. The fix for that put the
 * plate on its own stand — and mounted it on the carrier, which reintroduced the same bug by another route:
 * the server never sends a passenger's position, because the client derives it from the vehicle. With the
 * vehicle despawned client-side there is nothing to derive from. That is also why the interaction entity was
 * unclickable, so an operator could not feed a model pet by clicking it the way a head pet allows.
 *
 * <p>So neither may ride the carrier. Each is teleported on its own every tick.
 *
 * <p>Asserted against the source rather than by driving the renderer, because reproducing this needs a real
 * client to observe a packet that was never sent. A stub cannot fail the way the live server did: the test
 * double for {@code ModeledEntity} tracks {@code baseEntityVisible} as a boolean and despawns nothing, and
 * Bukkit's passenger semantics are not modelled at all. That is exactly why this survived a round of fixes,
 * and asserting on the source is the only check available that would have caught it.
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
     * Nothing rides the carrier.
     *
     * <p>The assertion that pins down the second face of this defect. A passenger's position is never sent
     * to clients — the client computes it from the vehicle — and this vehicle does not exist client-side, so
     * a passenger has no position on screen at all. Mounting is the intuitive way to make one entity follow
     * another, and it is exactly wrong here, which is why this is asserted rather than left to a comment.
     */
    @Test
    void neitherThePlateNorTheHitboxRidesTheCarrier() throws Exception {
        String renderer = code(source("PaperModelEngineRenderer.java"));

        assertFalse(renderer.contains("addPassenger"),
                "a passenger of a client-side-despawned vehicle has no position: teleport each instead");
        assertFalse(renderer.contains(".eject()"),
                "nothing is mounted, so nothing needs ejecting");
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
     * Every entity is moved, cleaned up, and validated on every path.
     *
     * <p>Since none of them ride the carrier, each one the renderer owns has to be moved explicitly. A
     * missed teleport leaves that entity where the pet used to be — the name stranded behind, or a hitbox
     * that can only be clicked at the pet's last position. A missed removal leaks an entity per activation.
     */
    @Test
    void everyOwnedEntityIsMovedAndRetiredTogether() throws Exception {
        String renderer = code(source("PaperModelEngineRenderer.java"));

        assertTrue(renderer.contains("handle.interaction().teleport(destination)"),
                "the hitbox follows the pet every tick, or a model pet can only be clicked where it was");
        assertTrue(renderer.contains("handle.plate().teleport(plateLocation(destination))"),
                "so does the name");
        assertTrue(renderer.contains("handle.plate().teleport(plateLocation(target))"),
                "and both move on a safety teleport as well");
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
