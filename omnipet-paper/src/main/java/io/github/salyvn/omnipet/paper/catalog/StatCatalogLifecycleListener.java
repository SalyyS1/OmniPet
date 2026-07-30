package io.github.salyvn.omnipet.paper.catalog;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;

/** Invalidates optional-provider snapshots when either vendor changes state. */
public final class StatCatalogLifecycleListener implements Listener {
    private final PaperStatCatalogContext context;

    public StatCatalogLifecycleListener(PaperStatCatalogContext context) {
        this.context = context;
    }

    @EventHandler
    public void onPluginEnable(PluginEnableEvent event) {
        invalidate(event.getPlugin().getName());
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        invalidate(event.getPlugin().getName());
    }

    private void invalidate(String pluginName) {
        if (pluginName.equalsIgnoreCase("MythicLib") || pluginName.equalsIgnoreCase("MMOItems")) {
            context.invalidate();
        }
    }
}
