package io.github.salyvn.omnipet.paper.incubation.placed;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;

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
        Objects.requireNonNull(location, "hologram location");
        Objects.requireNonNull(record, "placed egg record");
        TextDisplay existing = existing(record.key());
        if (existing != null) {
            existing.text(text);
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
