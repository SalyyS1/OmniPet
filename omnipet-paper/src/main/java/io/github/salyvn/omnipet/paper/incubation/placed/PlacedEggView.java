package io.github.salyvn.omnipet.paper.incubation.placed;

import java.util.Objects;
import java.util.UUID;
import java.util.function.BiConsumer;

import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import io.github.salyvn.omnipet.core.incubation.EggShake;
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

    /**
     * How often the rocking is redrawn, in ticks.
     *
     * <p>Separate from the countdown pass because the two want opposite things: crediting time is cheap
     * once a second, but sampling a shake eleven times a second at 1 Hz produces a stutter rather than a
     * rock. This task does no I/O and touches only eggs already close to hatching.
     */
    private static final long SHAKE_PERIOD_TICKS = 2L;

    /** Beyond this many blocks nobody can read the hologram, so nothing is redrawn for it. */
    private static final double VIEWER_RADIUS = 32.0;
    private static final double VIEWER_RADIUS_SQUARED = VIEWER_RADIUS * VIEWER_RADIUS;

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
    private BukkitTask shakeTask;
    private long lastPassNanos;
    private long lastFlushNanos;
    private long startedNanos;

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
        startedNanos = lastPassNanos;
        task = plugin.getServer().getScheduler().runTaskTimer(
                plugin, this::pass, PERIOD_TICKS, PERIOD_TICKS);
        shakeTask = plugin.getServer().getScheduler().runTaskTimer(
                plugin, this::shakePass, SHAKE_PERIOD_TICKS, SHAKE_PERIOD_TICKS);
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
        if (shakeTask != null) shakeTask.cancel();
        task = null;
        shakeTask = null;
        // Last chance to persist: a countdown that only lived in memory would otherwise roll back to the
        // previous flush on restart.
        coordinator.flush();
        holograms.hideAll();
    }

    /** Redraws one record's hologram from its current state. */
    public void refresh(PlacedEggRecord record) {
        Block block = block(record);
        if (block == null) return;
        holograms.show(PlacedEggCoordinator.hologramLocation(block), record, text(record),
                shakeDegrees(record));
    }

    /**
     * Rocks the eggs that are close to hatching.
     *
     * <p>Does no I/O and skips anything still in its quiet period, so the cost is proportional to the
     * eggs about to hatch rather than to every egg placed. An egg nobody is near is skipped outright: the
     * whole point of the effect is that somebody sees it.
     */
    private void shakePass() {
        for (PlacedEggRecord record : coordinator.all().records()) {
            if (record.ready()) continue;
            if (EggShake.intensity(record.remainingMillis(), record.totalMillis()) <= 0) continue;
            Block block = block(record);
            if (block == null || !block.getChunk().isLoaded()) continue;
            if (!watched(block)) continue;
            holograms.show(PlacedEggCoordinator.hologramLocation(block), record, text(record),
                    shakeDegrees(record));
        }
    }

    /** The rocking angle for one egg this instant, desynchronised so neighbours do not rock together. */
    private double shakeDegrees(PlacedEggRecord record) {
        double phaseSeconds = (System.nanoTime() - startedNanos) / 1_000_000_000.0
                + phaseOffsetSeconds(record);
        return EggShake.degrees(record.remainingMillis(), record.totalMillis(), phaseSeconds);
    }

    /**
     * A stable per-egg phase offset.
     *
     * <p>Derived from the block key so it survives a restart and so two eggs side by side never rock in
     * lockstep -- synchronised motion reads as a mechanism rather than as something alive, the same reason
     * pets carry a phase offset.
     */
    private static double phaseOffsetSeconds(PlacedEggRecord record) {
        int hash = record.key().hashCode();
        return Math.abs(hash % 1000) / 1000.0;
    }

    /** Whether any player is close enough to read this hologram. */
    private boolean watched(Block block) {
        var location = block.getLocation();
        for (var player : block.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(location) <= VIEWER_RADIUS_SQUARED) return true;
        }
        return false;
    }

    public void remove(String recordKey) {
        holograms.hide(recordKey);
    }

    /**
     * Deletes a record whose pet has been admitted, and clears the block it occupied.
     *
     * <p>Called only after the grant succeeded, so an egg is never consumed for a pet that did not
     * arrive. Until then the record stays and the egg keeps reading ready.
     *
     * <p>The block goes last and only if it is still the egg. It used to be left standing: the record and
     * the hologram were deleted and the egg block stayed in the world forever, looking like an egg that
     * had stopped incubating. Worse, breaking that orphan dropped a vanilla turtle egg, because the
     * listener's no-drop path only covers blocks it still has a record for.
     */
    public void consume(PlacedEggRecord record) {
        coordinator.consume(record);
        holograms.hide(record.key());
        clearBlock(record);
    }

    /**
     * Puts the egg's block back to air.
     *
     * <p>Only when the block is still whatever the record says was placed there. A record whose block
     * someone has since replaced must not have that replacement deleted, and after a chunk reload we are
     * reading the world rather than trusting memory.
     *
     * <p>An unloaded chunk is left alone. The record is gone either way, so the worst case is one stray
     * decorative block rather than a lost pet.
     */
    private void clearBlock(PlacedEggRecord record) {
        Block block = block(record);
        if (block == null || !block.getChunk().isLoaded()) return;
        if (!PlacedEggBlocks.isEggBlock(block)) return;
        // No physics update: the egg is decorative and its neighbours have nothing to recalculate.
        block.setType(org.bukkit.Material.AIR, false);
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
