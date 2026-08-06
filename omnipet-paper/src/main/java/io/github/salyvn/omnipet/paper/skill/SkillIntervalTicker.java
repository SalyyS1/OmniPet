package io.github.salyvn.omnipet.paper.skill;

import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import io.github.salyvn.omnipet.core.skill.SkillTrigger;

/**
 * Fires {@link SkillTrigger#INTERVAL} for owners who have a skill on it.
 *
 * <p>Ticks once a second and lets each binding's own cooldown do the pacing, rather than scheduling a task
 * per binding. A per-binding schedule would mean one task per pet per skill per online player, and the
 * cooldown machinery already refuses a cast that is too early — so the extra precision would buy nothing but
 * bookkeeping.
 *
 * <p>Only owners already known to have an interval binding are considered, which is why this reads the
 * controller's trigger index rather than player state: a tick that hit disk once a second per player would
 * be a far worse idea than the imprecision it fixed.
 */
public final class SkillIntervalTicker implements AutoCloseable {
    /** One second. Fine enough that a 2s interval is honoured, coarse enough to cost nothing. */
    private static final long PERIOD_TICKS = 20;

    private final JavaPlugin plugin;
    private final PaperActiveSkillController skills;
    private BukkitTask task;

    public SkillIntervalTicker(JavaPlugin plugin, PaperActiveSkillController skills) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.skills = Objects.requireNonNull(skills, "skill controller");
    }

    public synchronized void start() {
        if (task != null) return;
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, PERIOD_TICKS, PERIOD_TICKS);
    }

    private void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (skills.hasTrigger(player.getUniqueId(), SkillTrigger.INTERVAL)) {
                skills.trigger(player, SkillTrigger.INTERVAL);
            }
        }
    }

    @Override
    public synchronized void close() {
        if (task != null) task.cancel();
        task = null;
    }
}
