package io.github.salyvn.omnipet.paper.incubation.action;

import static io.github.salyvn.omnipet.paper.incubation.action.PaperIncubationActionTestItems.EFFECT;
import static io.github.salyvn.omnipet.paper.incubation.action.PaperIncubationActionTestItems.NONCE;
import static io.github.salyvn.omnipet.paper.incubation.action.PaperIncubationActionTestItems.SCHEMA;
import static io.github.salyvn.omnipet.paper.incubation.action.PaperIncubationActionTestItems.TYPE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;

import io.github.salyvn.omnipet.core.incubation.EggEscrowItemObservation;
import io.github.salyvn.omnipet.core.incubation.EggInventoryHand;
import io.github.salyvn.omnipet.core.incubation.EggItemIdentity;
import io.github.salyvn.omnipet.core.incubation.IncubationItemActionItemContract;
import io.github.salyvn.omnipet.core.incubation.IncubationItemActionStage;
import io.github.salyvn.omnipet.core.incubation.IncubationItemActionTransaction;
import io.github.salyvn.omnipet.core.incubation.IncubationItemActionType;
import io.github.salyvn.omnipet.paper.incubation.EggInventoryEscrowService;
import io.github.salyvn.omnipet.paper.incubation.EggInventoryMutationResult;
import io.github.salyvn.omnipet.paper.incubation.action.PaperIncubationActionTestItems.FakeItemStack;

class PaperIncubationItemActionInventoryTest {
    private static final UUID PLAYER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID ITEM_NONCE = UUID.fromString("44444444-4444-4444-4444-444444444444");

    @Test
    void captureRemoveAndRefundMutateExactlyOneRecordedMainHandItem() throws Exception {
        Fixture fixture = fixture(true, true);
        FakeItemStack reducer = (FakeItemStack) fixture.codec.createReducer(Material.CLOCK, 30_000);
        reducer.setAmount(3);
        fixture.player.items[4] = reducer;
        CapturedIncubationItemAction captured = fixture.inventory.capture(PLAYER_ID, EggInventoryHand.MAIN_HAND);
        fixture.restoreTemplate.set(captured.stack());

        assertEquals(EggEscrowItemObservation.MATCHING_ITEM_PRESENT,
                fixture.inventory.observe(PLAYER_ID, captured.identity()));
        assertEquals(EggInventoryMutationResult.REMOVED,
                fixture.inventory.removeOne(PLAYER_ID, captured.identity()));
        assertEquals(2, fixture.player.items[4].getAmount());
        assertEquals(EggEscrowItemObservation.MATCHING_ITEM_ABSENT,
                fixture.inventory.observe(PLAYER_ID, captured.identity()));
        assertEquals(EggInventoryMutationResult.REFUNDED,
                fixture.inventory.refundOne(PLAYER_ID, captured.identity()));
        assertEquals(3, fixture.player.items[4].getAmount());
        assertEquals(EggInventoryMutationResult.ALREADY_PRESENT,
                fixture.inventory.refundOne(PLAYER_ID, captured.identity()));
    }

    @Test
    void copiedNonceAndEffectTamperingAreAmbiguous() throws Exception {
        Fixture copied = fixture(true, true);
        copied.player.items[4] = copied.codec.createReducer(Material.CLOCK, 30_000);
        CapturedIncubationItemAction copiedAction = copied.inventory.capture(PLAYER_ID, EggInventoryHand.MAIN_HAND);
        copied.restoreTemplate.set(copiedAction.stack());
        copied.player.items[5] = copiedAction.stack().clone();

        assertEquals(EggEscrowItemObservation.AMBIGUOUS,
                copied.inventory.observe(PLAYER_ID, copiedAction.identity()));
        assertEquals(EggInventoryMutationResult.AMBIGUOUS,
                copied.inventory.removeOne(PLAYER_ID, copiedAction.identity()));

        Fixture tampered = fixture(true, true);
        tampered.player.items[4] = tampered.codec.createReducer(Material.CLOCK, 30_000);
        CapturedIncubationItemAction tamperedAction = tampered.inventory.capture(PLAYER_ID, EggInventoryHand.MAIN_HAND);
        tampered.restoreTemplate.set(tamperedAction.stack());
        ((FakeItemStack) tampered.player.items[4]).values().put(EFFECT, 45_000L);

        assertEquals(EggEscrowItemObservation.AMBIGUOUS,
                tampered.inventory.observe(PLAYER_ID, tamperedAction.identity()));
        assertEquals(EggInventoryMutationResult.AMBIGUOUS,
                tampered.inventory.refundOne(PLAYER_ID, tamperedAction.identity()));

        Fixture unexpectedStack = fixture(true, true);
        unexpectedStack.player.items[4] = unexpectedStack.codec.createReducer(Material.CLOCK, 30_000);
        CapturedIncubationItemAction stackedAction = unexpectedStack.inventory.capture(
                PLAYER_ID, EggInventoryHand.MAIN_HAND);
        unexpectedStack.restoreTemplate.set(stackedAction.stack());
        unexpectedStack.player.items[4].setAmount(2);

        assertEquals(EggEscrowItemObservation.AMBIGUOUS,
                unexpectedStack.inventory.observe(PLAYER_ID, stackedAction.identity()));
        assertEquals(EggInventoryMutationResult.NOT_MATCHING,
                unexpectedStack.inventory.removeOne(PLAYER_ID, stackedAction.identity()));
    }

    @Test
    void offlineWrongOwnerAndSecondaryThreadFailClosed() throws Exception {
        Fixture offline = fixture(false, true);
        offline.player.items[4] = offline.codec.createInstantHatch(Material.NETHER_STAR);
        CapturedIncubationItemAction identity = offline.codec.capture(
                offline.player.items[4], 4, EggInventoryHand.MAIN_HAND);
        offline.restoreTemplate.set(identity.stack());

        assertEquals(EggEscrowItemObservation.AMBIGUOUS,
                offline.inventory.observe(PLAYER_ID, identity.identity()));
        assertEquals(EggInventoryMutationResult.AMBIGUOUS,
                offline.inventory.removeOne(PLAYER_ID, identity.identity()));
        assertThrows(java.io.IOException.class,
                () -> offline.inventory.capture(PLAYER_ID, EggInventoryHand.MAIN_HAND));

        Fixture wrongOwner = fixture(true, true);
        wrongOwner.player.reportedId = UUID.fromString("55555555-5555-5555-5555-555555555555");
        assertEquals(EggInventoryMutationResult.AMBIGUOUS,
                wrongOwner.inventory.refundOne(PLAYER_ID, identity.identity()));

        Fixture secondary = fixture(true, false);
        assertThrows(IllegalStateException.class,
                () -> secondary.inventory.observe(PLAYER_ID, identity.identity()));
    }

    @Test
    void forgedExtensionBindingCannotTurnReducerSnapshotIntoInstantHatch() throws Exception {
        Fixture fixture = fixture(true, true);
        fixture.player.items[4] = fixture.codec.createReducer(Material.CLOCK, 30_000);
        CapturedIncubationItemAction captured = fixture.inventory.capture(PLAYER_ID, EggInventoryHand.MAIN_HAND);
        fixture.restoreTemplate.set(captured.stack());
        Map<String, Object> forgedExtensions = new HashMap<>(captured.identity().extensions());
        forgedExtensions.put(IncubationItemActionItemContract.TYPE_KEY, IncubationItemActionType.COMPLETE.name());
        forgedExtensions.put(IncubationItemActionItemContract.EFFECT_MILLIS_KEY, "0");
        EggItemIdentity forged = new EggItemIdentity(
                captured.identity().inventorySlot(),
                captured.identity().hand(),
                captured.identity().materialKey(),
                captured.identity().itemNonce(),
                captured.identity().fingerprint(),
                captured.identity().expectedStackAmount(),
                forgedExtensions);
        IncubationItemActionTransaction forgedTransaction = new IncubationItemActionTransaction(
                UUID.randomUUID(), PLAYER_ID, UUID.randomUUID(), 1, forged,
                IncubationItemActionType.COMPLETE, 0, IncubationItemActionStage.PREPARED);

        assertEquals(EggEscrowItemObservation.AMBIGUOUS,
                fixture.inventory.observe(PLAYER_ID, forgedTransaction.item()));
        assertEquals(EggInventoryMutationResult.AMBIGUOUS,
                fixture.inventory.removeOne(PLAYER_ID, forgedTransaction.item()));
        assertEquals(1, fixture.player.items[4].getAmount());
    }

    private static Fixture fixture(boolean online, boolean primaryThread) {
        AtomicReference<ItemStack> restoreTemplate = new AtomicReference<>();
        PaperIncubationItemActionCodec codec = new PaperIncubationItemActionCodec(
                SCHEMA,
                TYPE,
                EFFECT,
                NONCE,
                () -> ITEM_NONCE,
                PaperIncubationActionTestItems.FakeItemStack::new,
                ignored -> restoreTemplate.get().clone());
        FakePlayer player = new FakePlayer(online);
        PaperIncubationItemActionInventory inventory = new PaperIncubationItemActionInventory(
                () -> primaryThread,
                ignored -> player.proxy,
                codec,
                new EggInventoryEscrowService());
        return new Fixture(codec, inventory, player, restoreTemplate);
    }

    private record Fixture(
            PaperIncubationItemActionCodec codec,
            PaperIncubationItemActionInventory inventory,
            FakePlayer player,
            AtomicReference<ItemStack> restoreTemplate) {}

    private static final class FakePlayer {
        private final ItemStack[] items = new ItemStack[41];
        private final boolean online;
        private UUID reportedId = PLAYER_ID;
        private final PlayerInventory inventory;
        private final Player proxy;

        private FakePlayer(boolean online) {
            this.online = online;
            this.inventory = (PlayerInventory) Proxy.newProxyInstance(
                    PlayerInventory.class.getClassLoader(),
                    new Class<?>[] {PlayerInventory.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getHeldItemSlot" -> 4;
                        case "getItem" -> items[(int) args[0]];
                        case "setItem" -> { items[(int) args[0]] = (ItemStack) args[1]; yield null; }
                        case "getItemInOffHand" -> items[40];
                        case "setItemInOffHand" -> { items[40] = (ItemStack) args[0]; yield null; }
                        case "addItem" -> addItem((ItemStack[]) args[0]);
                        default -> throw new AssertionError("unexpected PlayerInventory access: " + method.getName());
                    });
            this.proxy = (Player) Proxy.newProxyInstance(
                    Player.class.getClassLoader(),
                    new Class<?>[] {Player.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "isOnline" -> this.online;
                        case "getUniqueId" -> this.reportedId;
                        case "getInventory" -> this.inventory;
                        default -> throw new AssertionError("unexpected Player access: " + method.getName());
                    });
        }

        private Map<Integer, ItemStack> addItem(ItemStack[] additions) {
            ItemStack item = additions[0];
            for (int slot = 0; slot < 36; slot++) {
                if (items[slot] == null) {
                    items[slot] = item;
                    return Map.of();
                }
            }
            Map<Integer, ItemStack> leftovers = new LinkedHashMap<>();
            leftovers.put(0, item);
            return leftovers;
        }
    }
}
