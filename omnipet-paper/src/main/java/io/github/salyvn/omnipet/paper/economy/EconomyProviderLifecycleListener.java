package io.github.salyvn.omnipet.paper.economy;

import java.util.Objects;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.java.JavaPlugin;

import io.github.salyvn.omnipet.paper.entitlement.PaperLuckPermsEntitlementRegistry;

/** Clears only adapters owned by a disabling provider and refreshes on the next tick. */
public final class EconomyProviderLifecycleListener implements Listener {
    private final JavaPlugin plugin;
    private final PaperEconomyProviderRegistry registry;
    private final PaperLuckPermsEntitlementRegistry luckPerms;
    private boolean refreshQueued;

    public EconomyProviderLifecycleListener(
            JavaPlugin plugin,
            PaperEconomyProviderRegistry registry,
            PaperLuckPermsEntitlementRegistry luckPerms) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.registry = Objects.requireNonNull(registry, "economy registry");
        this.luckPerms = Objects.requireNonNull(luckPerms, "LuckPerms registry");
    }

    @EventHandler
    public void onPluginEnable(PluginEnableEvent event) {
        scheduleRefresh();
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        String pluginName = event.getPlugin().getName();
        registry.invalidate(pluginName);
        luckPerms.invalidate(pluginName);
        scheduleRefresh();
    }

    private void scheduleRefresh() {
        if (!plugin.isEnabled() || refreshQueued) return;
        refreshQueued = true;
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            refreshQueued = false;
            registry.refresh();
            luckPerms.refresh();
        });
    }
}
