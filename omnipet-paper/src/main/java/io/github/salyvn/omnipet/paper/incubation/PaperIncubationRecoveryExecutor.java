package io.github.salyvn.omnipet.paper.incubation;

import java.io.IOException;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import io.github.salyvn.omnipet.core.domain.PlayerState;
import io.github.salyvn.omnipet.core.domain.incubation.IncubationStatus;
import io.github.salyvn.omnipet.core.incubation.EggEscrowRecovery;
import io.github.salyvn.omnipet.core.incubation.EggEscrowRecoveryDirective;
import io.github.salyvn.omnipet.core.incubation.EggEscrowItemObservation;
import io.github.salyvn.omnipet.core.incubation.EggEscrowStage;
import io.github.salyvn.omnipet.core.incubation.EggEscrowTransaction;
import io.github.salyvn.omnipet.core.incubation.HatchResult;
import io.github.salyvn.omnipet.core.incubation.ItemEscrowResult;
import io.github.salyvn.omnipet.paper.task.PerPlayerTaskQueue;

/** Replays only the conservative recovery directives emitted by the core. */
final class PaperIncubationRecoveryExecutor {
    private static final int MAX_TRANSACTIONS_PER_JOIN = 100;
    private static final Set<EggEscrowStage> PENDING_STAGES = Set.copyOf(EnumSet.of(
            EggEscrowStage.PREPARED,
            EggEscrowStage.ITEM_REMOVED,
            EggEscrowStage.REFUND_PENDING));

    private final JavaPlugin plugin;
    private final PaperIncubationServices services;
    private final PerPlayerTaskQueue tasks;
    private final java.util.function.BiConsumer<UUID, UUID> committedObserver;
    private final PaperEggItemCodec codec;
    private final EggInventoryEscrowService inventoryEscrow;
    private final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();
    private volatile boolean closed;

    PaperIncubationRecoveryExecutor(
            JavaPlugin plugin,
            PaperIncubationServices services,
            PerPlayerTaskQueue tasks,
            java.util.function.BiConsumer<UUID, UUID> committedObserver) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.services = Objects.requireNonNull(services, "incubation services");
        this.tasks = Objects.requireNonNull(tasks, "player task queue");
        this.committedObserver = Objects.requireNonNull(committedObserver, "committed observer");
        this.codec = new PaperEggItemCodec(plugin);
        this.inventoryEscrow = new EggInventoryEscrowService();
    }

    void recover(Player player) {
        Objects.requireNonNull(player, "player");
        if (closed || !player.isOnline()) return;
        recover(player.getUniqueId());
    }

    void recover(UUID playerId) {
        if (closed || playerId == null || !inFlight.add(playerId)) return;
        if (!submit(playerId, () -> loadTransactions(playerId))) inFlight.remove(playerId);
    }

    void close() {
        closed = true;
        inFlight.clear();
    }

    private void loadTransactions(UUID playerId) {
        try {
            PlayerState player = services.hatches().snapshot(playerId);
            var transactions = services.eggEscrowJournal().findByPlayer(
                    playerId, PENDING_STAGES, MAX_TRANSACTIONS_PER_JOIN);
            if (transactions.size() == MAX_TRANSACTIONS_PER_JOIN) {
                plugin.getLogger().warning("OmniPet escrow recovery reached its bounded scan for "
                        + playerId + "; inspect older pending records manually.");
            }
            Set<UUID> scheduled = new HashSet<>();
            for (EggEscrowTransaction transaction : transactions) {
                if (scheduled.add(transaction.transactionId())) recoverTransaction(transaction);
            }
            if (player.incubation() != null) {
                services.eggEscrowJournal().find(player.incubation().id())
                        .filter(transaction -> {
                            if (transaction.stage() == EggEscrowStage.COMMITTED
                                    && player.incubation().status() == IncubationStatus.INCUBATING) {
                                committedObserver.accept(playerId, transaction.transactionId());
                            }
                            return scheduled.add(transaction.transactionId());
                        })
                        .ifPresent(this::recoverTransaction);
            }
        } catch (IOException | RuntimeException failure) {
            plugin.getLogger().warning("OmniPet incubation recovery scan failed for " + playerId
                    + ": " + failure.getMessage());
        } finally {
            inFlight.remove(playerId);
        }
    }

    private void recoverTransaction(EggEscrowTransaction transaction) {
        UUID playerId = transaction.playerId();
        runMain(playerId, player -> {
            EggEscrowItemObservation observation;
            try {
                observation = inventoryEscrow.observe(
                        transaction.item(), new PaperPlayerEggInventory(player, codec));
            } catch (RuntimeException failure) {
                plugin.getLogger().warning("OmniPet could not inspect escrow "
                        + transaction.transactionId() + ": " + failure.getMessage());
                return;
            }
            submit(playerId, () -> inspectAndApply(transaction, observation));
        });
    }

    private void inspectAndApply(
            EggEscrowTransaction transaction,
            EggEscrowItemObservation observation) {
        try {
            PlayerState player = services.hatches().snapshot(transaction.playerId());
            EggEscrowRecovery recovery = services.recovery().inspect(transaction, player, observation);
            switch (recovery.directive()) {
                case NONE -> { }
                case REMOVE_MATCHING_ITEM -> removeMatching(transaction);
                case CANCEL_TRANSACTION -> cancelTransaction(transaction);
                case COMMIT_TRANSACTION -> commitTransaction(transaction);
                case REFUND_ITEM -> refundItem(transaction);
                case CANCEL_INCUBATION -> cancelIncubation(transaction, player);
                case OPERATOR_REVIEW -> plugin.getLogger().warning(
                        "OmniPet escrow requires operator review " + transaction.transactionId()
                                + ": " + recovery.reason());
            }
        } catch (IOException | RuntimeException failure) {
            plugin.getLogger().warning("OmniPet escrow recovery deferred for "
                    + transaction.transactionId() + ": " + failure.getMessage());
        }
    }

    private void removeMatching(EggEscrowTransaction transaction) {
        runMain(transaction.playerId(), player -> {
            EggInventoryMutationResult result;
            try {
                result = inventoryEscrow.removeOne(
                        transaction.item(), new PaperPlayerEggInventory(player, codec));
            } catch (RuntimeException failure) {
                plugin.getLogger().warning("OmniPet escrow removal failed for "
                        + transaction.transactionId() + ": " + failure.getMessage());
                return;
            }
            if (result == EggInventoryMutationResult.REMOVED) {
                submit(transaction.playerId(), () -> markRemovedAndCommit(transaction));
            }
        });
    }

    private void markRemovedAndCommit(EggEscrowTransaction transaction) {
        try {
            ItemEscrowResult removed = services.itemEscrow().markItemRemoved(transaction.transactionId());
            if (removed.status() == ItemEscrowResult.Status.ITEM_REMOVED) {
                ItemEscrowResult committed = services.itemEscrow().commit(transaction.transactionId());
                if (committed.status() == ItemEscrowResult.Status.COMMITTED) {
                    committedObserver.accept(transaction.playerId(), transaction.transactionId());
                }
            }
        } catch (IOException | RuntimeException failure) {
            plugin.getLogger().warning("OmniPet escrow commit deferred for "
                    + transaction.transactionId() + ": " + failure.getMessage());
        }
    }

    private void cancelTransaction(EggEscrowTransaction transaction) throws IOException {
        services.itemEscrow().cancel(transaction.transactionId());
    }

    private void commitTransaction(EggEscrowTransaction transaction) throws IOException {
        ItemEscrowResult committed = services.itemEscrow().commit(transaction.transactionId());
        if (committed.status() == ItemEscrowResult.Status.COMMITTED) {
            committedObserver.accept(transaction.playerId(), transaction.transactionId());
        }
    }

    private void refundItem(EggEscrowTransaction transaction) throws IOException {
        if (transaction.stage() == EggEscrowStage.ITEM_REMOVED) {
            ItemEscrowResult pending = services.itemEscrow().markRefundPending(transaction.transactionId());
            if (pending.status() != ItemEscrowResult.Status.REFUND_PENDING) return;
        }
        runMain(transaction.playerId(), player -> {
            EggInventoryMutationResult result;
            try {
                result = inventoryEscrow.refundOne(
                        transaction.item(), new PaperPlayerEggInventory(player, codec));
            } catch (RuntimeException failure) {
                plugin.getLogger().warning("OmniPet escrow refund failed for "
                        + transaction.transactionId() + ": " + failure.getMessage());
                return;
            }
            if (result == EggInventoryMutationResult.REFUNDED
                    || result == EggInventoryMutationResult.ALREADY_PRESENT) {
                submit(transaction.playerId(), () -> markRefunded(transaction));
            }
        });
    }

    private void markRefunded(EggEscrowTransaction transaction) {
        try {
            services.itemEscrow().markRefunded(transaction.transactionId());
        } catch (IOException | RuntimeException failure) {
            plugin.getLogger().warning("OmniPet escrow refund acknowledgement deferred for "
                    + transaction.transactionId() + ": " + failure.getMessage());
        }
    }

    private void cancelIncubation(EggEscrowTransaction transaction, PlayerState player) throws IOException {
        HatchResult result = services.hatches().cancel(
                transaction.playerId(),
                player.revision(),
                transaction.incubationId(),
                IncubationActionTokens.cancel(transaction.transactionId()));
        if (result.status() != HatchResult.Status.CANCELLED
                && result.status() != HatchResult.Status.ALREADY_APPLIED
                && result.status() != HatchResult.Status.NO_INCUBATION) {
            plugin.getLogger().fine("OmniPet incubation cancellation deferred for "
                    + transaction.transactionId() + ": " + result.status());
        }
    }

    private void runMain(UUID playerId, java.util.function.Consumer<Player> callback) {
        if (closed || !plugin.isEnabled()) return;
        try {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null && player.isOnline()) callback.accept(player);
            });
        } catch (RuntimeException failure) {
            if (!closed) plugin.getLogger().warning("OmniPet recovery main-thread dispatch failed: "
                    + failure.getMessage());
        }
    }

    private boolean submit(UUID playerId, Runnable task) {
        try {
            boolean accepted = tasks.submit(playerId, task);
            if (!accepted && !closed) {
                plugin.getLogger().warning("OmniPet recovery work was rejected for " + playerId);
            }
            return accepted;
        } catch (RuntimeException failure) {
            if (!closed) plugin.getLogger().warning("OmniPet recovery queue failed for " + playerId
                    + ": " + failure.getMessage());
            return false;
        }
    }
}
