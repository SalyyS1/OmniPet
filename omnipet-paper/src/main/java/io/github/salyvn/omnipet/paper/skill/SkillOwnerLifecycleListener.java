package io.github.salyvn.omnipet.paper.skill;

import java.util.Objects;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Keeps the skill controller's trigger index in step with who is online.
 *
 * <p>A join has to build the index before the player's first keypress, because a trigger that cancels a
 * vanilla action — the drop key, the off-hand swap — decides from the index synchronously and an empty index
 * means the skill does not fire. A quit has to drop it, because otherwise the map holds one entry per player
 * the server has ever seen.
 */
public final class SkillOwnerLifecycleListener implements Listener {
    private final PaperActiveSkillController skills;

    public SkillOwnerLifecycleListener(PaperActiveSkillController skills) {
        this.skills = Objects.requireNonNull(skills, "skill controller");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        skills.refreshTriggers(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        skills.forget(event.getPlayer().getUniqueId());
    }
}
