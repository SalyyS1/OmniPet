package io.github.salyvn.omnipet.paper.incubation.action;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;

final class PaperIncubationActionTestItems {
    static final NamespacedKey SCHEMA = new NamespacedKey("omnipet", "incubation_action_schema");
    static final NamespacedKey TYPE = new NamespacedKey("omnipet", "incubation_action_type");
    static final NamespacedKey EFFECT = new NamespacedKey("omnipet", "incubation_action_effect_ms");
    static final NamespacedKey NONCE = new NamespacedKey("omnipet", "incubation_action_nonce");

    private PaperIncubationActionTestItems() {}

    static final class FakeItemStack extends ItemStack {
        private final Material material;
        private final int maxStackAmount;
        private final FakeItemData data;
        private int amount;

        FakeItemStack(Material material) {
            this(material, 1, 64, new FakeItemData());
        }

        private FakeItemStack(Material material, int amount, int maxStackAmount, FakeItemData data) {
            this.material = material;
            this.amount = amount;
            this.maxStackAmount = maxStackAmount;
            this.data = data;
        }

        Map<NamespacedKey, Object> values() {
            return data.values;
        }

        @Override public Material getType() { return material; }
        @Override public int getAmount() { return amount; }
        @Override public void setAmount(int amount) { this.amount = amount; }
        @Override public int getMaxStackSize() { return maxStackAmount; }
        @Override public ItemMeta getItemMeta() { return data.meta; }
        @Override public boolean setItemMeta(ItemMeta meta) { return meta == data.meta; }

        @Override
        public FakeItemStack clone() {
            return new FakeItemStack(material, amount, maxStackAmount, data.copy());
        }

        @Override
        public byte[] serializeAsBytes() {
            StringBuilder serialized = new StringBuilder(material.getKey().asString()).append(':').append(amount);
            data.values.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey(Comparator.comparing(NamespacedKey::toString)))
                    .forEach(entry -> serialized.append('|').append(entry.getKey()).append('=').append(entry.getValue()));
            return serialized.toString().getBytes(StandardCharsets.UTF_8);
        }
    }

    private static final class FakeItemData {
        private final Map<NamespacedKey, Object> values;
        private final PersistentDataContainer container;
        private final ItemMeta meta;

        private FakeItemData() {
            this(new LinkedHashMap<>());
        }

        private FakeItemData(Map<NamespacedKey, Object> values) {
            this.values = values;
            this.container = (PersistentDataContainer) Proxy.newProxyInstance(
                    PersistentDataContainer.class.getClassLoader(),
                    new Class<?>[] {PersistentDataContainer.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "set" -> { this.values.put((NamespacedKey) args[0], args[2]); yield null; }
                        case "get" -> this.values.get(args[0]);
                        case "has" -> this.values.containsKey(args[0]);
                        case "getKeys" -> Set.copyOf(this.values.keySet());
                        case "isEmpty" -> this.values.isEmpty();
                        case "getSize" -> this.values.size();
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
            this.meta = (ItemMeta) Proxy.newProxyInstance(
                    ItemMeta.class.getClassLoader(),
                    new Class<?>[] {ItemMeta.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("getPersistentDataContainer")) return container;
                        throw new UnsupportedOperationException(method.getName());
                    });
        }

        private FakeItemData copy() {
            return new FakeItemData(new LinkedHashMap<>(values));
        }
    }
}
