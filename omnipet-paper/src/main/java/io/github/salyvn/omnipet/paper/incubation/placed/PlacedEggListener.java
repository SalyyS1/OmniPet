package io.github.salyvn.omnipet.paper.incubation.placed;

import java.util.Objects;
import java.util.Optional;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;

import io.github.salyvn.omnipet.paper.feedback.Feedback;
import io.github.salyvn.omnipet.paper.feedback.FeedbackEvent;
import io.github.salyvn.omnipet.paper.incubation.PaperEggItemCodec;
import io.github.salyvn.omnipet.paper.text.MessageKey;
import io.github.salyvn.omnipet.paper.text.Messages;

/**
 * Placing an OmniPet egg beside a heat source starts it incubating in the world.
 *
 * <p>Replaces the guard that simply cancelled placement. That guard existed because placing an egg
 * converted it into an ordinary block and destroyed the identity in its persistent data — the egg was lost
 * with no message. Placement is now recorded durably instead, so the identity survives in the record and
 * the block is a legitimate second way to incubate.
 *
 * <p>Placement is still refused when the surroundings do not satisfy the egg — a fire-affinity egg wants
 * lava all round — and the refusal says what is missing rather than failing mutely.
 */
public final class PlacedEggListener implements Listener {
    private final PaperEggItemCodec codec;
    private final PlacedEggCoordinator coordinator;
    private final PlacedEggView view;

    public PlacedEggListener(
            PaperEggItemCodec codec, PlacedEggCoordinator coordinator, PlacedEggView view) {
        this.codec = Objects.requireNonNull(codec, "egg item codec");
        this.coordinator = Objects.requireNonNull(coordinator, "placed egg coordinator");
        this.view = Objects.requireNonNull(view, "placed egg view");
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    public void onPlace(BlockPlaceEvent event) {
        ItemStack inHand = event.getItemInHand();
        if (!codec.carriesEggIdentity(inHand)) return;
        Player player = event.getPlayer();
        Optional<EggIdentityFields> fields = identity(inHand);
        if (fields.isEmpty()) {
            // Ours by key but unreadable: refuse rather than place a record we could not reconstruct.
            refuse(event, player, Messages.line(MessageKey.EGG_NOT_PLACEABLE));
            return;
        }
        PlacedEggCoordinator.Placement placement = coordinator.place(
                event.getBlock(), player.getUniqueId(), fields.get().eggId(), fields.get().nonce(), inHand);
        if (!placement.allowed()) {
            refuse(event, player, Messages.line(MessageKey.EGG_PLACEMENT_REFUSED,
                    Messages.of("detail", placement.reason())));
            return;
        }
        coordinator.at(event.getBlock()).ifPresent(view::refresh);
        player.sendMessage(Messages.line(MessageKey.EGG_PLACED));
        Feedback.emit(player, FeedbackEvent.HATCH_QUEUED);
    }

    /**
     * Breaking a placed egg returns that exact egg.
     *
     * <p>The block drops nothing of its own: the record is consumed first and the stored snapshot is given
     * back, so the returned egg carries the identity it was placed with and a break/place cycle cannot
     * mint a second one.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Optional<PlacedEggRecord> record = coordinator.at(block);
        if (record.isEmpty()) return;
        event.setDropItems(false);
        view.remove(record.get().key());
        Optional<ItemStack> egg = coordinator.reclaim(record.get());
        if (egg.isEmpty()) {
            // The record could not be consumed, so returning an item now might duplicate it. Refusing the
            // break keeps exactly one owner of the egg.
            event.setCancelled(true);
            event.getPlayer().sendMessage(Messages.line(MessageKey.EGG_RECLAIM_FAILED));
            return;
        }
        giveOrDrop(event.getPlayer(), block, egg.get());
        event.getPlayer().sendMessage(Messages.line(MessageKey.EGG_RECLAIMED));
    }

    private void refuse(BlockPlaceEvent event, Player player, net.kyori.adventure.text.Component message) {
        event.setCancelled(true);
        player.sendMessage(message);
        Feedback.emit(player, FeedbackEvent.HATCH_REJECTED);
    }

    /** Hands the egg back, dropping it at the block when the inventory is full so it is never destroyed. */
    private static void giveOrDrop(Player player, Block block, ItemStack egg) {
        int empty = player.getInventory().firstEmpty();
        if (empty < 0) {
            block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), egg);
            return;
        }
        player.getInventory().setItem(empty, egg);
    }

    private Optional<EggIdentityFields> identity(ItemStack item) {
        return codec.observe(item, 0)
                .filter(observed -> observed.identityValid() && observed.nonce() != null)
                .map(observed -> new EggIdentityFields(observed.eggId(), observed.nonce()));
    }

    private record EggIdentityFields(String eggId, java.util.UUID nonce) {}
}
