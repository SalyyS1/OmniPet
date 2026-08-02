package io.github.salyvn.omnipet.paper.incubation;

import java.util.Objects;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import io.github.salyvn.omnipet.core.domain.incubation.IncubationStatus;
import io.github.salyvn.omnipet.core.incubation.EggEscrowStage;
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
    private final IncubationTickBaselines tickBaselines = new IncubationTickBaselines();
    private volatile java.util.function.Consumer<UUID> refreshListener = ignored -> { };
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
        this.recovery = new PaperIncubationRecoveryExecutor(
                plugin, services, tasks, this::observeCommitted);
        this.starts = new PaperIncubationStartSaga(
                plugin, services, registry, limitsResolver, tasks, recovery::recover,
                this::observeCommitted);
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
        tickBaselines.reset(playerId);
        recovery.recover(player);
    }

    public void onQuit(UUID playerId) {
        if (playerId == null) return;
        onlineEpochs.quit(playerId);
        starts.onQuit(playerId);
        tickBaselines.reset(playerId);
    }

    public boolean start(Player player, EggInventoryHand hand) {
        return starts.start(player, hand);
    }

    public void setRefreshListener(java.util.function.Consumer<UUID> listener) {
        refreshListener = Objects.requireNonNull(listener, "refresh listener");
    }

    public void close() {
        closed = true;
        BukkitTask current = ticker;
        ticker = null;
        if (current != null) current.cancel();
        starts.close();
        recovery.close();
        onlineEpochs.clear();
        tickBaselines.clear();
    }

    private void tickOnlinePlayers() {
        if (closed || !plugin.isEnabled()) return;
        long now = System.nanoTime();
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID playerId = player.getUniqueId();
            long epoch = onlineEpochs.currentOrJoin(playerId);
            submitTick(playerId, epoch, now);
        }
    }

    private void observeCommitted(UUID playerId, UUID incubationId) {
        tickBaselines.observeCommitted(playerId, incubationId, System.nanoTime());
    }

    private void submitTick(UUID playerId, long epoch, long sampledNanos) {
        try {
            tasks.submit(playerId, () -> {
                if (closed || !isCurrentEpoch(playerId, epoch)) return;
                UUID attemptedIncubation = null;
                try {
                    var snapshot = services.hatches().snapshot(playerId);
                    if (!isCurrentEpoch(playerId, epoch)
                            || snapshot.incubation() == null
                            || snapshot.incubation().status() != IncubationStatus.INCUBATING) {
                        tickBaselines.reset(playerId);
                        return;
                    }
                    var transaction = services.eggEscrowJournal().find(snapshot.incubation().id()).orElse(null);
                    if (transaction == null || transaction.stage() != EggEscrowStage.COMMITTED) {
                        tickBaselines.reset(playerId);
                        if (transaction != null && (transaction.stage() == EggEscrowStage.PREPARED
                                || transaction.stage() == EggEscrowStage.ITEM_REMOVED
                                || transaction.stage() == EggEscrowStage.REFUND_PENDING)) {
                            recovery.recover(playerId);
                        }
                        return;
                    }
                    UUID incubationId = snapshot.incubation().id();
                    attemptedIncubation = incubationId;
                    long elapsedMillis = tickBaselines.elapsedMillis(
                            playerId, incubationId, sampledNanos, System.nanoTime());
                    if (elapsedMillis < 1L) return;
                    var result = services.hatches().tick(
                            playerId,
                            snapshot.revision(),
                            incubationId,
                            elapsedMillis);
                    if (result.status() == io.github.salyvn.omnipet.core.incubation.HatchResult.Status.TICKED) {
                        tickBaselines.commit(playerId, incubationId, sampledNanos);
                        if (result.incubation() == null
                                || result.incubation().status() != IncubationStatus.INCUBATING) {
                            tickBaselines.reset(playerId);
                        }
                        refreshListener.accept(playerId);
                    } else {
                        tickBaselines.reset(playerId);
                    }
                } catch (java.io.IOException | RuntimeException failure) {
                    if (attemptedIncubation != null) {
                        tickBaselines.discard(playerId, attemptedIncubation, sampledNanos);
                    }
                    plugin.getLogger().warning("OmniPet incubation tick deferred for " + playerId
                            + "; unpersisted online time was paused to preserve the rollback bound: "
                            + failure.getMessage());
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
