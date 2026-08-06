package io.github.salyvn.omnipet.paper.skill;

import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import io.github.salyvn.omnipet.core.skill.SkillProvider;

/** Immutable-epoch provider context refreshed only on lifecycle/reload boundaries. */
public final class PaperMythicMobsSkillContext {
    private final JavaPlugin plugin;
    private long epoch;
    private ReflectiveMythicMobsSkillProvider provider;

    public PaperMythicMobsSkillContext(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    public synchronized void refresh() {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("skill provider refresh requires main thread");
        Plugin mythic = plugin.getServer().getPluginManager().getPlugin("MythicMobs");
        ClassLoader loader = mythic == null ? plugin.getClass().getClassLoader() : mythic.getClass().getClassLoader();
        provider = new ReflectiveMythicMobsSkillProvider(
                loader,
                () -> mythic != null && mythic.isEnabled(),
                Bukkit::isPrimaryThread,
                ownerId -> Bukkit.getPlayer(ownerId),
                targetId -> Bukkit.getEntity(targetId));
        provider.refresh(++epoch);
    }

    public synchronized SkillProvider provider() {
        if (provider == null) throw new IllegalStateException("skill provider context has not been refreshed");
        return provider;
    }
}
