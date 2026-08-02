package io.github.salyvn.omnipet.paper.buff;

import java.util.Objects;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;

public final class MythicLibBuffLifecycleListener implements Listener {
    private final PaperOwnerBuffCoordinator coordinator;

    public MythicLibBuffLifecycleListener(PaperOwnerBuffCoordinator coordinator) {
        this.coordinator = Objects.requireNonNull(coordinator, "owner buff coordinator");
    }

    @EventHandler
    public void onEnable(PluginEnableEvent event) {
        if (event.getPlugin().getName().equalsIgnoreCase("MythicLib")) coordinator.refreshProvider();
    }

    @EventHandler
    public void onDisable(PluginDisableEvent event) {
        if (event.getPlugin().getName().equalsIgnoreCase("MythicLib")) coordinator.refreshProvider();
    }
}
