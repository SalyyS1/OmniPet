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
import org.bukkit.inventory.meta.ItemMeta;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import io.github.salyvn.omnipet.core.domain.PetInstance;
import io.github.salyvn.omnipet.core.storage.PetStorageSnapshot;

public final class PlayerPetMenuRenderer {
    private static final int PETS_PER_PAGE = 45;

    public Inventory render(Player player, PetStorageSnapshot snapshot, int requestedPage) {
        int pages = Math.max(1, (snapshot.pets().size() + PETS_PER_PAGE - 1) / PETS_PER_PAGE);
        int page = Math.max(1, Math.min(requestedPage, pages));
        Map<Integer, PlayerPetInventoryHolder.Action> actions = new HashMap<>();
        PlayerPetInventoryHolder holder = new PlayerPetInventoryHolder(
                player.getUniqueId(), snapshot.revision(), page, actions);
        Inventory inventory = Bukkit.createInventory(
                holder, 54, Component.text("OmniPet Vault | " + page + "/" + pages, NamedTextColor.GOLD));
        holder.bind(inventory);
        fill(inventory);

        int start = (page - 1) * PETS_PER_PAGE;
        for (int index = start; index < Math.min(start + PETS_PER_PAGE, snapshot.pets().size()); index++) {
            PetInstance pet = snapshot.pets().get(index);
            boolean active = snapshot.desiredActivePetIds().contains(pet.id());
            int slot = index - start;
            actions.put(slot, PlayerPetInventoryHolder.Action.pet(pet.id(), active));
            inventory.setItem(slot, item(
                    active ? Material.LIME_DYE : Material.PLAYER_HEAD,
                    pet.definitionId(),
                    active ? NamedTextColor.GREEN : NamedTextColor.AQUA,
                    "Instance: " + abbreviate(pet.id().toString()),
                    active ? "Left-click: recall" : "Left-click: activate",
                    "Right-click: manage, cultivate, or release"));
        }

        if (page > 1) {
            actions.put(45, PlayerPetInventoryHolder.Action.previous());
            inventory.setItem(45, item(Material.ARROW, "Previous page", NamedTextColor.YELLOW));
        }
        if (page < pages) {
            actions.put(53, PlayerPetInventoryHolder.Action.next());
            inventory.setItem(53, item(Material.ARROW, "Next page", NamedTextColor.YELLOW));
        }
        inventory.setItem(49, item(
                snapshot.vaultOverflow() > 0 ? Material.RED_STAINED_GLASS : Material.ENDER_CHEST,
                "Vault status", snapshot.vaultOverflow() > 0 ? NamedTextColor.RED : NamedTextColor.GREEN,
                "Owned: " + snapshot.ownedCount() + "/" + snapshot.effectiveVaultCapacity(),
                "Desired active: " + snapshot.desiredActivePetIds().size() + "/" + snapshot.effectiveActiveSlotCount(),
                snapshot.vaultOverflow() > 0
                        ? "Overflow is read-only; no pet was deleted"
                        : "Active slot purchases use explicit provider choice"));
        actions.put(50, PlayerPetInventoryHolder.Action.purchaseSlot());
        inventory.setItem(50, item(
                Material.EXPERIENCE_BOTTLE,
                "Unlock active slot",
                NamedTextColor.GOLD,
                "Click to review configured Vault/PlayerPoints prices",
                "OmniPet never auto-selects a currency"));
        return inventory;
    }

    private static void fill(Inventory inventory) {
        ItemStack pane = item(Material.GRAY_STAINED_GLASS_PANE, " ", NamedTextColor.GRAY);
        for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, pane);
    }

    private static ItemStack item(Material material, String name, NamedTextColor color, String... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(name, color));
        List<Component> lines = new ArrayList<>();
        for (String line : lore) lines.add(Component.text(line, NamedTextColor.GRAY));
        meta.lore(lines);
        stack.setItemMeta(meta);
        return stack;
    }

    private static String abbreviate(String value) {
        return value.length() <= 18 ? value : value.substring(0, 18) + "...";
    }
}
