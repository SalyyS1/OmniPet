package io.github.salyvn.omnipet.paper.incubation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.UUID;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import io.github.salyvn.omnipet.core.incubation.EggInventoryHand;
import io.github.salyvn.omnipet.core.incubation.EggItemIdentity;

class PaperPlayerEggInventoryTest {
    @Test
    void rejectsOfflineOwnersBeforeReadingOrMutatingInventory() {
        PaperPlayerEggInventory inventory = inventory(false);

        assertThrows(IllegalStateException.class, inventory::stacks);
        assertThrows(IllegalStateException.class, () -> inventory.restoreOne(identity()));
    }

    @Test
    void invalidDurableSnapshotsFailWithoutTouchingAnOnlineInventory() {
        assertFalse(inventory(true).restoreOne(identity()));
    }

    private static PaperPlayerEggInventory inventory(boolean online) {
        Player player = (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[] {Player.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("isOnline")) return online;
                    throw new AssertionError("unexpected Player access: " + method.getName());
                });
        PaperEggItemCodec codec = new PaperEggItemCodec(
                new NamespacedKey("omnipet", "egg"),
                new NamespacedKey("omnipet", "item_nonce"),
                new NamespacedKey("omnipet", "item_schema"),
                new NamespacedKey("passivepet", "egg"),
                UUID::randomUUID);
        return new PaperPlayerEggInventory(player, codec, new PaperThreadGuard(() -> true));
    }

    private static EggItemIdentity identity() {
        return new EggItemIdentity(
                4,
                EggInventoryHand.MAIN_HAND,
                "minecraft:player_head",
                UUID.fromString("77777777-7777-7777-7777-777777777777"),
                "a".repeat(64),
                1,
                Map.of());
    }
}
