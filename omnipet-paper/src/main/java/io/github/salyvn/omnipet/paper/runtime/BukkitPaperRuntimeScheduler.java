package io.github.salyvn.omnipet.paper.runtime;

import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class BukkitPaperRuntimeScheduler implements PaperRuntimeScheduler {
    private final JavaPlugin plugin;

    public BukkitPaperRuntimeScheduler(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "runtime plugin");
    }

    @Override
    public boolean isMainThread() {
        return Bukkit.isPrimaryThread();
    }

    @Override
    public ScheduledTask scheduleRepeating(Runnable task, long initialDelayTicks, long periodTicks) {
        BukkitTask scheduled = plugin.getServer().getScheduler().runTaskTimer(
                plugin, Objects.requireNonNull(task, "runtime task"), initialDelayTicks, periodTicks);
        return new ScheduledTask() {
            @Override public void cancel() { scheduled.cancel(); }
            @Override public boolean cancelled() { return scheduled.isCancelled(); }
        };
    }
}
