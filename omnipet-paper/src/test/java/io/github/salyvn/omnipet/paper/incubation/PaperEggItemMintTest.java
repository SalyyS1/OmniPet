package io.github.salyvn.omnipet.paper.incubation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.junit.jupiter.api.Test;

import net.kyori.adventure.text.Component;

import io.github.salyvn.omnipet.core.incubation.EggInventoryHand;

/**
 * A minted egg must be accepted by the very code that reads eggs.
 *
 * <p>This is the load-bearing test of the egg-distribution work. Before it there was no way to obtain
 * an egg in game — the codec could read and restore one but never sign a new one — so a pet created in
 * the Studio was unreachable. If minting and reading ever disagree on the identity keys, the symptom is
 * an egg that exists in a player's hand and is silently refused by {@code /pet hatch}, which is the
 * hardest kind of bug to diagnose from a bug report.
 *
 * <p>{@code create} itself calls {@code new ItemStack(...)}, which needs a live server, so these drive
 * the {@code sign} seam that decides identity. The material choice is the only untested part, and it
 * cannot make a valid egg invalid.
 */
class PaperEggItemMintTest {
    private static final NamespacedKey EGG = new NamespacedKey("omnipet", "egg");
    private static final NamespacedKey NONCE = new NamespacedKey("omnipet", "item_nonce");
    private static final NamespacedKey SCHEMA = new NamespacedKey("omnipet", "item_schema");
    private static final NamespacedKey LEGACY_EGG = new NamespacedKey("passivepet", "egg");
    private static final UUID MINT_NONCE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID CAPTURE_NONCE = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Test
    void aMintedEggIsObservedAsAValidOmniPetEgg() {
        FakeItemData data = new FakeItemData();
        PaperEggItemCodec codec = codec(() -> MINT_NONCE);

        ItemStack minted = codec.sign(new FakeItemStack(1, data), "tier_d_egg", null, List.of());
        var observed = codec.observe(minted, 3).orElseThrow();

        // identityValid is the flag /pet hatch gates on: false means "not an OmniPet egg".
        assertTrue(observed.identityValid(), "a freshly minted egg must be recognised as ours");
        assertEquals("tier_d_egg", observed.eggId());
        assertEquals(MINT_NONCE, observed.nonce());
        assertNotEquals("invalid", observed.fingerprint());
    }

    @Test
    void aMintedEggIsAcceptedByCaptureSoHatchCanStart() {
        FakeItemData data = new FakeItemData();
        AtomicInteger sequence = new AtomicInteger();
        PaperEggItemCodec codec = codec(
                () -> sequence.getAndIncrement() == 0 ? MINT_NONCE : CAPTURE_NONCE);

        ItemStack minted = codec.sign(new FakeItemStack(1, data), "tier_d_egg", null, List.of());
        CapturedEggItem captured = codec.capture(minted, 4, EggInventoryHand.MAIN_HAND);

        assertEquals("tier_d_egg", captured.eggId());
        // Capture rotates the nonce so a duplicated stack cannot replay a paid start.
        assertEquals(CAPTURE_NONCE, captured.identity().itemNonce());
        assertEquals(EggInventoryHand.MAIN_HAND, captured.identity().hand());
        assertEquals(4, captured.identity().inventorySlot());
    }

    @Test
    void mintingWritesExactlyTheThreeKeysTheReaderChecks() {
        FakeItemData data = new FakeItemData();

        codec(() -> MINT_NONCE).sign(new FakeItemStack(1, data), "tier_d_egg", null, List.of());

        assertEquals("tier_d_egg", data.values.get(EGG));
        assertEquals(MINT_NONCE.toString(), data.values.get(NONCE));
        assertEquals(1, data.values.get(SCHEMA), "schema must match ITEM_SCHEMA or observe rejects it");
        // The legacy key is a read-compatibility path only; minting must not write it.
        assertEquals(null, data.values.get(LEGACY_EGG));
    }

    @Test
    void everyMintedEggCarriesItsOwnNonce() {
        AtomicInteger sequence = new AtomicInteger();
        PaperEggItemCodec codec = codec(
                () -> sequence.getAndIncrement() == 0 ? MINT_NONCE : CAPTURE_NONCE);
        FakeItemData first = new FakeItemData();
        FakeItemData second = new FakeItemData();

        codec.sign(new FakeItemStack(1, first), "tier_d_egg", null, List.of());
        codec.sign(new FakeItemStack(1, second), "tier_d_egg", null, List.of());

        // Two eggs sharing a nonce would be indistinguishable to the escrow saga, so it could not tell
        // which one was paid for.
        assertNotEquals(first.values.get(NONCE), second.values.get(NONCE));
    }

    @Test
    void anEggMissingItsNonceOrSchemaIsNotTreatedAsOurs() {
        PaperEggItemCodec codec = codec(() -> MINT_NONCE);

        FakeItemData noNonce = new FakeItemData();
        noNonce.values.put(EGG, "tier_d_egg");
        noNonce.values.put(SCHEMA, 1);
        assertTrue(!codec.observe(new FakeItemStack(1, noNonce), 0).orElseThrow().identityValid(),
                "a hand-crafted egg without a nonce must be refused");

        FakeItemData noSchema = new FakeItemData();
        noSchema.values.put(EGG, "tier_d_egg");
        noSchema.values.put(NONCE, MINT_NONCE.toString());
        assertTrue(!codec.observe(new FakeItemStack(1, noSchema), 0).orElseThrow().identityValid(),
                "a hand-crafted egg without the schema marker must be refused");
    }

    @Test
    void mintingRejectsAnUnusableEggId() {
        PaperEggItemCodec codec = codec(() -> MINT_NONCE);

        assertThrows(IllegalArgumentException.class,
                () -> codec.sign(new FakeItemStack(1, new FakeItemData()), "", null, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> codec.sign(new FakeItemStack(1, new FakeItemData()), "Not Valid!", null, List.of()));
    }

    @Test
    void nameAndLoreReachTheItemWhenSupplied() {
        FakeItemData data = new FakeItemData();

        codec(() -> MINT_NONCE).sign(
                new FakeItemStack(1, data),
                "tier_d_egg",
                Component.text("Tier d Egg"),
                List.of(Component.text("Tier D"), Component.text("Incubates in 1h")));

        assertEquals("Tier d Egg", plain(data.displayName));
        assertEquals(2, data.lore.size());
        assertEquals("Tier D", plain(data.lore.getFirst()));
    }

    private static String plain(Object component) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize((Component) component);
    }

    private static PaperEggItemCodec codec(java.util.function.Supplier<UUID> nonceSupplier) {
        return new PaperEggItemCodec(EGG, NONCE, SCHEMA, LEGACY_EGG, nonceSupplier);
    }

    /** Mirrors the harness in {@code PaperEggItemCodecTest}, plus the name and lore mint writes. */
    private static final class FakeItemData {
        private final Map<NamespacedKey, Object> values = new LinkedHashMap<>();
        private Object displayName;
        private List<?> lore = List.of();
        private final PersistentDataContainer container = (PersistentDataContainer) Proxy.newProxyInstance(
                PersistentDataContainer.class.getClassLoader(),
                new Class<?>[] {PersistentDataContainer.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "set" -> { values.put((NamespacedKey) args[0], args[2]); yield null; }
                    case "get" -> values.get(args[0]);
                    case "has" -> values.containsKey(args[0]);
                    case "getKeys" -> Set.copyOf(values.keySet());
                    case "isEmpty" -> values.isEmpty();
                    case "getSize" -> values.size();
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        private final ItemMeta meta = (ItemMeta) Proxy.newProxyInstance(
                ItemMeta.class.getClassLoader(),
                new Class<?>[] {ItemMeta.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getPersistentDataContainer" -> container;
                    case "displayName" -> { displayName = args[0]; yield null; }
                    case "lore" -> { lore = (List<?>) args[0]; yield null; }
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private static final class FakeItemStack extends ItemStack {
        private int amount;
        private final FakeItemData data;

        private FakeItemStack(int amount, FakeItemData data) {
            this.amount = amount;
            this.data = data;
        }

        @Override public Material getType() { return Material.TURTLE_EGG; }
        @Override public int getAmount() { return amount; }
        @Override public void setAmount(int amount) { this.amount = amount; }
        @Override public int getMaxStackSize() { return 1; }
        @Override public ItemMeta getItemMeta() { return data.meta; }
        @Override public boolean setItemMeta(ItemMeta meta) { return meta == data.meta; }
        @Override public FakeItemStack clone() { return new FakeItemStack(amount, data); }

        @Override
        public byte[] serializeAsBytes() {
            StringBuilder serialized = new StringBuilder("turtle_egg:").append(amount);
            data.values.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey(java.util.Comparator.comparing(NamespacedKey::toString)))
                    .forEach(entry -> serialized.append('|').append(entry.getKey()).append('=').append(entry.getValue()));
            return serialized.toString().getBytes(StandardCharsets.UTF_8);
        }
    }
}
