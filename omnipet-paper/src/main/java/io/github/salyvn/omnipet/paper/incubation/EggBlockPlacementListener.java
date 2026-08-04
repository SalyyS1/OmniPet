package io.github.salyvn.omnipet.paper.incubation;

import java.util.Objects;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

import io.github.salyvn.omnipet.paper.feedback.Feedback;
import io.github.salyvn.omnipet.paper.feedback.FeedbackEvent;
import io.github.salyvn.omnipet.paper.text.MessageKey;
import io.github.salyvn.omnipet.paper.text.Messages;

/**
 * Stops an OmniPet egg from being placed as a block.
 *
 * <p>The default egg material is {@code TURTLE_EGG}, which is placeable. Placing one converted the
 * item into an ordinary block and destroyed its persistent identity — the nonce and schema the escrow
 * saga matches on — so the egg was silently and permanently lost with no message and no way to
 * recover it. An operator can configure any material, including other placeable ones, so this guards
 * on OmniPet identity rather than on a material list.
 *
 * <p>Only cancels placement of items OmniPet owns. A vanilla turtle egg, and every other block, is
 * left entirely alone so no other plugin's behaviour changes.
 */
public final class EggBlockPlacementListener implements Listener {
    private final PaperEggItemCodec codec;

    public EggBlockPlacementListener(PaperEggItemCodec codec) {
        this.codec = Objects.requireNonNull(codec, "egg item codec");
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    public void onPlace(BlockPlaceEvent event) {
        if (!codec.carriesEggIdentity(event.getItemInHand())) return;
        event.setCancelled(true);
        // The player tried to do something reasonable with an item that gives no hint it is special, so
        // say what to do instead rather than failing mutely.
        event.getPlayer().sendMessage(Messages.line(MessageKey.EGG_NOT_PLACEABLE));
        Feedback.emit(event.getPlayer(), FeedbackEvent.HATCH_REJECTED);
    }
}
