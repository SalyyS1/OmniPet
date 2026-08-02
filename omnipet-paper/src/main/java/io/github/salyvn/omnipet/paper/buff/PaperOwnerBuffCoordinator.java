package io.github.salyvn.omnipet.paper.buff;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

    private void report(UUID ownerId, MythicLibBuffResult result) {
        if (result.status() == MythicLibBuffResult.Status.QUARANTINED) {
            plugin.getLogger().warning("MythicLib pet buffs quarantined for " + ownerId + ": " + result.detail());
        }
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
