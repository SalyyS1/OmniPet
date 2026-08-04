package io.github.salyvn.omnipet.paper.incubation;

import java.util.List;
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

import net.kyori.adventure.text.Component;

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

    /**
     * Whether the stack carries OmniPet egg identity, without deriving a fingerprint.
     *
     * <p>Cheap enough for a hot event handler: it reads one PDC key and never serializes the stack.
     * Deliberately accepts a malformed or legacy identity too — for deciding "is this ours, leave it
     * alone", an egg with a broken schema still must not be treated as a vanilla block.
     */
    public boolean carriesEggIdentity(ItemStack source) {
        if (source == null || source.getType() == Material.AIR) return false;
        ItemMeta meta = source.getItemMeta();
        if (meta == null) return false;
        PersistentDataContainer data = meta.getPersistentDataContainer();
        return data.get(eggKey, PersistentDataType.STRING) != null
                || data.get(legacyEggKey, PersistentDataType.STRING) != null;
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

    /**
     * Mints a fresh, unstarted egg item for {@code eggId}.
     *
     * <p>Until this existed there was no way to obtain an egg in-game at all: the codec could read and
     * restore an egg but never sign a new one, and no command distributed them, so a pet created in the
     * Studio could not be reached.
     *
     * <p>Writes the same three keys and {@code ITEM_SCHEMA} that {@link #observe} validates, so a minted
     * egg is accepted by {@link #capture} by construction. It deliberately does not precompute a
     * fingerprint: that is derived at capture time from the serialized stack, and duplicating it here
     * would create a second source of truth that could disagree.
     *
     * @param material the egg's item type
     * @param name display name, already coloured by the caller
     * @param lore description lines, already coloured by the caller
     */
    public ItemStack create(String eggId, Material material, Component name, List<Component> lore) {
        Objects.requireNonNull(material, "egg material");
        if (material == Material.AIR) throw new IllegalArgumentException("egg material cannot be air");
        // Always one: each egg carries its own nonce, so a stack of two would share an identity and the
        // escrow saga could not tell the paid egg from its neighbour.
        return sign(new ItemStack(material, 1), eggId, name, lore);
    }

    /**
     * Stamps egg identity onto an existing stack.
     *
     * <p>Split out from {@link #create} because {@code new ItemStack(...)} needs a live server and so
     * cannot run in a unit test, while this — the part that actually decides whether
     * {@link #capture} will accept the result — can be driven against a fake stack.
     */
    ItemStack sign(ItemStack stack, String eggId, Component name, List<Component> lore) {
        String id = StableId.requireValid(eggId);
        ItemMeta meta = requireMeta(stack);
        if (name != null) meta.displayName(name);
        if (lore != null && !lore.isEmpty()) meta.lore(List.copyOf(lore));
        PersistentDataContainer data = meta.getPersistentDataContainer();
        data.set(eggKey, PersistentDataType.STRING, id);
        data.set(nonceKey, PersistentDataType.STRING,
                Objects.requireNonNull(nonceSupplier.get(), "item nonce").toString());
        data.set(schemaKey, PersistentDataType.INTEGER, ITEM_SCHEMA);
        stack.setItemMeta(meta);
        return stack;
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
