package io.github.salyvn.omnipet.paper.management;

import java.util.List;
import java.util.Locale;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class PetCultivationItemController {
    private final PaperPetConsumableInventory items;

    public PetCultivationItemController(PaperPetConsumableInventory items) {
        this.items = java.util.Objects.requireNonNull(items, "cultivation items");
    }

    public boolean supports(List<String> arguments) {
        if (arguments == null || arguments.isEmpty()) return false;
        String type = arguments.getFirst().toLowerCase(Locale.ROOT);
        return type.equals("candy") || type.equals("breakthrough");
    }

    public void command(CommandSender sender, List<String> arguments) {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("cultivation item command requires main thread");
        if (!supports(arguments) || arguments.size() < 2 || arguments.size() > 3) {
            usage(sender);
            return;
        }
        Player target = Bukkit.getPlayerExact(arguments.get(1));
        if (target == null || !target.isOnline()) {
            sender.sendMessage("OmniPet: item target must be an online exact player name.");
            return;
        }
        int amount;
        try {
            amount = arguments.size() == 3 ? Integer.parseInt(arguments.get(2)) : 1;
        } catch (NumberFormatException invalid) {
            sender.sendMessage("OmniPet: cultivation item amount must be an integer.");
            return;
        }
        if (amount < 1 || amount > 36 || emptySlots(target) < amount) {
            sender.sendMessage("OmniPet: amount must be 1..36 and the target needs one empty slot per item.");
            return;
        }
        PetConsumableInventoryPort.Kind kind = arguments.getFirst().equalsIgnoreCase("candy")
                ? PetConsumableInventoryPort.Kind.EXPERIENCE_CANDY
                : PetConsumableInventoryPort.Kind.BREAKTHROUGH_STONE;
        for (int index = 0; index < amount; index++) {
            ItemStack item = items.create(kind);
            target.getInventory().setItem(target.getInventory().firstEmpty(), item);
        }
        sender.sendMessage("OmniPet: delivered " + amount + " unstacked "
                + arguments.getFirst().toLowerCase(Locale.ROOT) + " item(s) to " + target.getName() + ".");
    }

    private static int emptySlots(Player player) {
        int empty = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item == null || item.getType().isAir()) empty++;
        }
        return empty;
    }

    private static void usage(CommandSender sender) {
        sender.sendMessage("OmniPet: use /pet admin item candy <player_name> [amount] or "
                + "/pet admin item breakthrough <player_name> [amount].");
    }
}
