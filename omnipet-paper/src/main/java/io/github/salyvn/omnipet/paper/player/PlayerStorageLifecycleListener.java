package io.github.salyvn.omnipet.paper.player;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PlayerStorageLifecycleListener implements Listener {
    private final PlayerPetController controller;

    public PlayerStorageLifecycleListener(PlayerPetController controller) {
        this.controller = controller;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        controller.reconcile(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onKick(PlayerKickEvent event) {
        controller.release(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        controller.release(event.getPlayer().getUniqueId());
    }
}
