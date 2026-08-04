package io.github.salyvn.omnipet.paper.gui.player;

import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;

import net.kyori.adventure.text.Component;

import io.github.salyvn.omnipet.paper.gui.GuiColors;
import io.github.salyvn.omnipet.paper.config.GuiSettings;
import io.github.salyvn.omnipet.paper.gui.GuiItems;
import io.github.salyvn.omnipet.paper.gui.MenuMaterials;
import io.github.salyvn.omnipet.paper.text.Displays;
import io.github.salyvn.omnipet.paper.text.MessageKey;
import io.github.salyvn.omnipet.paper.text.Messages;

/**
 * The vault's bottom control row and its three distinct empty/end states.
 *
 * <p>Extracted from {@link PlayerPetMenuRenderer} to keep that file inside the project's 200-line
 * limit once sort, filter, and honest state messages were added.
 *
 * <p>Occupied slots are 45, 48, 49, 50, and 53 — paging, hub, status, and slot purchase. Sort takes
 * 46 and filter takes 47, the only two action-free slots left in the row. "Free" means action-free
 * rather than item-free, since the renderer paints all 54 slots with filler first.
 */
final class VaultMenuControls {
    static final int SORT_SLOT = 46;
    static final int FILTER_SLOT = 47;
    private static final int STATE_SLOT = 22;

    private VaultMenuControls() {}

    /**
     * The vault's background pane.
     *
     * <p>Lives here rather than in the renderer because both paint it, and an operator changing the
     * vault's filler expects one setting to cover the whole menu.
     */
    static org.bukkit.inventory.ItemStack filler() {
        if (GuiSettings.gui().menu("vault").filler().isEmpty()) return GuiItems.filler();
        return GuiItems.of(
                MenuMaterials.filler("vault", Material.GRAY_STAINED_GLASS_PANE),
                GuiItems.label(" ", GuiItems.LORE_COLOR), java.util.List.of());
    }

    static void paint(
            Inventory inventory,
            Map<Integer, PlayerPetInventoryHolder.Action> actions,
            VaultViewState state,
            VaultPetView view) {
        actions.put(SORT_SLOT, PlayerPetInventoryHolder.Action.sort());
        inventory.setItem(SORT_SLOT, GuiItems.of(
                MenuMaterials.of("vault", "sort", Material.HOPPER),
                Messages.line(MessageKey.GUI_VAULT_SORT,
                        Messages.of("status", Displays.words(state.sort()))),
                List.of(
                        Messages.line(MessageKey.GUI_VAULT_MATCHED,
                                Messages.of("amount", view.matchedCount()),
                                Messages.of("total", view.ownedCount())),
                        Component.empty(),
                        Messages.line(MessageKey.GUI_VAULT_SORT_HINT,
                                Messages.of("detail", Displays.words(state.sort().next()))))));

        actions.put(FILTER_SLOT, PlayerPetInventoryHolder.Action.filter());
        inventory.setItem(FILTER_SLOT, GuiItems.of(
                MenuMaterials.of("vault", "filter", Material.SPYGLASS),
                Messages.line(MessageKey.GUI_VAULT_FILTER,
                        Messages.of("status", Displays.words(state.filter()))),
                List.of(Messages.line(MessageKey.GUI_VAULT_FILTER_HINT,
                        Messages.of("detail", Displays.words(state.filter().next()))))));
    }

    /**
     * The disabled-looking NEXT control on the final page. The arrow previously vanished, which reads
     * as a glitch rather than as "there is nothing after this".
     */
    static void paintLastPage(Inventory inventory, VaultPetView view) {
        inventory.setItem(53, GuiItems.of(
                Material.GRAY_STAINED_GLASS_PANE,
                Messages.line(MessageKey.GUI_VAULT_LAST_PAGE),
                List.of(Messages.line(MessageKey.GUI_VAULT_LAST_PAGE_HINT,
                        Messages.of("page", view.page()),
                        Messages.of("pages", view.pages())))));
    }

    /**
     * Owning no pets and filtering everything out are different problems, so they get different
     * messages. Conflating them leaves a player who filtered to favorites believing their vault is
     * empty.
     */
    static void paintEmptyState(Inventory inventory, VaultViewState state, VaultPetView view) {
        if (view.empty()) {
            inventory.setItem(STATE_SLOT, GuiItems.of(
                    Material.ENDER_CHEST,
                    Messages.line(MessageKey.GUI_VAULT_EMPTY).color(GuiColors.WARNING),
                    List.of(Messages.line(MessageKey.GUI_VAULT_EMPTY_HINT))));
            return;
        }
        if (!view.noMatches()) return;
        inventory.setItem(STATE_SLOT, GuiItems.of(
                Material.BARRIER,
                Messages.line(MessageKey.GUI_VAULT_NO_MATCHES,
                        Messages.of("status", Displays.words(state.filter()))).color(GuiColors.WARNING),
                List.of(Messages.line(MessageKey.GUI_VAULT_NO_MATCHES_HINT,
                        Messages.of("total", view.ownedCount())))));
    }

    /** Overflow lore that names both ways out instead of showing a bare count. */
    static List<Component> overflowLore() {
        return List.of(
                Messages.line(MessageKey.GUI_VAULT_OVERFLOW),
                Messages.line(MessageKey.GUI_VAULT_OVERFLOW_RELEASE),
                Messages.line(MessageKey.GUI_VAULT_OVERFLOW_CAPACITY));
    }
}
