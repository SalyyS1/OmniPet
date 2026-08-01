package io.github.salyvn.omnipet.paper.incubation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;

import io.github.salyvn.omnipet.core.incubation.EggInventoryHand;

class PaperEggItemCodecTest {
    private static final NamespacedKey EGG = new NamespacedKey("omnipet", "egg");
    private static final NamespacedKey NONCE = new NamespacedKey("omnipet", "item_nonce");
    private static final NamespacedKey SCHEMA = new NamespacedKey("omnipet", "item_schema");
    private static final NamespacedKey LEGACY_EGG = new NamespacedKey("passivepet", "egg");
    private static final UUID FIRST_NONCE = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID SECOND_NONCE = UUID.fromString("66666666-6666-6666-6666-666666666666");

    @Test
    void captureMigratesLegacyItemsAndRotatesTheActionNonce() {
        FakeItemData data = new FakeItemData();
        data.values.put(LEGACY_EGG, "tier_d_egg");
        AtomicInteger sequence = new AtomicInteger();
        PaperEggItemCodec codec = codec(() -> sequence.getAndIncrement() == 0 ? FIRST_NONCE : SECOND_NONCE);

        CapturedEggItem first = codec.capture(new FakeItemStack(5, data), 4, EggInventoryHand.MAIN_HAND);
        CapturedEggItem second = codec.capture(first.stack(), 4, EggInventoryHand.MAIN_HAND);

        assertEquals("tier_d_egg", first.eggId());
        assertEquals("tier_d_egg", data.values.get(EGG));
        assertEquals(1, data.values.get(SCHEMA));
        assertEquals(SECOND_NONCE.toString(), data.values.get(NONCE));
        assertEquals(FIRST_NONCE, first.identity().itemNonce());
        assertEquals(SECOND_NONCE, second.identity().itemNonce());
        assertNotEquals(first.identity().fingerprint(), second.identity().fingerprint());
    }

    @Test
    void unsupportedSchemasAreRejectedOnCaptureAndFailClosedOnObservation() {
        FakeItemData data = new FakeItemData();
        data.values.put(EGG, "tier_d_egg");
        data.values.put(NONCE, FIRST_NONCE.toString());
        data.values.put(SCHEMA, 2);
        FakeItemStack stack = new FakeItemStack(1, data);
        PaperEggItemCodec codec = codec(() -> SECOND_NONCE);

        assertThrows(IllegalArgumentException.class,
                () -> codec.capture(stack, 4, EggInventoryHand.MAIN_HAND));
        assertEquals("invalid", codec.observe(stack, 4).orElseThrow().fingerprint());
    }

    @Test
    void missingAndMalformedNoncesRemainObservableAsInvalidEggs() {
        FakeItemData data = new FakeItemData();
        data.values.put(EGG, "tier_d_egg");
        data.values.put(SCHEMA, 1);
        FakeItemStack stack = new FakeItemStack(1, data);
        PaperEggItemCodec codec = codec(() -> SECOND_NONCE);

        ObservedEggStack missing = codec.observe(stack, 4).orElseThrow();
        data.values.put(NONCE, "not-a-uuid");
        ObservedEggStack malformed = codec.observe(stack, 4).orElseThrow();

        assertEquals(false, missing.identityValid());
        assertEquals(false, malformed.identityValid());
        assertEquals("invalid", missing.fingerprint());
        assertEquals("invalid", malformed.fingerprint());
    }

    @Test
    void observationUsesTheEffectiveItemStackMaximum() {
        FakeItemData data = new FakeItemData();
        data.values.put(EGG, "tier_d_egg");
        data.values.put(NONCE, FIRST_NONCE.toString());
        data.values.put(SCHEMA, 1);

        ObservedEggStack observed = codec(() -> SECOND_NONCE)
                .observe(new FakeItemStack(1, 1, data), 4)
                .orElseThrow();

        assertEquals(1, observed.maxStackAmount());
    }

    private static PaperEggItemCodec codec(java.util.function.Supplier<UUID> nonceSupplier) {
        return new PaperEggItemCodec(EGG, NONCE, SCHEMA, LEGACY_EGG, nonceSupplier);
    }

    private static final class FakeItemData {
        private final Map<NamespacedKey, Object> values = new LinkedHashMap<>();
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
                (proxy, method, args) -> {
                    if (method.getName().equals("getPersistentDataContainer")) return container;
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private static final class FakeItemStack extends ItemStack {
        private int amount;
        private final int maxStackAmount;
        private final FakeItemData data;

        private FakeItemStack(int amount, FakeItemData data) {
            this(amount, 64, data);
        }

        private FakeItemStack(int amount, int maxStackAmount, FakeItemData data) {
            this.amount = amount;
            this.maxStackAmount = maxStackAmount;
            this.data = data;
        }

        @Override public Material getType() { return Material.PLAYER_HEAD; }
        @Override public int getAmount() { return amount; }
        @Override public void setAmount(int amount) { this.amount = amount; }
        @Override public int getMaxStackSize() { return maxStackAmount; }
        @Override public ItemMeta getItemMeta() { return data.meta; }
        @Override public boolean setItemMeta(ItemMeta meta) { return meta == data.meta; }
        @Override public FakeItemStack clone() { return new FakeItemStack(amount, maxStackAmount, data); }

        @Override
        public byte[] serializeAsBytes() {
            StringBuilder serialized = new StringBuilder("player_head:").append(amount);
            data.values.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey(java.util.Comparator.comparing(NamespacedKey::toString)))
                    .forEach(entry -> serialized.append('|').append(entry.getKey()).append('=').append(entry.getValue()));
            return serialized.toString().getBytes(StandardCharsets.UTF_8);
        }
    }
}
