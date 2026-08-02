package io.github.salyvn.omnipet.paper.incubation;

import java.util.Objects;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import io.github.salyvn.omnipet.paper.player.PlayerHatchController;

public final class IncubationLifecycleListener implements Listener {
    private final PaperIncubationCoordinator coordinator;
    private final PlayerHatchController hatchController;

    public IncubationLifecycleListener(
            PaperIncubationCoordinator coordinator,
            PlayerHatchController hatchController) {
        this.coordinator = Objects.requireNonNull(coordinator, "incubation coordinator");
        this.hatchController = Objects.requireNonNull(hatchController, "hatch controller");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        coordinator.onJoin(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onKick(PlayerKickEvent event) {
        coordinator.onQuit(event.getPlayer().getUniqueId());
        hatchController.release(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        coordinator.onQuit(event.getPlayer().getUniqueId());
        hatchController.release(event.getPlayer().getUniqueId());
    }
}
