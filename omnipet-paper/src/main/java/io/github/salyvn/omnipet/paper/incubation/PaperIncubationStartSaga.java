package io.github.salyvn.omnipet.paper.incubation;

import java.io.IOException;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import io.github.salyvn.omnipet.core.domain.incubation.EggDefinitionEnvelope;
import io.github.salyvn.omnipet.core.incubation.EggEscrowStage;
import io.github.salyvn.omnipet.core.incubation.EggEscrowTransaction;
import io.github.salyvn.omnipet.core.incubation.EggInventoryHand;
import io.github.salyvn.omnipet.core.incubation.HatchResult;
import io.github.salyvn.omnipet.core.incubation.ItemEscrowResult;
import io.github.salyvn.omnipet.core.persistence.RegistrySnapshot;
import io.github.salyvn.omnipet.core.storage.PetStorageLimits;
import io.github.salyvn.omnipet.paper.permission.PaperStorageLimitsResolver;
import io.github.salyvn.omnipet.paper.task.PerPlayerTaskQueue;

/** Starts an incubation without holding a Bukkit Player across async boundaries. */
final class PaperIncubationStartSaga {
    private final JavaPlugin plugin;
    private final PaperIncubationServices services;
    private final io.github.salyvn.omnipet.core.persistence.RegistrySnapshotRepository registry;
    private volatile PaperStorageLimitsResolver limitsResolver;
    private final PerPlayerTaskQueue tasks;
    private final PaperEggItemCodec codec;
    private final EggInventoryEscrowService inventoryEscrow;
    private final ConcurrentMap<UUID, Boolean> inFlight = new ConcurrentHashMap<>();
    private volatile boolean closed;

    PaperIncubationStartSaga(
            JavaPlugin plugin,
            PaperIncubationServices services,
            io.github.salyvn.omnipet.core.persistence.RegistrySnapshotRepository registry,
            PaperStorageLimitsResolver limitsResolver,
            PerPlayerTaskQueue tasks) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.services = Objects.requireNonNull(services, "incubation services");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.limitsResolver = Objects.requireNonNull(limitsResolver, "limits resolver");
        this.tasks = Objects.requireNonNull(tasks, "player task queue");
        this.codec = new PaperEggItemCodec(plugin);
        this.inventoryEscrow = new EggInventoryEscrowService();
    }

    void updateLimitsResolver(PaperStorageLimitsResolver next) {
        limitsResolver = Objects.requireNonNull(next, "limits resolver");
    }

    void start(Player player, EggInventoryHand hand) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(hand, "egg hand");
        if (closed || !player.isOnline() || !Bukkit.isPrimaryThread()) return;
        UUID playerId = player.getUniqueId();
        if (inFlight.putIfAbsent(playerId, Boolean.TRUE) != null) return;
        try {
            PetStorageLimits limits = limitsResolver.resolve(player::hasPermission).limits();
            CapturedEggItem captured = new PaperPlayerEggInventory(player, codec).capture(hand);
            if (!submit(playerId, () -> prepareAndStart(playerId, captured, limits))) {
                finish(playerId, "start queue rejected");
            }
        } catch (RuntimeException failure) {
            inFlight.remove(playerId);
            plugin.getLogger().warning("OmniPet incubation capture failed for " + playerId + ": "
                    + failure.getMessage());
        }
    }

    void close() {
        closed = true;
        inFlight.clear();
    }

    void onQuit(UUID playerId) {
        if (playerId != null) inFlight.remove(playerId);
    }

    private void prepareAndStart(UUID playerId, CapturedEggItem captured, PetStorageLimits limits) {
        UUID incubationId = UUID.randomUUID();
        try {
            EggDefinitionEnvelope envelope = services.eggDefinitions().read(captured.eggId())
                    .orElseThrow(() -> new IOException("unknown egg definition: " + captured.eggId()));
            var snapshot = services.hatches().snapshot(playerId);
            RegistrySnapshot currentRegistry = registry.current();
            EggEscrowTransaction transaction = new EggEscrowTransaction(
                    incubationId,
                    playerId,
                    incubationId,
                    captured.eggId(),
                    snapshot.revision(),
                    captured.identity(),
                    EggEscrowStage.PREPARED,
                    java.util.Map.of());
            ItemEscrowResult prepared = services.itemEscrow().prepare(transaction);
            if (prepared.status() != ItemEscrowResult.Status.CREATED
                    && prepared.status() != ItemEscrowResult.Status.ALREADY_EXISTS) {
                finish(playerId, "egg escrow was not prepared: " + prepared.status());
                return;
            }
            HatchResult started = services.hatches().start(
                    playerId,
                    snapshot.revision(),
                    incubationId,
                    envelope.definition(),
                    currentRegistry,
                    incubationId.getMostSignificantBits() ^ incubationId.getLeastSignificantBits(),
                    limits);
            if (started.status() != HatchResult.Status.STARTED) {
                services.itemEscrow().cancel(incubationId);
                finish(playerId, "egg could not start: " + started.status());
                return;
            }
            runMain(playerId, player -> removeCapturedItem(playerId, transaction));
        } catch (IOException | RuntimeException failure) {
            plugin.getLogger().warning("OmniPet incubation start is pending for " + playerId + ": "
                    + failure.getMessage());
            finish(playerId, "incubation start deferred");
        }
    }

    private void removeCapturedItem(UUID playerId, EggEscrowTransaction transaction) {
        if (closed) return;
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) return;
        EggInventoryMutationResult result;
        try {
            result = inventoryEscrow.removeOne(
                    transaction.item(), new PaperPlayerEggInventory(player, codec));
        } catch (RuntimeException failure) {
            plugin.getLogger().warning("OmniPet could not remove egg for " + playerId + ": "
                    + failure.getMessage());
            finish(playerId, "item removal deferred");
            return;
        }
        if (result != EggInventoryMutationResult.REMOVED) {
            plugin.getLogger().warning("OmniPet left incubation escrow pending for " + playerId
                    + ": " + result);
            finish(playerId, "item removal deferred");
            return;
        }
        if (!submit(playerId, () -> commitRemoved(playerId, transaction.transactionId()))) {
            finish(playerId, "commit queue rejected");
        }
    }

    private void commitRemoved(UUID playerId, UUID transactionId) {
        try {
            ItemEscrowResult removed = services.itemEscrow().markItemRemoved(transactionId);
            if (removed.status() == ItemEscrowResult.Status.ITEM_REMOVED) {
                services.itemEscrow().commit(transactionId);
            }
        } catch (IOException | RuntimeException failure) {
            plugin.getLogger().warning("OmniPet incubation escrow remains recoverable for " + playerId
                    + ": " + failure.getMessage());
        } finally {
            finish(playerId, "incubation started");
        }
    }

    private void runMain(UUID playerId, java.util.function.Consumer<Player> callback) {
        if (closed || !plugin.isEnabled()) {
            finish(playerId, "main-thread work deferred");
            return;
        }
        try {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null && player.isOnline()) callback.accept(player);
                else finish(playerId, "player disconnected before item removal");
            });
        } catch (RuntimeException failure) {
            plugin.getLogger().warning("OmniPet could not schedule incubation main-thread work: "
                    + failure.getMessage());
            finish(playerId, "main-thread dispatch failed");
        }
    }

    private boolean submit(UUID playerId, Runnable task) {
        try {
            boolean accepted = tasks.submit(playerId, task);
            if (!accepted && !closed) {
                plugin.getLogger().warning("OmniPet incubation work was rejected for " + playerId);
            }
            return accepted;
        } catch (RuntimeException failure) {
            if (!closed) plugin.getLogger().warning("OmniPet incubation queue failed for " + playerId
                    + ": " + failure.getMessage());
            return false;
        }
    }

    private void finish(UUID playerId, String message) {
        inFlight.remove(playerId);
        if (message != null && !message.isBlank()) {
            plugin.getLogger().fine("OmniPet incubation " + message + " for " + playerId);
        }
    }
}
