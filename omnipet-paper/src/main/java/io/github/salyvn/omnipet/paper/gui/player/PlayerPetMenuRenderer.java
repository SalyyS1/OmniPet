package io.github.salyvn.omnipet.paper.gui.player;

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

import io.github.salyvn.omnipet.core.domain.PetInstance;
import io.github.salyvn.omnipet.core.storage.PetStorageSnapshot;
import io.github.salyvn.omnipet.paper.gui.GuiColors;
import io.github.salyvn.omnipet.paper.gui.GuiItems;
import io.github.salyvn.omnipet.paper.text.Displays;
import io.github.salyvn.omnipet.paper.text.MessageKey;
import io.github.salyvn.omnipet.paper.text.Messages;

public final class PlayerPetMenuRenderer {
    private static final int PETS_PER_PAGE = 45;

    public Inventory render(Player player, PetStorageSnapshot snapshot, int requestedPage) {
        int pages = Math.max(1, (snapshot.pets().size() + PETS_PER_PAGE - 1) / PETS_PER_PAGE);
        int page = Math.max(1, Math.min(requestedPage, pages));
        Map<Integer, PlayerPetInventoryHolder.Action> actions = new HashMap<>();
        PlayerPetInventoryHolder holder = new PlayerPetInventoryHolder(
                player.getUniqueId(), snapshot.revision(), page, actions);
        Inventory inventory = Bukkit.createInventory(holder, 54, Messages.line(
                MessageKey.GUI_TITLE_VAULT, Messages.of("page", page), Messages.of("pages", pages)));
        holder.bind(inventory);
        fill(inventory);

        int start = (page - 1) * PETS_PER_PAGE;
        for (int index = start; index < Math.min(start + PETS_PER_PAGE, snapshot.pets().size()); index++) {
            PetInstance pet = snapshot.pets().get(index);
            boolean active = snapshot.desiredActivePetIds().contains(pet.id());
            int slot = index - start;
            actions.put(slot, PlayerPetInventoryHolder.Action.pet(pet.id(), active));
            inventory.setItem(slot, petRow(pet, active));
        }

        if (page > 1) {
            actions.put(45, PlayerPetInventoryHolder.Action.previous());
            inventory.setItem(45, GuiItems.of(Material.ARROW,
                    Messages.line(MessageKey.GUI_VAULT_PREVIOUS), List.of()));
        }
        if (page < pages) {
            actions.put(53, PlayerPetInventoryHolder.Action.next());
            inventory.setItem(53, GuiItems.of(Material.ARROW,
                    Messages.line(MessageKey.GUI_VAULT_NEXT), List.of()));
        }
        inventory.setItem(49, vaultStatus(snapshot));
        // Slot 48 is free: 45/49/50/53 are taken by paging, status, and slot purchase.
        actions.put(48, PlayerPetInventoryHolder.Action.hub());
        inventory.setItem(48, GuiItems.of(Material.COMPASS,
                Messages.line(MessageKey.HUB_BACK), List.of()));
        actions.put(50, PlayerPetInventoryHolder.Action.purchaseSlot());
        inventory.setItem(50, GuiItems.of(
                Material.EXPERIENCE_BOTTLE,
                Messages.line(MessageKey.GUI_VAULT_UNLOCK_SLOT),
                List.of(Messages.line(MessageKey.GUI_VAULT_UNLOCK_HINT))));
        return inventory;
    }

    /**
     * Pet name, then level and rarity where present, then the action hints.
     *
     * <p>Values come from {@link VaultPetSummary}, which omits what it cannot read rather than
     * throwing, so one malformed legacy pet cannot blank the page. The previous truncated instance
     * UUID is gone — it was noise for players; full UUIDs remain in the management screen.
     */
    private static ItemStack petRow(PetInstance pet, boolean active) {
        VaultPetSummary summary = VaultPetSummary.of(pet);
        List<Component> lore = new ArrayList<>();
        summary.level().ifPresent(level -> lore.add(
                Messages.line(MessageKey.GUI_VAULT_PET_LEVEL, Messages.of("level", level))));
        summary.rarity().ifPresent(rarity -> lore.add(Messages.line(
                MessageKey.GUI_VAULT_PET_RARITY, Messages.of("status", Displays.identifier(rarity)))));
        // Value lines first, spacer, then the action hints last so players always read actions in
        // the same position across every menu.
        lore.add(Component.empty());
        lore.add(Messages.line(active
                ? MessageKey.GUI_VAULT_PET_RECALL
                : MessageKey.GUI_VAULT_PET_ACTIVATE));
        lore.add(Messages.line(MessageKey.GUI_VAULT_PET_MANAGE));
        return GuiItems.of(
                active ? Material.LIME_DYE : Material.PLAYER_HEAD,
                GuiItems.label(pet.definitionId(), active ? GuiColors.POSITIVE : GuiColors.ACCENT),
                lore);
    }

    private static ItemStack vaultStatus(PetStorageSnapshot snapshot) {
        boolean overflow = snapshot.vaultOverflow() > 0;
        List<Component> lore = new ArrayList<>();
        lore.add(Messages.line(MessageKey.GUI_VAULT_OWNED,
                Messages.of("amount", snapshot.ownedCount()),
                Messages.of("total", snapshot.effectiveVaultCapacity())));
        lore.add(Messages.line(MessageKey.GUI_VAULT_ACTIVE,
                Messages.of("amount", snapshot.desiredActivePetIds().size()),
                Messages.of("total", snapshot.effectiveActiveSlotCount())));
        lore.add(Component.empty());
        lore.add(Messages.line(overflow
                ? MessageKey.GUI_VAULT_OVERFLOW
                : MessageKey.GUI_VAULT_PROVIDER_NOTE));
        return GuiItems.of(
                overflow ? Material.RED_STAINED_GLASS : Material.ENDER_CHEST,
                Messages.line(MessageKey.GUI_VAULT_STATUS).color(GuiColors.availability(!overflow)),
                lore);
    }

    private static void fill(Inventory inventory) {
        ItemStack pane = GuiItems.filler();
        for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, pane);
    }
}
