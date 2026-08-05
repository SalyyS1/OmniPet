package io.github.salyvn.omnipet.paper.gui.egg;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;

import io.github.salyvn.omnipet.paper.gui.GuiColors;
import io.github.salyvn.omnipet.paper.gui.GuiItems;
import io.github.salyvn.omnipet.paper.gui.MenuMaterials;
import io.github.salyvn.omnipet.paper.text.Durations;
import io.github.salyvn.omnipet.paper.text.MessageKey;
import io.github.salyvn.omnipet.paper.text.Messages;

/**
 * The menu shown when a player right-clicks an egg incubating in the world.
 *
 * <p>Three things: how long is left, and one button per hand for spending a support item. There are no
 * storage slots — see {@link PlacedEggInventoryHolder} for why a container would be a way to lose a paid
 * item.
 *
 * <p>A hand holding nothing useful still gets a button, greyed out and saying why. Hiding it would leave a
 * player who is holding the right item in the wrong hand with no clue what to do.
 */
public final class PlacedEggMenuRenderer {
    private static final int STATUS_SLOT = 4;
    private static final int MAIN_HAND_SLOT = 11;
    private static final int OFF_HAND_SLOT = 15;
    private static final int REFRESH_SLOT = 22;
    private static final int SIZE = 27;

    public Inventory render(
            Player player,
            String recordKey,
            long remainingMillis,
            boolean owner,
            PlacedEggSupportOffer mainHand,
            PlacedEggSupportOffer offHand) {
        Map<Integer, PlacedEggInventoryHolder.Action> actions = new HashMap<>();
        PlacedEggInventoryHolder holder = new PlacedEggInventoryHolder(
                player.getUniqueId(), recordKey, remainingMillis, actions);
        Inventory inventory = Bukkit.createInventory(
                holder, SIZE, Messages.line(MessageKey.GUI_TITLE_PLACED_EGG));
        holder.bind(inventory);
        fill(inventory);

        inventory.setItem(STATUS_SLOT, status(remainingMillis, owner));
        // Someone else's egg gets the countdown and nothing else: they can look, not spend.
        if (owner) {
            paintHand(inventory, actions, MAIN_HAND_SLOT, mainHand, false);
            paintHand(inventory, actions, OFF_HAND_SLOT, offHand, true);
            actions.put(REFRESH_SLOT, PlacedEggInventoryHolder.Action.refresh());
            inventory.setItem(REFRESH_SLOT, GuiItems.of(
                    MenuMaterials.of("placedEgg", "refresh", Material.SUNFLOWER),
                    Messages.line(MessageKey.GUI_PLACED_EGG_REFRESH),
                    List.of(Messages.line(MessageKey.GUI_PLACED_EGG_REFRESH_HINT))));
        } else {
            actions.put(REFRESH_SLOT, PlacedEggInventoryHolder.Action.hub());
            inventory.setItem(REFRESH_SLOT, GuiItems.of(
                    MenuMaterials.of("placedEgg", "hub", Material.COMPASS),
                    Messages.line(MessageKey.HUB_BACK), List.of()));
        }
        return inventory;
    }

    /**
     * The countdown, or the claim instruction once it has run out.
     *
     * <p>A ready egg is claimed by breaking the block, not from here. Adding a claim button would give the
     * grant two entry points, and the break path already returns the pet and clears the block in one
     * ordered step.
     */
    private static ItemStack status(long remainingMillis, boolean owner) {
        List<Component> lore = new ArrayList<>();
        boolean ready = remainingMillis <= 0;
        lore.add(ready
                ? Messages.line(MessageKey.GUI_PLACED_EGG_READY)
                : Messages.line(MessageKey.GUI_PLACED_EGG_REMAINING,
                        Messages.of("remaining", Durations.countdown(remainingMillis))));
        if (!owner) {
            lore.add(Component.empty());
            lore.add(Messages.line(MessageKey.GUI_PLACED_EGG_OWNER_NOTE));
        }
        return GuiItems.of(
                MenuMaterials.of("placedEgg", "status", ready ? Material.LIME_DYE : Material.TURTLE_EGG),
                Messages.line(MessageKey.GUI_PLACED_EGG_STATUS)
                        .color(ready ? GuiColors.POSITIVE : GuiColors.WARNING),
                lore);
    }

    /**
     * One hand's button: what it holds, what that would do, and whether clicking spends it.
     *
     * <p>Clickable only when spending would change something. A usable item against a ready egg is not
     * clickable, so a player cannot burn an instant-hatch on an egg that is already waiting for them.
     */
    private static void paintHand(
            Inventory inventory,
            Map<Integer, PlacedEggInventoryHolder.Action> actions,
            int slot,
            PlacedEggSupportOffer offer,
            boolean offHand) {
        PlacedEggSupportOffer resolved = offer == null
                ? PlacedEggSupportOffer.empty(0) : offer;
        boolean usable = resolved.usable();
        if (usable) {
            actions.put(slot, offHand
                    ? PlacedEggInventoryHolder.Action.redeemOffHand()
                    : PlacedEggInventoryHolder.Action.redeemMain());
        }
        inventory.setItem(slot, GuiItems.of(
                handMaterial(resolved, usable),
                Messages.line(offHand
                                ? MessageKey.GUI_PLACED_EGG_OFF_HAND
                                : MessageKey.GUI_PLACED_EGG_MAIN_HAND)
                        .color(usable ? GuiColors.POSITIVE : GuiColors.DISABLED),
                handLore(resolved, usable)));
    }

    private static Material handMaterial(PlacedEggSupportOffer offer, boolean usable) {
        if (!usable) return MenuMaterials.of("placedEgg", "handIdle", Material.GRAY_STAINED_GLASS_PANE);
        return offer.kind() == PlacedEggSupportOffer.Kind.INSTANT
                ? MenuMaterials.of("placedEgg", "instant", Material.NETHER_STAR)
                : MenuMaterials.of("placedEgg", "reducer", Material.CLOCK);
    }

    private static List<Component> handLore(PlacedEggSupportOffer offer, boolean usable) {
        List<Component> lore = new ArrayList<>();
        switch (offer.kind()) {
            case EMPTY -> lore.add(Messages.line(MessageKey.GUI_PLACED_EGG_HAND_EMPTY));
            case UNSUPPORTED -> lore.add(Messages.line(MessageKey.GUI_PLACED_EGG_HAND_UNSUPPORTED));
            // The item's own figure, not the clamped one: a player must not be told their accelerator
            // is smaller than it is just because this egg has less time left than it covers.
            case REDUCER -> lore.add(Messages.line(MessageKey.GUI_PLACED_EGG_WOULD_REDUCE,
                    Messages.of("detail", Durations.countdown(offer.effectMillis()))));
            case INSTANT -> lore.add(Messages.line(MessageKey.GUI_PLACED_EGG_WOULD_FINISH));
        }
        if (offer.finishes() && offer.kind() == PlacedEggSupportOffer.Kind.REDUCER) {
            lore.add(Messages.line(MessageKey.GUI_PLACED_EGG_WOULD_FINISH));
        }
        if (usable) {
            lore.add(Component.empty());
            lore.add(Messages.line(MessageKey.GUI_PLACED_EGG_SPEND_HINT));
        }
        return lore;
    }

    private static void fill(Inventory inventory) {
        ItemStack pane = GuiItems.of(
                MenuMaterials.filler("placedEgg", Material.GRAY_STAINED_GLASS_PANE),
                GuiItems.label(" ", GuiItems.LORE_COLOR), List.of());
        for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, pane);
    }
}
