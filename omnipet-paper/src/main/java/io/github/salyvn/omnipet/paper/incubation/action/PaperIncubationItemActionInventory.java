package io.github.salyvn.omnipet.paper.incubation.action;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import io.github.salyvn.omnipet.core.incubation.EggEscrowItemObservation;
import io.github.salyvn.omnipet.core.incubation.EggInventoryHand;
import io.github.salyvn.omnipet.core.incubation.EggItemIdentity;
import io.github.salyvn.omnipet.paper.incubation.EggInventoryEscrowService;
import io.github.salyvn.omnipet.paper.incubation.EggInventoryMutationResult;
import io.github.salyvn.omnipet.paper.incubation.EggInventoryPort;
import io.github.salyvn.omnipet.paper.incubation.ObservedEggStack;

public final class PaperIncubationItemActionInventory implements IncubationItemActionInventoryPort {
    private static final int STORAGE_SIZE = 36;
    private static final int OFF_HAND_SLOT = 40;

    private final BooleanSupplier mainThread;
    private final Function<UUID, Player> playerLookup;
    private final PaperIncubationItemActionCodec codec;
    private final EggInventoryEscrowService escrow;

    public PaperIncubationItemActionInventory(PaperIncubationItemActionCodec codec) {
        this(Bukkit::isPrimaryThread, Bukkit::getPlayer, codec, new EggInventoryEscrowService());
    }

    PaperIncubationItemActionInventory(
            BooleanSupplier mainThread,
            Function<UUID, Player> playerLookup,
            PaperIncubationItemActionCodec codec,
            EggInventoryEscrowService escrow) {
        this.mainThread = Objects.requireNonNull(mainThread, "primary thread probe");
        this.playerLookup = Objects.requireNonNull(playerLookup, "online player lookup");
        this.codec = Objects.requireNonNull(codec, "incubation action item codec");
        this.escrow = Objects.requireNonNull(escrow, "incubation action item escrow");
    }

    @Override
    public boolean isMainThread() {
        return mainThread.getAsBoolean();
    }

    @Override
    public EggEscrowItemObservation observe(UUID playerId, EggItemIdentity item) throws IOException {
        requireMainThread();
        PlayerActionInventory inventory = inventory(playerId);
        if (inventory == null || !codec.supports(item)) return EggEscrowItemObservation.AMBIGUOUS;
        return escrow.observe(item, inventory);
    }

    @Override
    public EggInventoryMutationResult removeOne(UUID playerId, EggItemIdentity item) throws IOException {
        requireMainThread();
        PlayerActionInventory inventory = inventory(playerId);
        if (inventory == null || !codec.supports(item)) return EggInventoryMutationResult.AMBIGUOUS;
        return escrow.removeOne(item, inventory);
    }

    @Override
    public EggInventoryMutationResult refundOne(UUID playerId, EggItemIdentity item) throws IOException {
        requireMainThread();
        PlayerActionInventory inventory = inventory(playerId);
        if (inventory == null || !codec.supports(item)) return EggInventoryMutationResult.AMBIGUOUS;
        return escrow.refundOne(item, inventory);
    }

    public CapturedIncubationItemAction capture(UUID playerId, EggInventoryHand hand) throws IOException {
        requireMainThread();
        PlayerActionInventory inventory = inventory(playerId);
        if (inventory == null) throw new IOException("incubation action item owner is not online");
        return inventory.capture(Objects.requireNonNull(hand, "incubation action item hand"));
    }

    private PlayerActionInventory inventory(UUID playerId) {
        if (playerId == null) return null;
        Player player = playerLookup.apply(playerId);
        if (player == null || !player.isOnline() || !playerId.equals(player.getUniqueId())) return null;
        return new PlayerActionInventory(player);
    }

    private void requireMainThread() {
        if (!isMainThread()) {
            throw new IllegalStateException("Paper incubation action inventory access must run on the primary thread");
        }
    }

    private final class PlayerActionInventory implements EggInventoryPort {
        private final Player player;

        private PlayerActionInventory(Player player) {
            this.player = player;
        }

        private CapturedIncubationItemAction capture(EggInventoryHand hand) {
            int slot = hand == EggInventoryHand.OFF_HAND
                    ? OFF_HAND_SLOT
                    : player.getInventory().getHeldItemSlot();
            CapturedIncubationItemAction captured = codec.capture(item(slot), slot, hand);
            setItem(slot, captured.stack());
            return captured;
        }

        @Override
        public List<ObservedEggStack> stacks() {
            List<ObservedEggStack> result = new ArrayList<>();
            for (int slot = 0; slot < STORAGE_SIZE; slot++) {
                codec.observe(item(slot), slot).ifPresent(result::add);
            }
            codec.observe(item(OFF_HAND_SLOT), OFF_HAND_SLOT).ifPresent(result::add);
            return List.copyOf(result);
        }

        @Override
        public boolean handMatches(EggItemIdentity identity) {
            return identity.hand() == EggInventoryHand.OFF_HAND
                    ? identity.inventorySlot() == OFF_HAND_SLOT
                    : identity.inventorySlot() == player.getInventory().getHeldItemSlot();
        }

        @Override
        public boolean removeOne(ObservedEggStack expected) {
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
            ObservedEggStack current = codec.observe(item(expected.slot()), expected.slot()).orElse(null);
            if (!expected.equals(current) || current.amount() >= current.maxStackAmount()) return false;
            ItemStack next = item(expected.slot()).clone();
            next.setAmount(next.getAmount() + 1);
            setItem(expected.slot(), next);
            return true;
        }

        @Override
        public boolean restoreOne(EggItemIdentity identity) {
            try {
                return player.getInventory().addItem(codec.restore(identity)).isEmpty();
            } catch (RuntimeException invalidSnapshot) {
                return false;
            }
        }

        private ItemStack item(int slot) {
            PlayerInventory inventory = player.getInventory();
            return slot == OFF_HAND_SLOT ? inventory.getItemInOffHand() : inventory.getItem(slot);
        }

        private void setItem(int slot, ItemStack item) {
            PlayerInventory inventory = player.getInventory();
            ItemStack normalized = item == null || item.getType() == Material.AIR || item.getAmount() < 1 ? null : item;
            if (slot == OFF_HAND_SLOT) inventory.setItemInOffHand(normalized);
            else inventory.setItem(slot, normalized);
        }
    }
}
