package io.github.salyvn.omnipet.paper.skill;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;

import com.destroystokyo.paper.event.player.PlayerJumpEvent;

import io.github.salyvn.omnipet.core.skill.SkillTrigger;

/**
 * Turns the things a player does into {@link SkillTrigger}s.
 *
 * <p>One listener rather than one per trigger, because the mapping is the interesting part and splitting it
 * across a dozen classes would hide it. Every handler is {@code MONITOR}/{@code ignoreCancelled} except the
 * two that cancel the vanilla action they hijack: dropping an item and swapping hands are bound to keys a
 * player will press for the skill, so leaving the drop or the swap to happen as well would make those
 * triggers unusable.
 *
 * <p>{@link SkillTrigger#ON_LOW_HEALTH} is edge-triggered here rather than in the binding: it fires when the
 * owner crosses below the threshold and re-arms only once they heal back above it. A level-triggered version
 * would fire on every hit taken while low, which is exactly when a player can least afford the noise, and
 * the cooldown alone would not fix it because the intent is "when this happens", not "every so often while
 * it is true".
 *
 * <p>The threshold is per-binding, so the crossing has to be tracked per binding as well; this listener
 * keeps only the coarser "is this owner currently below any of their thresholds" latch, which the controller
 * refines. Owners are evicted on quit so the map cannot grow for the server's lifetime.
 */
public final class SkillTriggerListener implements Listener {
    private final PaperActiveSkillController skills;
    private final java.util.function.Function<UUID, Double> lowHealthThreshold;
    private final Map<UUID, Boolean> belowThreshold = new ConcurrentHashMap<>();

    /**
     * @param lowHealthThreshold the lowest threshold among an owner's bindings, or null when they have none
     */
    public SkillTriggerListener(
            PaperActiveSkillController skills, java.util.function.Function<UUID, Double> lowHealthThreshold) {
        this.skills = Objects.requireNonNull(skills, "skill controller");
        this.lowHealthThreshold = Objects.requireNonNull(lowHealthThreshold, "low health threshold source");
    }

    // --- active: the owner pressed something ---------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        boolean right = action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
        boolean left = action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK;
        if (!right && !left) return;
        // Only the main hand. An interact fires once per hand, and firing the skill twice for one click
        // would halve every cooldown the operator configured.
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        Player player = event.getPlayer();
        boolean sneaking = player.isSneaking();
        skills.trigger(player, right
                ? (sneaking ? SkillTrigger.SHIFT_RIGHT_CLICK : SkillTrigger.RIGHT_CLICK)
                : (sneaking ? SkillTrigger.SHIFT_LEFT_CLICK : SkillTrigger.LEFT_CLICK));
    }

    /**
     * The drop key, with the drop cancelled.
     *
     * <p>Not {@code MONITOR}: this one changes the outcome. A player who bound a skill to Q should not also
     * lose the item in their hand every time they cast, so the drop is undone. That is only correct because
     * a binding matched — an owner with no drop-key skill keeps their drop.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (skills.hasTrigger(event.getPlayer().getUniqueId(), SkillTrigger.DROP_KEY)) {
            event.setCancelled(true);
            skills.trigger(event.getPlayer(), SkillTrigger.DROP_KEY);
        }
    }

    /** The off-hand swap key, with the swap cancelled for the same reason the drop is. */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        if (skills.hasTrigger(event.getPlayer().getUniqueId(), SkillTrigger.SWAP_HAND_KEY)) {
            event.setCancelled(true);
            skills.trigger(event.getPlayer(), SkillTrigger.SWAP_HAND_KEY);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJump(PlayerJumpEvent event) {
        skills.trigger(event.getPlayer(), SkillTrigger.JUMP);
    }

    /** Beginning to sneak, not ending. A release would fire the skill twice per crouch. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSneak(PlayerToggleSneakEvent event) {
        if (event.isSneaking()) skills.trigger(event.getPlayer(), SkillTrigger.SNEAK);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSprint(PlayerToggleSprintEvent event) {
        if (event.isSprinting()) skills.trigger(event.getPlayer(), SkillTrigger.SPRINT);
    }

    // --- passive: something happened ------------------------------------------------------------

    /**
     * The owner landed a hit, or took one.
     *
     * <p>Both live in one handler because both are the same event seen from opposite ends, and reading them
     * together is the only way to be sure a hit between two players is not counted as both at once for the
     * same owner.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player attacker && !attacker.equals(event.getEntity())) {
            skills.trigger(attacker, SkillTrigger.ON_ATTACK);
        }
        if (event.getEntity() instanceof Player victim) {
            skills.trigger(victim, SkillTrigger.ON_DAMAGE_TAKEN);
        }
    }

    /** Damage from anything at all, so drowning and fall damage count as being hurt. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        // A hit from an entity already fired ON_DAMAGE_TAKEN in the handler above; firing again here would
        // double every such cast.
        if (!(event instanceof EntityDamageByEntityEvent)) {
            skills.trigger(player, SkillTrigger.ON_DAMAGE_TAKEN);
        }
        checkLowHealth(player, event.getFinalDamage());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRegain(EntityRegainHealthEvent event) {
        if (event.getEntity() instanceof Player player) checkLowHealth(player, -event.getAmount());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKill(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer != null) skills.trigger(killer, SkillTrigger.ON_KILL);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTargeted(EntityTargetEvent event) {
        if (event.getTarget() instanceof Player player) skills.trigger(player, SkillTrigger.ON_TARGETED);
    }

    /**
     * The owner died.
     *
     * <p>Cast before they drop, which is why this is on the death event rather than on respawn: a skill that
     * fires after the death screen is a skill nobody sees.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        skills.trigger(event.getPlayer(), SkillTrigger.ON_DEATH);
        belowThreshold.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        belowThreshold.remove(event.getPlayer().getUniqueId());
    }

    /**
     * Fires {@link SkillTrigger#ON_LOW_HEALTH} on the crossing, and re-arms on the way back up.
     *
     * @param delta the health about to be lost, negative when healing, since both events report the change
     *     before it is applied to the entity
     */
    @SuppressWarnings("deprecation")
    private void checkLowHealth(Player player, double delta) {
        Double threshold = lowHealthThreshold.apply(player.getUniqueId());
        if (threshold == null) {
            belowThreshold.remove(player.getUniqueId());
            return;
        }
        // Read through Damageable rather than the max-health Attribute constant: that constant was
        // renamed in Paper 1.21.3 (GENERIC_MAX_HEALTH -> MAX_HEALTH), so either spelling fails to link on
        // one end of the supported 1.21 range, and a Registry lookup by key returns null on the other
        // end, which would silently read every player as 20 max HP. The deprecated accessor is the one
        // path present on every build, and it cannot fall back to a wrong number.
        double maximum = player.getMaxHealth();
        if (maximum <= 0) return;
        double after = Math.max(0, Math.min(maximum, player.getHealth() - delta));
        boolean low = after / maximum <= threshold;
        boolean was = Boolean.TRUE.equals(belowThreshold.put(player.getUniqueId(), low));
        if (low && !was) skills.trigger(player, SkillTrigger.ON_LOW_HEALTH);
    }
}
