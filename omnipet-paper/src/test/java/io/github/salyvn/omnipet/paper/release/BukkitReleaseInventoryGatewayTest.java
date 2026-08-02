package io.github.salyvn.omnipet.paper.release;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

class BukkitReleaseInventoryGatewayTest {
    @Test
    void capacitySimulationUsesExistingStacksBeforeEmptySlots() {
        ItemStack[] contents = new ItemStack[2];
        contents[0] = new FakeItemStack(Material.DIAMOND, 63);

        assertTrue(BukkitReleaseInventoryGateway.fits(
                contents, List.of(new FakeItemStack(Material.DIAMOND, 2))));
        assertFalse(BukkitReleaseInventoryGateway.fits(
                contents, List.of(new FakeItemStack(Material.DIAMOND, 66))));
    }

    private static final class FakeItemStack extends ItemStack {
        private final Material material;
        private int amount;

        private FakeItemStack(Material material, int amount) {
            this.material = material;
            this.amount = amount;
        }

        @Override public Material getType() { return material; }
        @Override public int getAmount() { return amount; }
        @Override public void setAmount(int amount) { this.amount = amount; }
        @Override public int getMaxStackSize() { return 64; }
        @Override public boolean isSimilar(ItemStack stack) { return stack.getType() == material; }
        @Override public FakeItemStack clone() { return new FakeItemStack(material, amount); }
    }
}
