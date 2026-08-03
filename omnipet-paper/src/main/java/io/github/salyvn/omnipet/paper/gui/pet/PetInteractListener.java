package io.github.salyvn.omnipet.paper.gui.pet;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;

import io.github.salyvn.omnipet.core.runtime.InteractionIdentity;
import io.github.salyvn.omnipet.paper.feedback.Feedback;
import io.github.salyvn.omnipet.paper.feedback.FeedbackEvent;

/**
 * Right-clicking your own rendered pet opens its management screen.
 *
 * <p>Only {@link PlayerInteractEntityEvent} is registered. {@code PlayerInteractAtEntityEvent} has its
 * own {@code HandlerList} despite extending this event, so a handler registered here never receives
 * it — registering both would deliver two events for one click.
 */
public final class PetInteractListener implements Listener {
    private final Function<UUID, Optional<InteractionIdentity>> lookup;
    private final PetManagementTarget management;

    /**
     * @param lookup entity ID to the pet it belongs to. Must already reject stale renderer
     *     generations, so a leftover entity from a previous render reads as not-ours.
     */
    public PetInteractListener(
            Function<UUID, Optional<InteractionIdentity>> lookup,
            PetManagementTarget management) {
        this.lookup = Objects.requireNonNull(lookup, "interaction lookup");
        this.management = Objects.requireNonNull(management, "management target");
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onRightClick(PlayerInteractEntityEvent event) {
        // Guard #1, and the most important one: this event fires once per hand. Without the filter a
        // single right-click opens the screen twice, and the second open trips the management
        // concurrency guard, so the player sees "Another management request is already processing."
        // on every legitimate click.
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        InteractionIdentity identity = lookup.apply(event.getRightClicked().getUniqueId()).orElse(null);
        // Not ours: return without cancelling, so another plugin's entity behaves normally.
        if (identity == null) return;
        // Only now do we claim the click, suppressing the vanilla interaction on our own entity.
        event.setCancelled(true);
        // Ownership is enforced inside the management controller, which passes the viewer as the owner,
        // so another player's pet is structurally unreachable rather than merely checked here.
        if (!identity.ownerId().equals(player.getUniqueId())) return;

        Feedback.progress(player, FeedbackEvent.PET_MANAGEMENT_OPENED);
        management.open(player, identity.petInstanceId());
    }

    /** The management screen entry point, kept as a seam so the listener is testable. */
    @FunctionalInterface
    public interface PetManagementTarget {
        void open(Player player, UUID petId);
    }
}
