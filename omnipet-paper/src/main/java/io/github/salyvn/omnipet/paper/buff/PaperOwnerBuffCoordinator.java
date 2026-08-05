package io.github.salyvn.omnipet.paper.buff;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import io.github.salyvn.omnipet.core.buff.PetStatBuff;
import io.github.salyvn.omnipet.core.buff.PetStatBuffProjection;
import io.github.salyvn.omnipet.core.storage.PetStorageSnapshot;

/** Coalesces async storage snapshots before mutating MythicLib on the main thread. */
public final class PaperOwnerBuffCoordinator implements Consumer<PetStorageSnapshot>, AutoCloseable {
    private final JavaPlugin plugin;
    private final Map<UUID, Desired> desiredByOwner = new LinkedHashMap<>();
    /** Reasons already warned about, per owner, so a per-snapshot condition logs once. Cleared on quit. */
    private final Map<UUID, Set<String>> reportedByOwner = new LinkedHashMap<>();
    private ReflectiveMythicLibBuffPort port;
    private boolean closed;

    public PaperOwnerBuffCoordinator(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    public void refreshProvider() {
        requireMainThread();
        if (closed) return;
        Plugin provider = plugin.getServer().getPluginManager().getPlugin("MythicLib");
        port = provider == null || !provider.isEnabled()
                ? null
                : new ReflectiveMythicLibBuffPort(
                        provider.getClass().getClassLoader(),
                        Bukkit::isPrimaryThread,
                        ownerId -> plugin.getServer().getPlayer(ownerId) != null);
        for (UUID ownerId : owners()) applyCurrent(ownerId);
    }

    @Override
    public void accept(PetStorageSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "buff storage snapshot");
        List<PetStatBuff> buffs = PetStatBuffProjection.readActive(
                snapshot.pets(), snapshot.desiredActivePetIds());
        synchronized (this) {
            if (closed) return;
            Desired current = desiredByOwner.get(snapshot.playerId());
            if (current != null && current.revision() > snapshot.revision()) return;
            desiredByOwner.put(snapshot.playerId(), new Desired(snapshot.revision(), buffs));
        }
        runMain(() -> applyRevision(snapshot.playerId(), snapshot.revision()));
    }

    public void ownerQuit(UUID ownerId) {
        Objects.requireNonNull(ownerId, "buff owner ID");
        synchronized (this) {
            desiredByOwner.remove(ownerId);
            // Forgotten with the owner, so a condition they fix and re-log in on is reported again rather
            // than staying silent for the lifetime of the server.
            reportedByOwner.remove(ownerId);
        }
        runMain(() -> clearCurrent(ownerId));
    }

    @Override
    public void close() {
        requireMainThread();
        List<UUID> owners;
        synchronized (this) {
            if (closed) return;
            closed = true;
            owners = List.copyOf(desiredByOwner.keySet());
            desiredByOwner.clear();
        }
        owners.forEach(this::clearCurrent);
        port = null;
    }

    private void applyRevision(UUID ownerId, long revision) {
        requireMainThread();
        Desired desired;
        synchronized (this) {
            if (closed) return;
            desired = desiredByOwner.get(ownerId);
            if (desired == null || desired.revision() != revision) return;
        }
        apply(ownerId, desired.buffs());
    }

    private void applyCurrent(UUID ownerId) {
        Desired desired;
        synchronized (this) {
            desired = desiredByOwner.get(ownerId);
        }
        if (desired != null) apply(ownerId, desired.buffs());
    }

    private void clearCurrent(UUID ownerId) {
        if (port != null) report(ownerId, port.clear(ownerId));
    }

    private void apply(UUID ownerId, List<PetStatBuff> buffs) {
        if (port != null) report(ownerId, port.reconcile(ownerId, buffs));
    }

    /**
     * Says what happened to an owner's buffs, when it is worth saying.
     *
     * <p>This used to log {@code QUARANTINED} and nothing else, which made "MythicLib stats do nothing"
     * undiagnosable: a server with no MythicLib, an owner who had just logged out, and a pet whose stat IDs
     * MythicLib has never heard of all looked identical from outside — silent, with the plugin reporting
     * success. The skipped count in particular was already being computed and then thrown away.
     *
     * <p>Deduplicated per owner and reason, because both the unavailable state and an unregistered stat ID
     * are properties of the configuration rather than of one tick, and this runs on every snapshot.
     */
    private void report(UUID ownerId, MythicLibBuffResult result) {
        switch (result.status()) {
            case QUARANTINED -> warnOnce(ownerId, "quarantined",
                    "MythicLib pet buffs quarantined for " + ownerId + ": " + result.detail());
            case UNAVAILABLE -> warnOnce(ownerId, "unavailable:" + result.detail(),
                    "MythicLib pet buffs were not applied for " + ownerId + ": " + result.detail());
            case OFF_THREAD -> warnOnce(ownerId, "off-thread",
                    "MythicLib pet buffs were skipped for " + ownerId + ": " + result.detail());
            case APPLIED -> {
                // Applied with nothing applied. Every stat was dropped because MythicLib does not know the
                // ID, which is an authoring mistake in the pet definition and the likeliest cause of
                // "the stats do nothing" on a server where MythicLib is installed and working.
                if (result.skipped() > 0) {
                    warnOnce(ownerId, "skipped:" + result.skipped(),
                            "MythicLib did not recognise " + result.skipped() + " stat ID(s) on "
                            + ownerId + "'s active pets, so those stats had no effect — check the stat IDs"
                            + " in the pet definition against MythicLib's registered stats");
                }
            }
        }
    }

    /** Warns once per owner and reason, so a per-snapshot condition does not become a per-snapshot line. */
    private void warnOnce(UUID ownerId, String reason, String message) {
        if (!reportedByOwner.computeIfAbsent(ownerId, ignored -> new LinkedHashSet<>()).add(reason)) return;
        plugin.getLogger().warning(message);
    }

    private synchronized List<UUID> owners() {
        return List.copyOf(desiredByOwner.keySet());
    }

    private void runMain(Runnable task) {
        if (closed || !plugin.isEnabled()) return;
        if (Bukkit.isPrimaryThread()) task.run();
        else plugin.getServer().getScheduler().runTask(plugin, task);
    }

    private static void requireMainThread() {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("buff lifecycle requires the Paper main thread");
    }

    private record Desired(long revision, List<PetStatBuff> buffs) {
        private Desired {
            buffs = List.copyOf(buffs);
        }
    }
}
