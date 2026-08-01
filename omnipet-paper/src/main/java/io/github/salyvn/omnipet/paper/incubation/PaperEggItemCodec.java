package io.github.salyvn.omnipet.paper.incubation;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import io.github.salyvn.omnipet.core.domain.StableId;
import io.github.salyvn.omnipet.core.incubation.EggInventoryHand;
import io.github.salyvn.omnipet.core.incubation.EggItemIdentity;

public final class PaperEggItemCodec {
    private static final int ITEM_SCHEMA = 1;
    private static final String INVALID_FINGERPRINT = "invalid";

    private final NamespacedKey eggKey;
    private final NamespacedKey nonceKey;
    private final NamespacedKey schemaKey;
    private final NamespacedKey legacyEggKey;
    private final Supplier<UUID> nonceSupplier;

    public PaperEggItemCodec(Plugin plugin) {
        this(
                new NamespacedKey(Objects.requireNonNull(plugin, "plugin"), "egg"),
                new NamespacedKey(plugin, "item_nonce"),
                new NamespacedKey(plugin, "item_schema"),
                Objects.requireNonNull(NamespacedKey.fromString("passivepet:egg")),
                UUID::randomUUID);
    }

    PaperEggItemCodec(
            NamespacedKey eggKey,
            NamespacedKey nonceKey,
            NamespacedKey schemaKey,
            NamespacedKey legacyEggKey,
            Supplier<UUID> nonceSupplier) {
        this.eggKey = Objects.requireNonNull(eggKey, "egg key");
        this.nonceKey = Objects.requireNonNull(nonceKey, "nonce key");
        this.schemaKey = Objects.requireNonNull(schemaKey, "schema key");
        this.legacyEggKey = Objects.requireNonNull(legacyEggKey, "legacy egg key");
        this.nonceSupplier = Objects.requireNonNull(nonceSupplier, "nonce supplier");
    }

    public CapturedEggItem capture(ItemStack source, int slot, EggInventoryHand hand) {
        ItemStack stack = requireItem(source).clone();
        ItemMeta meta = requireMeta(stack);
        PersistentDataContainer data = meta.getPersistentDataContainer();
        requireSupportedSchemaForCapture(data);
        String eggId = data.get(eggKey, PersistentDataType.STRING);
        if (eggId == null) eggId = data.get(legacyEggKey, PersistentDataType.STRING);
        eggId = StableId.requireValid(eggId);
        UUID nonce = Objects.requireNonNull(nonceSupplier.get(), "item nonce");
        data.set(eggKey, PersistentDataType.STRING, eggId);
        data.set(nonceKey, PersistentDataType.STRING, nonce.toString());
        data.set(schemaKey, PersistentDataType.INTEGER, ITEM_SCHEMA);
        stack.setItemMeta(meta);

        byte[] payload = serializeAmountOne(stack);
        EggItemIdentity identity = new EggItemIdentity(
                slot,
                hand,
                stack.getType().getKey().asString(),
                nonce,
                PaperEggItemSnapshot.fingerprint(payload),
                stack.getAmount(),
                PaperEggItemSnapshot.extensions(payload));
        return new CapturedEggItem(stack, eggId, identity);
    }

    public Optional<ObservedEggStack> observe(ItemStack source, int slot) {
        if (source == null || source.getType() == Material.AIR || source.getAmount() < 1) return Optional.empty();
        ItemMeta meta = source.getItemMeta();
        if (meta == null) return Optional.empty();
        PersistentDataContainer data = meta.getPersistentDataContainer();
        String eggId = data.get(eggKey, PersistentDataType.STRING);
        if (eggId == null) eggId = data.get(legacyEggKey, PersistentDataType.STRING);
        if (eggId == null) return Optional.empty();
        Optional<UUID> nonce = readNonce(data);
        boolean identityValid = nonce.isPresent() && hasSupportedSchema(data);
        String fingerprint;
        try {
            if (!identityValid) {
                throw new IllegalArgumentException("unsupported Paper egg item schema");
            }
            fingerprint = PaperEggItemSnapshot.fingerprint(serializeAmountOne(source));
        } catch (IllegalArgumentException invalidSnapshot) {
            fingerprint = INVALID_FINGERPRINT;
        }
        return Optional.of(new ObservedEggStack(
                slot,
                source.getType().getKey().asString(),
                eggId,
                nonce.orElse(null),
                fingerprint,
                source.getAmount(),
                source.getMaxStackSize(),
                identityValid));
    }

    public ItemStack restore(EggItemIdentity identity) {
        byte[] payload = PaperEggItemSnapshot.payload(identity);
        if (!PaperEggItemSnapshot.fingerprint(payload).equals(identity.fingerprint())) {
            throw new IllegalArgumentException("Paper egg item snapshot fingerprint does not match escrow identity");
        }
        ItemStack restored = ItemStack.deserializeBytes(payload);
        restored.setAmount(1);
        ObservedEggStack observed = observe(restored, identity.inventorySlot())
                .orElseThrow(() -> new IllegalArgumentException("Paper egg item snapshot lacks canonical identity"));
        if (!identity.materialKey().equals(observed.materialKey())
                || !identity.itemNonce().equals(observed.nonce())
                || !identity.fingerprint().equals(observed.fingerprint())) {
            throw new IllegalArgumentException("Paper egg item snapshot identity does not match escrow identity");
        }
        return restored;
    }

    private Optional<UUID> readNonce(PersistentDataContainer data) {
        String raw = data.get(nonceKey, PersistentDataType.STRING);
        if (raw == null) return Optional.empty();
        try { return Optional.of(UUID.fromString(raw)); }
        catch (IllegalArgumentException invalid) { return Optional.empty(); }
    }

    private void requireSupportedSchemaForCapture(PersistentDataContainer data) {
        if (data.has(schemaKey) && !hasSupportedSchema(data)) {
            throw new IllegalArgumentException("unsupported Paper egg item schema");
        }
    }

    private boolean hasSupportedSchema(PersistentDataContainer data) {
        Integer schema = data.get(schemaKey, PersistentDataType.INTEGER);
        return schema != null && schema == ITEM_SCHEMA;
    }

    private static byte[] serializeAmountOne(ItemStack item) {
        ItemStack normalized = item.clone();
        normalized.setAmount(1);
        return normalized.serializeAsBytes();
    }

    private static ItemStack requireItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || item.getAmount() < 1) {
            throw new IllegalArgumentException("egg item stack is required");
        }
        return item;
    }

    private static ItemMeta requireMeta(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) throw new IllegalArgumentException("egg item metadata is required");
        return meta;
    }
}
