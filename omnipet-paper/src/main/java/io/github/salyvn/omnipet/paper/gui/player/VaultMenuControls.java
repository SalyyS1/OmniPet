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
 *
 * <p>One tile is also painted inside the pet grid: the locked active slot, positioned by
 * {@link VaultPetView#lockedSlotIndex(int)} rather than by a configured index.
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

    /**
     * The locked active slot sitting in the grid, right after the player's last pet.
     *
     * <p>In the grid rather than only in the control row because a player asked to be able to see that
     * there was something to unlock without first opening a menu about it. The row button stays: it is
     * the only entry point when the last page is full, when the vault is empty, and when a filter is
     * hiding everything.
     *
     * <p>Clickable only when a purchase is plausible. A tile that is merely informative — cap reached,
     * nothing priced, permission missing, or multi-pet off — gets no action, so a click on it does not
     * open a menu that would immediately refuse.
     */
    static void paintLockedSlot(
            Inventory inventory,
            Map<Integer, PlayerPetInventoryHolder.Action> actions,
            int slot,
            ActiveSlotStatus status) {
        if (slot < 0 || status == null) return;
        if (!status.multiPetEnabled() && status.locked() == 0) return;
        boolean purchasable = status.purchasable();
        if (purchasable) actions.put(slot, PlayerPetInventoryHolder.Action.purchaseSlot());
        inventory.setItem(slot, GuiItems.of(
                MenuMaterials.of("vault", purchasable ? "lockedSlot" : "lockedSlotIdle",
                        purchasable ? Material.IRON_DOOR : Material.BARRIER),
                lockedTitle(status),
                lockedSlotLore(status, purchasable)));
    }

    /**
     * Names the slot being offered, or says the slots are all open.
     *
     * <p>A number in the title rather than a bare "Locked slot", because "slot 3" is the thing the
     * purchase menu will then talk about and matching the two avoids a player wondering which slot they
     * just bought.
     */
    private static Component lockedTitle(ActiveSlotStatus status) {
        if (status.exhausted()) {
            return Messages.line(MessageKey.GUI_VAULT_SLOT_ALL_UNLOCKED).color(GuiColors.POSITIVE);
        }
        return Messages.line(MessageKey.GUI_VAULT_SLOT_LOCKED,
                Messages.of("amount", status.nextSlot()));
    }

    /**
     * The count, then every price, then one closing line about what a click does.
     *
     * <p>All prices rather than the cheapest: the purchase flow offers each currency as its own choice
     * and never picks for the player, so showing one would misrepresent the next screen.
     */
    private static List<Component> lockedSlotLore(ActiveSlotStatus status, boolean purchasable) {
        List<Component> lore = new java.util.ArrayList<>();
        lore.add(Messages.line(MessageKey.GUI_VAULT_SLOT_UNLOCKED_COUNT,
                Messages.of("amount", status.unlocked()),
                Messages.of("total", status.max())));
        if (!status.costs().isEmpty()) {
            lore.add(Component.empty());
            status.costs().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> lore.add(Messages.line(MessageKey.GUI_VAULT_SLOT_COST_LINE,
                            Messages.of("provider", Displays.of(entry.getKey())),
                            Messages.of("cost", entry.getValue().value().toPlainString()))));
        }
        lore.add(Component.empty());
        if (purchasable) lore.add(Messages.line(MessageKey.GUI_VAULT_SLOT_LOCKED_HINT));
        else if (!status.multiPetEnabled()) lore.add(Messages.line(MessageKey.GUI_VAULT_SLOT_SINGLE_PET));
        else if (status.blockedByPermission()) {
            lore.add(Messages.line(MessageKey.GUI_VAULT_SLOT_LOCKED_PERMISSION));
        } else lore.add(Messages.line(MessageKey.GUI_VAULT_SLOT_NO_UPGRADE));
        return lore;
    }
}
