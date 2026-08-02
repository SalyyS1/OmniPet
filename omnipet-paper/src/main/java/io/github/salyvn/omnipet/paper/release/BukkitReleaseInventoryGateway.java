package io.github.salyvn.omnipet.paper.release;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class BukkitReleaseInventoryGateway implements ReleaseInventoryGateway {
    private final NamespacedKey transactionKey;

    public BukkitReleaseInventoryGateway(JavaPlugin plugin) {
        this.transactionKey = new NamespacedKey(Objects.requireNonNull(plugin, "plugin"), "release_transaction");
    }

    @Override
    public ReleaseInventoryClaimResult claim(
            UUID playerId,
            UUID transactionId,
            List<PaperMaterialReward> rewards) {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("release inventory delivery must run on Paper's primary thread");
        }
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            return result(ReleaseInventoryClaimResult.Status.PLAYER_OFFLINE, "player is offline; reward kept in mailbox");
        }
        PlayerInventory inventory = player.getInventory();
        Map<Material, Long> observed = taggedAmounts(inventory, transactionId);
        Map<Material, Long> expected = expectedAmounts(rewards);
        if (!observed.isEmpty()) {
            return observed.equals(expected)
                    ? result(ReleaseInventoryClaimResult.Status.ALREADY_DELIVERED,
                            "transaction-tagged rewards already exist in inventory")
                    : result(ReleaseInventoryClaimResult.Status.AMBIGUOUS,
                            "partial or altered transaction-tagged rewards require reconciliation");
        }
        List<ItemStack> stacks = stacks(transactionId, rewards);
        if (!fits(inventory.getStorageContents(), stacks)) {
            return result(ReleaseInventoryClaimResult.Status.CAPACITY_FULL,
                    "inventory has insufficient capacity; reward kept in mailbox");
        }
        Map<Integer, ItemStack> leftovers = inventory.addItem(stacks.toArray(ItemStack[]::new));
        return leftovers.isEmpty()
                ? result(ReleaseInventoryClaimResult.Status.DELIVERED, "release rewards added to inventory")
                : result(ReleaseInventoryClaimResult.Status.AMBIGUOUS,
                        "inventory changed during delivery; transaction requires reconciliation");
    }

    private List<ItemStack> stacks(UUID transactionId, List<PaperMaterialReward> rewards) {
        ArrayList<ItemStack> stacks = new ArrayList<>();
        for (PaperMaterialReward reward : rewards) {
            long remaining = reward.amount();
            int maximum = reward.material().getMaxStackSize();
            while (remaining > 0) {
                int amount = (int) Math.min(remaining, maximum);
                ItemStack stack = new ItemStack(reward.material(), amount);
                var meta = stack.getItemMeta();
                meta.getPersistentDataContainer().set(
                        transactionKey, PersistentDataType.STRING, transactionId.toString());
                stack.setItemMeta(meta);
                stacks.add(stack);
                remaining -= amount;
            }
        }
        return List.copyOf(stacks);
    }

    private Map<Material, Long> taggedAmounts(PlayerInventory inventory, UUID transactionId) {
        EnumMap<Material, Long> totals = new EnumMap<>(Material.class);
        for (ItemStack item : inventory.getStorageContents()) {
            if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) continue;
            String token = item.getItemMeta().getPersistentDataContainer()
                    .get(transactionKey, PersistentDataType.STRING);
            if (transactionId.toString().equals(token)) {
                totals.merge(item.getType(), (long) item.getAmount(), Math::addExact);
            }
        }
        return Map.copyOf(totals);
    }

    private static Map<Material, Long> expectedAmounts(List<PaperMaterialReward> rewards) {
        EnumMap<Material, Long> totals = new EnumMap<>(Material.class);
        rewards.forEach(reward -> totals.merge(reward.material(), reward.amount(), Math::addExact));
        return Map.copyOf(totals);
    }

    static boolean fits(ItemStack[] contents, List<ItemStack> additions) {
        ItemStack[] simulated = new ItemStack[contents.length];
        for (int index = 0; index < contents.length; index++) {
            simulated[index] = contents[index] == null ? null : contents[index].clone();
        }
        for (ItemStack addition : additions) {
            int remaining = addition.getAmount();
            for (ItemStack current : simulated) {
                if (current == null || !current.isSimilar(addition)) continue;
                int accepted = Math.min(remaining, current.getMaxStackSize() - current.getAmount());
                current.setAmount(current.getAmount() + accepted);
                remaining -= accepted;
                if (remaining == 0) break;
            }
            for (int slot = 0; remaining > 0 && slot < simulated.length; slot++) {
                if (simulated[slot] != null && simulated[slot].getType() != Material.AIR) continue;
                ItemStack placed = addition.clone();
                int amount = Math.min(remaining, addition.getMaxStackSize());
                placed.setAmount(amount);
                simulated[slot] = placed;
                remaining -= amount;
            }
            if (remaining > 0) return false;
        }
        return true;
    }

    private static ReleaseInventoryClaimResult result(ReleaseInventoryClaimResult.Status status, String detail) {
        return new ReleaseInventoryClaimResult(status, detail);
    }
}
