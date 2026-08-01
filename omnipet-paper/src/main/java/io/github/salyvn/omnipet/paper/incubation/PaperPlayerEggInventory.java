package io.github.salyvn.omnipet.paper.incubation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import io.github.salyvn.omnipet.core.incubation.EggInventoryHand;
import io.github.salyvn.omnipet.core.incubation.EggItemIdentity;

public final class PaperPlayerEggInventory implements EggInventoryPort {
    public static final int OFF_HAND_SLOT = 40;
    private static final int STORAGE_SIZE = 36;

    private final Player player;
    private final PaperEggItemCodec codec;
    private final PaperThreadGuard threadGuard;

    public PaperPlayerEggInventory(Player player, PaperEggItemCodec codec) {
        this(player, codec, new PaperThreadGuard());
    }

    PaperPlayerEggInventory(Player player, PaperEggItemCodec codec, PaperThreadGuard threadGuard) {
        this.player = Objects.requireNonNull(player, "player");
        this.codec = Objects.requireNonNull(codec, "egg item codec");
        this.threadGuard = Objects.requireNonNull(threadGuard, "thread guard");
    }

    public CapturedEggItem capture(EggInventoryHand hand) {
        checkAvailable();
        int slot = hand == EggInventoryHand.OFF_HAND
                ? OFF_HAND_SLOT
                : player.getInventory().getHeldItemSlot();
        CapturedEggItem captured = codec.capture(item(slot), slot, hand);
        setItem(slot, captured.stack());
        return captured;
    }

    @Override
    public List<ObservedEggStack> stacks() {
        checkAvailable();
        List<ObservedEggStack> result = new ArrayList<>();
        for (int slot = 0; slot < STORAGE_SIZE; slot++) {
            codec.observe(item(slot), slot).ifPresent(result::add);
        }
        codec.observe(item(OFF_HAND_SLOT), OFF_HAND_SLOT).ifPresent(result::add);
        return List.copyOf(result);
    }

    @Override
    public boolean handMatches(EggItemIdentity identity) {
        checkAvailable();
        return identity.hand() == EggInventoryHand.OFF_HAND
                ? identity.inventorySlot() == OFF_HAND_SLOT
                : identity.inventorySlot() == player.getInventory().getHeldItemSlot();
    }

    @Override
    public boolean removeOne(ObservedEggStack expected) {
        checkAvailable();
        ObservedEggStack current = codec.observe(item(expected.slot()), expected.slot()).orElse(null);
        if (!expected.equals(current)) return false;
        ItemStack currentItem = item(expected.slot());
        if (currentItem.getAmount() == 1) {
            setItem(expected.slot(), null);
        } else {
            ItemStack next = currentItem.clone();
            next.setAmount(next.getAmount() - 1);
            setItem(expected.slot(), next);
        }
        return true;
    }

    @Override
    public boolean addOne(ObservedEggStack expected) {
        checkAvailable();
        ObservedEggStack current = codec.observe(item(expected.slot()), expected.slot()).orElse(null);
        if (!expected.equals(current) || current.amount() >= current.maxStackAmount()) return false;
        ItemStack next = item(expected.slot()).clone();
        next.setAmount(next.getAmount() + 1);
        setItem(expected.slot(), next);
        return true;
    }

    @Override
    public boolean restoreOne(EggItemIdentity identity) {
        checkAvailable();
        try {
            ItemStack restored = codec.restore(identity);
            return player.getInventory().addItem(restored).isEmpty();
        } catch (RuntimeException invalidSnapshot) {
            return false;
        }
    }

    private ItemStack item(int slot) {
        PlayerInventory inventory = player.getInventory();
        return slot == OFF_HAND_SLOT ? inventory.getItemInOffHand() : inventory.getItem(slot);
    }

    private void checkAvailable() {
        threadGuard.check();
        if (!player.isOnline()) {
            throw new IllegalStateException("Paper egg inventory owner is no longer online");
        }
    }

    private void setItem(int slot, ItemStack item) {
        PlayerInventory inventory = player.getInventory();
        if (slot == OFF_HAND_SLOT) inventory.setItemInOffHand(emptyToNull(item));
        else inventory.setItem(slot, emptyToNull(item));
    }

    private static ItemStack emptyToNull(ItemStack item) {
        return item == null || item.getType() == Material.AIR || item.getAmount() < 1 ? null : item;
    }
}
