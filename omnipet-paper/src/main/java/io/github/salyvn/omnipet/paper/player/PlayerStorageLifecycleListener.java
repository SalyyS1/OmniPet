package io.github.salyvn.omnipet.paper.player;

import java.util.Objects;
import java.util.function.Consumer;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.entity.Player;

import io.github.salyvn.omnipet.paper.feedback.Feedback;
import io.github.salyvn.omnipet.paper.runtime.PaperPetRuntimeCoordinator;

public final class PlayerStorageLifecycleListener implements Listener {
    private final PlayerPetController controller;
    private final PaperPetRuntimeCoordinator runtime;
    private final Consumer<java.util.UUID> ownerCleanup;
    private final Consumer<Player> joinRecovery;

    public PlayerStorageLifecycleListener(PlayerPetController controller) {
        this(controller, null, ignored -> {}, ignored -> {});
    }

    public PlayerStorageLifecycleListener(
            PlayerPetController controller,
            PaperPetRuntimeCoordinator runtime) {
        this(controller, runtime, ignored -> {}, ignored -> {});
    }

    public PlayerStorageLifecycleListener(
            PlayerPetController controller,
            PaperPetRuntimeCoordinator runtime,
            Consumer<java.util.UUID> ownerCleanup) {
        this(controller, runtime, ownerCleanup, ignored -> {});
    }

    public PlayerStorageLifecycleListener(
            PlayerPetController controller,
            PaperPetRuntimeCoordinator runtime,
            Consumer<java.util.UUID> ownerCleanup,
            Consumer<Player> joinRecovery) {
        this.controller = Objects.requireNonNull(controller, "player pet controller");
        this.runtime = runtime;
        this.ownerCleanup = Objects.requireNonNull(ownerCleanup, "owner cleanup callback");
        this.joinRecovery = Objects.requireNonNull(joinRecovery, "join recovery callback");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        controller.reconcile(event.getPlayer());
        joinRecovery.accept(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onKick(PlayerKickEvent event) {
        controller.release(event.getPlayer().getUniqueId());
        ownerQuit(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        controller.release(event.getPlayer().getUniqueId());
        ownerQuit(event.getPlayer().getUniqueId());
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        ownerMoved(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onWorldChanged(PlayerChangedWorldEvent event) {
        ownerMoved(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        controller.release(event.getEntity().getUniqueId());
        ownerMoved(event.getEntity().getUniqueId());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        controller.reconcile(event.getPlayer());
    }

    private void ownerQuit(java.util.UUID ownerId) {
        if (runtime != null) runtime.ownerQuit(ownerId);
        // Without this the rate limiter retains one entry per player who has ever clicked, for the
        // server's lifetime. Fires on kick as well as quit, since both paths reach here.
        Feedback.release(ownerId);
        ownerCleanup.accept(ownerId);
    }

    private void ownerMoved(java.util.UUID ownerId) {
        if (runtime != null) runtime.ownerWorldChanged(ownerId);
    }
}
