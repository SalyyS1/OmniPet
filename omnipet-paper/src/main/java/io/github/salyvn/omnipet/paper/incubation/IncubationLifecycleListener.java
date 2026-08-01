package io.github.salyvn.omnipet.paper.incubation;

import java.util.Objects;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class IncubationLifecycleListener implements Listener {
    private final PaperIncubationCoordinator coordinator;

    public IncubationLifecycleListener(PaperIncubationCoordinator coordinator) {
        this.coordinator = Objects.requireNonNull(coordinator, "incubation coordinator");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        coordinator.onJoin(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onKick(PlayerKickEvent event) {
        coordinator.onQuit(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        coordinator.onQuit(event.getPlayer().getUniqueId());
    }
}
