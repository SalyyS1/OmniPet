package io.github.salyvn.omnipet.paper.skill;

import java.util.Objects;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;

public final class MythicMobsSkillLifecycleListener implements Listener {
    private final PaperMythicMobsSkillContext context;

    public MythicMobsSkillLifecycleListener(PaperMythicMobsSkillContext context) {
        this.context = Objects.requireNonNull(context, "MythicMobs skill context");
    }

    @EventHandler
    public void onEnable(PluginEnableEvent event) {
        if (event.getPlugin().getName().equalsIgnoreCase("MythicMobs")) context.refresh();
    }

    @EventHandler
    public void onDisable(PluginDisableEvent event) {
        if (event.getPlugin().getName().equalsIgnoreCase("MythicMobs")) context.refresh();
    }
}
