package io.github.salyvn.omnipet.paper.incubation;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import io.github.salyvn.omnipet.core.domain.incubation.IncubationStatus;
import io.github.salyvn.omnipet.core.incubation.EggInventoryHand;
import io.github.salyvn.omnipet.core.persistence.RegistrySnapshotRepository;
import io.github.salyvn.omnipet.paper.permission.PaperStorageLimitsResolver;
import io.github.salyvn.omnipet.paper.task.PerPlayerTaskQueue;

/** Owns the one online-only incubation loop and lifecycle-safe recovery hooks. */
public final class PaperIncubationCoordinator {
    private static final long TICK_PERIOD_TICKS = 100L;

    private final JavaPlugin plugin;
    private final PaperIncubationServices services;
    private final PerPlayerTaskQueue tasks;
    private final PaperIncubationStartSaga starts;
    private final PaperIncubationRecoveryExecutor recovery;
    private final IncubationOnlineEpochs onlineEpochs = new IncubationOnlineEpochs();
    private final java.util.Map<UUID, Long> lastTickNanos = new ConcurrentHashMap<>();
    private volatile BukkitTask ticker;
    private volatile boolean closed;

    public PaperIncubationCoordinator(
            JavaPlugin plugin,
            PaperIncubationServices services,
            RegistrySnapshotRepository registry,
            PaperStorageLimitsResolver limitsResolver,
            PerPlayerTaskQueue tasks) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.services = Objects.requireNonNull(services, "incubation services");
        this.tasks = Objects.requireNonNull(tasks, "player task queue");
        Objects.requireNonNull(limitsResolver, "limits resolver");
        this.starts = new PaperIncubationStartSaga(plugin, services, registry, limitsResolver, tasks);
        this.recovery = new PaperIncubationRecoveryExecutor(plugin, services, tasks);
    }

    public void start() {
        if (closed || ticker != null) return;
        ticker = plugin.getServer().getScheduler().runTaskTimer(
                plugin, this::tickOnlinePlayers, TICK_PERIOD_TICKS, TICK_PERIOD_TICKS);
    }

    public void updateLimitsResolver(PaperStorageLimitsResolver next) {
        starts.updateLimitsResolver(Objects.requireNonNull(next, "limits resolver"));
    }

    public void onJoin(Player player) {
        Objects.requireNonNull(player, "player");
        if (closed || !player.isOnline()) return;
        UUID playerId = player.getUniqueId();
        onlineEpochs.join(playerId);
        lastTickNanos.put(playerId, System.nanoTime());
        recovery.recover(player);
    }

    public void onQuit(UUID playerId) {
        if (playerId == null) return;
        onlineEpochs.quit(playerId);
        starts.onQuit(playerId);
        lastTickNanos.remove(playerId);
    }

    public void start(Player player, EggInventoryHand hand) {
        starts.start(player, hand);
    }

    public void close() {
        closed = true;
        BukkitTask current = ticker;
        ticker = null;
        if (current != null) current.cancel();
        starts.close();
        recovery.close();
        onlineEpochs.clear();
        lastTickNanos.clear();
    }

    private void tickOnlinePlayers() {
        if (closed || !plugin.isEnabled()) return;
        long now = System.nanoTime();
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID playerId = player.getUniqueId();
            long epoch = onlineEpochs.currentOrJoin(playerId);
            long previous = lastTickNanos.put(playerId, now);
            if (previous == 0L || now <= previous) continue;
            long elapsedMillis = Duration.ofNanos(now - previous).toMillis();
            if (elapsedMillis < 1L) continue;
            submitTick(playerId, epoch, elapsedMillis);
        }
    }

    private void submitTick(UUID playerId, long epoch, long elapsedMillis) {
        try {
            tasks.submit(playerId, () -> {
                if (closed || !isCurrentEpoch(playerId, epoch)) return;
                try {
                    var snapshot = services.hatches().snapshot(playerId);
                    if (!isCurrentEpoch(playerId, epoch)
                            || snapshot.incubation() == null
                            || snapshot.incubation().status() != IncubationStatus.INCUBATING) return;
                    services.hatches().tick(
                            playerId,
                            snapshot.revision(),
                            snapshot.incubation().id(),
                            elapsedMillis);
                } catch (java.io.IOException | RuntimeException failure) {
                    plugin.getLogger().warning("OmniPet incubation tick deferred for " + playerId
                            + ": " + failure.getMessage());
                }
            });
        } catch (RuntimeException failure) {
            if (!closed) plugin.getLogger().warning("OmniPet incubation tick queue failed for "
                    + playerId + ": " + failure.getMessage());
        }
    }

    private boolean isCurrentEpoch(UUID playerId, long epoch) {
        return onlineEpochs.isCurrent(playerId, epoch);
    }
}
