package io.github.salyvn.omnipet.paper.gui.hub;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;

import net.kyori.adventure.text.Component;

import io.github.salyvn.omnipet.core.domain.incubation.IncubationState;
import io.github.salyvn.omnipet.core.domain.incubation.IncubationStatus;
import io.github.salyvn.omnipet.core.storage.PetStorageSnapshot;
import io.github.salyvn.omnipet.paper.config.GuiSettings;
import io.github.salyvn.omnipet.paper.gui.MenuLayout;
import io.github.salyvn.omnipet.paper.text.Displays;
import io.github.salyvn.omnipet.paper.text.Durations;
import io.github.salyvn.omnipet.paper.text.MessageKey;
import io.github.salyvn.omnipet.paper.text.Messages;

/**
 * The hub: one tile per destination, each with a live summary.
 *
 * <p>Pure rendering. Every value comes from the snapshot the controller already read, so no tile
 * performs I/O.
 *
 * <p>Size, materials, and slots come from {@code gui.menus.hub} when the operator set them, and from
 * the constants below otherwise. Every tile is placed through {@link MenuLayout#put}, which binds the
 * click action and the item together — a moved tile cannot become a drawn button with no action.
 */
public final class HubMenuRenderer {
    /** Built-in layout, used for any slot the operator did not move. */
    private static final int DEFAULT_SIZE = 27;
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
        MenuLayout<HubInventoryHolder.Action> layout = new MenuLayout<>(
                GuiSettings.gui().menu("hub"), "gui.menus.hub", GuiSettings::warn);

        layout.put("vault", VAULT_SLOT, HubInventoryHolder.Action.VAULT,
                Material.ENDER_CHEST, Messages.line(MessageKey.HUB_VAULT), vaultLore(view.storage()));
        hatchTile(layout, view.incubation());
        layout.put("slots", SLOTS_SLOT, HubInventoryHolder.Action.SLOTS,
                Material.EXPERIENCE_BOTTLE, Messages.line(MessageKey.HUB_SLOTS),
                List.of(
                        Messages.line(MessageKey.HUB_SLOTS_COUNT,
                                Messages.of("amount", view.storage().effectiveActiveSlotCount())),
                        Component.empty(),
                        Messages.line(MessageKey.HUB_SLOTS_HINT)));
        layout.put("help", HELP_SLOT, HubInventoryHolder.Action.HELP,
                Material.BOOK, Messages.line(MessageKey.HUB_HELP),
                List.of(Messages.line(MessageKey.HUB_HELP_HINT)));
        if (studioVisible) {
            layout.put("studio", STUDIO_SLOT, HubInventoryHolder.Action.STUDIO,
                    Material.CARTOGRAPHY_TABLE, Messages.line(MessageKey.HUB_STUDIO),
                    List.of(Messages.line(MessageKey.HUB_STUDIO_HINT)));
        }

        // Built after every tile is recorded, so the holder still snapshots a complete action map.
        HubInventoryHolder holder = new HubInventoryHolder(
                view.viewerId(), view.storage().revision(), layout.actions());
        Inventory inventory = Bukkit.createInventory(
                holder, layout.size(DEFAULT_SIZE), Messages.line(MessageKey.GUI_TITLE_HUB));
        holder.bind(inventory);
        layout.draw(inventory);
        return inventory;
    }

    private static List<Component> vaultLore(PetStorageSnapshot storage) {
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
        return lore;
    }

    /**
     * The hatch tile, whose material reflects state.
     *
     * <p>An operator who names one material for this tile gets it in all three states; that is what
     * naming a single material means, and {@link MenuLayout#material} treats the state choice as the
     * fallback rather than the override.
     */
    private static void hatchTile(
            MenuLayout<HubInventoryHolder.Action> layout, IncubationState incubation) {
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
        layout.put("hatch", HATCH_SLOT, HubInventoryHolder.Action.HATCH,
                ready ? Material.LIME_DYE : active ? Material.DRAGON_EGG : Material.EGG,
                Messages.line(MessageKey.HUB_HATCH), lore);
    }
}
