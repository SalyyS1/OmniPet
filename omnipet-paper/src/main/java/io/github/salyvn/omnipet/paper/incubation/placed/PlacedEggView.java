package io.github.salyvn.omnipet.paper.incubation.placed;

import java.util.Objects;
import java.util.UUID;
import java.util.function.BiConsumer;

import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import io.github.salyvn.omnipet.paper.text.Durations;
import io.github.salyvn.omnipet.paper.text.MessageKey;
import io.github.salyvn.omnipet.paper.text.Messages;

/**
 * Advances every placed egg on one bounded task and keeps its hologram in step.
 *
 * <p>One task for all placed eggs, matching the pet runtime's design: there is no task per egg, so a
 * hundred placed eggs cost one scheduled job rather than a hundred. Each pass credits real elapsed time
 * rather than assuming the period was met, so a lagging server does not silently accelerate hatching.
 *
 * <p>Holograms are rebuilt from records here, never persisted, so a restart or chunk reload restores them
 * from the only source of truth there is.
 */
public final class PlacedEggView {
    /** One second: fine enough for a visible countdown, coarse enough to stay cheap. */
    private static final long PERIOD_TICKS = 20L;

    private final JavaPlugin plugin;
    private final PlacedEggCoordinator coordinator;
    private final PlacedEggHolograms holograms;
    private final BiConsumer<UUID, PlacedEggRecord> onReady;
    /**
     * How often a moved countdown is written, in nanoseconds.
     *
     * <p>Thirty seconds. A crash loses at most this much incubation progress, which is the deliberate
     * trade for not fsyncing per egg per second. The record itself — the player's item — is written the
     * moment the egg is placed and is never at risk, and an egg reaching ready flushes immediately.
     */
    private static final long FLUSH_INTERVAL_NANOS = 30_000_000_000L;

    private BukkitTask task;
    private long lastPassNanos;
    private long lastFlushNanos;

    public PlacedEggView(
            JavaPlugin plugin,
            PlacedEggCoordinator coordinator,
            PlacedEggHolograms holograms,
            BiConsumer<UUID, PlacedEggRecord> onReady) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.coordinator = Objects.requireNonNull(coordinator, "placed egg coordinator");
        this.holograms = Objects.requireNonNull(holograms, "hologram manager");
        this.onReady = Objects.requireNonNull(onReady, "ready handler");
    }

    /** Starts ticking and restores a hologram over every egg already on disk. */
    public void start() {
        if (task != null) return;
        lastPassNanos = System.nanoTime();
        lastFlushNanos = lastPassNanos;
        task = plugin.getServer().getScheduler().runTaskTimer(
                plugin, this::pass, PERIOD_TICKS, PERIOD_TICKS);
        PlacedEggStore.Scan scan = coordinator.all();
        scan.records().forEach(this::refresh);
        if (!scan.unreadable().isEmpty()) {
            plugin.getLogger().warning("OmniPet could not read " + scan.unreadable().size()
                    + " placed egg record(s); they need operator review: " + scan.unreadable());
        }
        if (scan.truncated()) {
            plugin.getLogger().warning("OmniPet stopped scanning placed eggs at the bound; "
                    + "older records were not loaded and need operator review.");
        }
    }

    public void stop() {
        if (task != null) task.cancel();
        task = null;
        // Last chance to persist: a countdown that only lived in memory would otherwise roll back to the
        // previous flush on restart.
        coordinator.flush();
        holograms.hideAll();
    }

    /** Redraws one record's hologram from its current state. */
    public void refresh(PlacedEggRecord record) {
        Block block = block(record);
        if (block == null) return;
        holograms.show(PlacedEggCoordinator.hologramLocation(block), record, text(record));
    }

    public void remove(String recordKey) {
        holograms.hide(recordKey);
    }

    /**
     * Deletes a record whose pet has been admitted.
     *
     * <p>Called only after the grant succeeded, so an egg is never consumed for a pet that did not
     * arrive. Until then the record stays and the egg keeps reading ready.
     */
    public void consume(PlacedEggRecord record) {
        coordinator.consume(record);
        holograms.hide(record.key());
    }

    private void pass() {
        long now = System.nanoTime();
        long elapsedMillis = Math.max(0, (now - lastPassNanos) / 1_000_000L);
        lastPassNanos = now;
        if (elapsedMillis == 0) return;
        // Countdowns move in memory every pass and reach disk on this cadence. Writing each one every
        // second meant two fsyncs per egg per second on the tick thread.
        if (now - lastFlushNanos >= FLUSH_INTERVAL_NANOS) {
            lastFlushNanos = now;
            coordinator.flush();
        }
        for (PlacedEggRecord record : coordinator.all().records()) {
            Block block = block(record);
            // An unloaded world or chunk simply pauses that egg: crediting time for a block nobody can
            // see would let an egg hatch somewhere the requirement may no longer hold.
            if (block == null || !block.getChunk().isLoaded()) continue;
            coordinator.tick(block, record, elapsedMillis).ifPresent(advanced -> {
                refresh(advanced);
                if (advanced.ready()) onReady.accept(advanced.ownerId(), advanced);
            });
        }
    }

    private Block block(PlacedEggRecord record) {
        var world = Bukkit.getWorld(record.world());
        if (world == null) return null;
        return world.getBlockAt(record.blockX(), record.blockY(), record.blockZ());
    }

    private static net.kyori.adventure.text.Component text(PlacedEggRecord record) {
        return record.ready()
                ? Messages.line(MessageKey.EGG_HOLOGRAM_READY)
                : Messages.line(MessageKey.EGG_HOLOGRAM_COUNTDOWN,
                        Messages.of("remaining", Durations.countdown(record.remainingMillis())));
    }
}
