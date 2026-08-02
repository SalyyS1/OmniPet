package io.github.salyvn.omnipet.paper.render;

import java.util.Objects;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;

import io.github.salyvn.omnipet.paper.runtime.PaperPetRuntimeCoordinator;

public final class RendererProviderLifecycleListener implements Listener {
    private final PaperPetRuntimeCoordinator runtime;

    public RendererProviderLifecycleListener(PaperPetRuntimeCoordinator runtime) {
        this.runtime = Objects.requireNonNull(runtime, "pet runtime");
    }

    @EventHandler
    public void onEnable(PluginEnableEvent event) {
        if (event.getPlugin().getName().equalsIgnoreCase("ModelEngine")) runtime.reload();
    }

    @EventHandler
    public void onDisable(PluginDisableEvent event) {
        if (event.getPlugin().getName().equalsIgnoreCase("ModelEngine")) runtime.reload();
    }
}
