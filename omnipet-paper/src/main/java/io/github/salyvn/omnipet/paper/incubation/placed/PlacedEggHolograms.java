package io.github.salyvn.omnipet.paper.incubation.placed;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;

import org.joml.AxisAngle4f;

import net.kyori.adventure.text.Component;

/**
 * The floating countdown above a placed egg.
 *
 * <p>Uses Paper's {@link TextDisplay}, already on the compile classpath, so no hologram library enters the
 * distribution.
 *
 * <p>Displays are never the source of truth. Each one is derived from a {@link PlacedEggRecord} and can be
 * destroyed and rebuilt at any time, which is what makes a crash or a chunk unload harmless: nothing is
 * lost, because nothing was stored here. Entities are tracked by record key so a rebuild replaces rather
 * than stacks a second hologram on the same egg.
 */
public final class PlacedEggHolograms {
    private final Map<String, UUID> displays = new ConcurrentHashMap<>();

    /** Creates or updates the hologram for one record. Must run on the main thread. */
    public void show(Location location, PlacedEggRecord record, Component text) {
        show(location, record, text, 0);
    }

    /**
     * Creates or updates the hologram, rocking it by {@code shakeDegrees}.
     *
     * <p>The egg itself is a world block and cannot be moved, so the hologram carries the anticipation.
     * Rotation goes through the display's own transformation rather than the entity's rotation, because a
     * {@code CENTER} billboard ignores entity yaw — it always faces the viewer, which is what makes the
     * countdown readable from any angle and also what makes {@code setRotation} do nothing here.
     */
    public void show(Location location, PlacedEggRecord record, Component text, double shakeDegrees) {
        Objects.requireNonNull(location, "hologram location");
        Objects.requireNonNull(record, "placed egg record");
        TextDisplay existing = existing(record.key());
        if (existing != null) {
            existing.text(text);
            applyShake(existing, shakeDegrees);
            return;
        }
        if (location.getWorld() == null) return;
        TextDisplay display = location.getWorld().spawn(location, TextDisplay.class, spawned -> {
            spawned.text(text);
            spawned.setBillboard(Display.Billboard.CENTER);
            spawned.setSeeThrough(false);
            // Nothing may treat a hologram as a real entity: it carries no state and must not be
            // pushed, damaged, or persisted into the world save as something to restore.
            spawned.setPersistent(false);
            spawned.setInvulnerable(true);
        });
        displays.put(record.key(), display.getUniqueId());
        applyShake(display, shakeDegrees);
    }

    /**
     * Tilts the display, or restores it upright.
     *
     * <p>Skipped when the angle has not visibly changed. A transformation is a tracked field, so rewriting
     * an identical one costs a packet per egg per pass to every nearby player and shows nothing new — the
     * same trap the pet renderers were carrying.
     */
    private void applyShake(TextDisplay display, double shakeDegrees) {
        float radians = (float) Math.toRadians(shakeDegrees);
        Transformation current = display.getTransformation();
        // Read back as a quaternion, so the comparison converts rather than assuming the stored form.
        AxisAngle4f rolled = new AxisAngle4f(radians, 0, 0, 1);
        AxisAngle4f existing = new AxisAngle4f().set(current.getLeftRotation());
        // Skipped when nothing visibly changed: a transformation is a tracked field, so rewriting an
        // identical one costs a packet per egg per pass to every nearby player and shows nothing new.
        if (sameRoll(existing, rolled)) return;
        display.setTransformation(new Transformation(
                current.getTranslation(),
                // Rolled around the viewing axis: a billboard always faces the viewer, so this is the one
                // rotation that reads as rocking rather than as the text turning away.
                rolled,
                current.getScale(),
                new AxisAngle4f().set(current.getRightRotation())));
    }

    /** Whether two rolls are close enough that redrawing would show the viewer nothing new. */
    private static boolean sameRoll(AxisAngle4f existing, AxisAngle4f next) {
        // A zero angle has an arbitrary axis, so compare the signed angle about z rather than the axis.
        float existingRoll = existing.z < 0 ? -existing.angle : existing.angle;
        float nextRoll = next.z < 0 ? -next.angle : next.angle;
        return Math.abs(existingRoll - nextRoll) < 0.002f;
    }

    /** Removes the hologram for one record, if it still exists. */
    public void hide(String recordKey) {
        UUID id = displays.remove(recordKey);
        if (id == null) return;
        TextDisplay display = lookup(id);
        if (display != null) display.remove();
    }

    /** Removes every hologram this manager created, for a clean disable. */
    public void hideAll() {
        displays.keySet().forEach(this::hide);
        displays.clear();
    }

    private TextDisplay existing(String recordKey) {
        UUID id = displays.get(recordKey);
        if (id == null) return null;
        TextDisplay display = lookup(id);
        // A display can vanish with its chunk; forgetting the stale id lets show() respawn it.
        if (display == null) displays.remove(recordKey);
        return display;
    }

    private static TextDisplay lookup(UUID id) {
        if (org.bukkit.Bukkit.getServer() == null) return null;
        return org.bukkit.Bukkit.getEntity(id) instanceof TextDisplay display && display.isValid()
                ? display
                : null;
    }
}
