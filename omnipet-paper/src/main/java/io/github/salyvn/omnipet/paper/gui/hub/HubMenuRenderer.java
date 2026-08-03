package io.github.salyvn.omnipet.paper.gui.hub;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;

import io.github.salyvn.omnipet.core.domain.incubation.IncubationState;
import io.github.salyvn.omnipet.core.domain.incubation.IncubationStatus;
import io.github.salyvn.omnipet.core.storage.PetStorageSnapshot;
import io.github.salyvn.omnipet.paper.gui.GuiItems;
import io.github.salyvn.omnipet.paper.text.Displays;
import io.github.salyvn.omnipet.paper.text.Durations;
import io.github.salyvn.omnipet.paper.text.MessageKey;
import io.github.salyvn.omnipet.paper.text.Messages;

/**
 * The 27-slot hub: one tile per destination, each with a live summary.
 *
 * <p>Pure rendering. Every value comes from the snapshot the controller already read, so no tile
 * performs I/O.
 */
public final class HubMenuRenderer {
    /** Slot layout, mirroring the spacing the hatch menu already uses. */
    private static final int VAULT_SLOT = 11;
    private static final int HATCH_SLOT = 13;
    private static final int SLOTS_SLOT = 15;
    private static final int HELP_SLOT = 22;
    private static final int STUDIO_SLOT = 26;

    /**
     * @param studioVisible whether the viewer holds the Studio permission. Checked at render time so
     *     the tile is never visible-but-dead.
     */
    public Inventory render(HubView view, boolean studioVisible) {
        Map<Integer, HubInventoryHolder.Action> actions = new LinkedHashMap<>();
        actions.put(VAULT_SLOT, HubInventoryHolder.Action.VAULT);
        actions.put(HATCH_SLOT, HubInventoryHolder.Action.HATCH);
        actions.put(SLOTS_SLOT, HubInventoryHolder.Action.SLOTS);
        actions.put(HELP_SLOT, HubInventoryHolder.Action.HELP);
        if (studioVisible) actions.put(STUDIO_SLOT, HubInventoryHolder.Action.STUDIO);

        HubInventoryHolder holder = new HubInventoryHolder(
                view.viewerId(), view.storage().revision(), actions);
        Inventory inventory = Bukkit.createInventory(
                holder, 27, Messages.line(MessageKey.GUI_TITLE_HUB));
        holder.bind(inventory);
        for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, GuiItems.filler());

        inventory.setItem(VAULT_SLOT, vaultTile(view.storage()));
        inventory.setItem(HATCH_SLOT, hatchTile(view.incubation()));
        inventory.setItem(SLOTS_SLOT, slotsTile(view.storage()));
        inventory.setItem(HELP_SLOT, GuiItems.of(Material.BOOK,
                Messages.line(MessageKey.HUB_HELP),
                List.of(Messages.line(MessageKey.HUB_HELP_HINT))));
        if (studioVisible) {
            inventory.setItem(STUDIO_SLOT, GuiItems.of(Material.CARTOGRAPHY_TABLE,
                    Messages.line(MessageKey.HUB_STUDIO),
                    List.of(Messages.line(MessageKey.HUB_STUDIO_HINT))));
        }
        return inventory;
    }

    private static ItemStack vaultTile(PetStorageSnapshot storage) {
        List<Component> lore = new ArrayList<>();
        lore.add(Messages.line(MessageKey.HUB_VAULT_OWNED,
                Messages.of("amount", storage.ownedCount()),
                Messages.of("total", storage.effectiveVaultCapacity())));
        lore.add(Messages.line(MessageKey.HUB_VAULT_ACTIVE,
                Messages.of("amount", storage.desiredActivePetIds().size()),
                Messages.of("total", storage.effectiveActiveSlotCount())));
        if (storage.vaultOverflow() > 0) {
            lore.add(Messages.line(MessageKey.HUB_VAULT_OVERFLOW,
                    Messages.of("amount", storage.vaultOverflow())));
        }
        lore.add(Component.empty());
        lore.add(Messages.line(MessageKey.HUB_VAULT_HINT));
        return GuiItems.of(Material.ENDER_CHEST, Messages.line(MessageKey.HUB_VAULT), lore);
    }

    private static ItemStack hatchTile(IncubationState incubation) {
        List<Component> lore = new ArrayList<>();
        boolean active = incubation != null && !incubation.terminal();
        boolean ready = active && incubation.status() == IncubationStatus.READY;
        if (active) {
            lore.add(Messages.line(MessageKey.HUB_HATCH_STATUS,
                    Messages.of("status", Displays.words(incubation.status()))));
            lore.add(ready
                    ? Messages.line(MessageKey.HUB_HATCH_READY)
                    : Messages.line(MessageKey.HUB_HATCH_REMAINING,
                            Messages.of("remaining", Durations.countdown(incubation.remainingActiveMillis()))));
        } else {
            lore.add(Messages.line(MessageKey.HUB_HATCH_IDLE));
        }
        lore.add(Component.empty());
        lore.add(Messages.line(MessageKey.HUB_HATCH_HINT));
        return GuiItems.of(
                ready ? Material.LIME_DYE : active ? Material.DRAGON_EGG : Material.EGG,
                Messages.line(MessageKey.HUB_HATCH),
                lore);
    }

    private static ItemStack slotsTile(PetStorageSnapshot storage) {
        return GuiItems.of(Material.EXPERIENCE_BOTTLE,
                Messages.line(MessageKey.HUB_SLOTS),
                List.of(
                        Messages.line(MessageKey.HUB_SLOTS_COUNT,
                                Messages.of("amount", storage.effectiveActiveSlotCount())),
                        Component.empty(),
                        Messages.line(MessageKey.HUB_SLOTS_HINT)));
    }
}
