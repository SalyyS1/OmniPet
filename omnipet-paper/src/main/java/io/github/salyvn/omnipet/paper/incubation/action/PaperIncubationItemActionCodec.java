package io.github.salyvn.omnipet.paper.incubation.action;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import io.github.salyvn.omnipet.core.incubation.EggInventoryHand;
import io.github.salyvn.omnipet.core.incubation.EggItemIdentity;
import io.github.salyvn.omnipet.core.incubation.IncubationItemActionItemContract;
import io.github.salyvn.omnipet.core.incubation.IncubationItemActionType;
import io.github.salyvn.omnipet.paper.incubation.ObservedEggStack;

public final class PaperIncubationItemActionCodec {
    private static final int ITEM_SCHEMA = 1;
    private static final String INVALID_FINGERPRINT = "invalid";
    private static final String INVALID_ACTION_ID = "invalid_action";

    private final NamespacedKey schemaKey;
    private final NamespacedKey typeKey;
    private final NamespacedKey effectKey;
    private final NamespacedKey nonceKey;
    private final Supplier<UUID> nonceSupplier;
    private final Function<Material, ItemStack> itemFactory;
    private final Function<byte[], ItemStack> itemDeserializer;

    public PaperIncubationItemActionCodec(Plugin plugin) {
        this(
                new NamespacedKey(Objects.requireNonNull(plugin, "plugin"), "incubation_action_schema"),
                new NamespacedKey(plugin, "incubation_action_type"),
                new NamespacedKey(plugin, "incubation_action_effect_ms"),
                new NamespacedKey(plugin, "incubation_action_nonce"),
                UUID::randomUUID,
                ItemStack::new,
                ItemStack::deserializeBytes);
    }

    PaperIncubationItemActionCodec(
            NamespacedKey schemaKey,
            NamespacedKey typeKey,
            NamespacedKey effectKey,
            NamespacedKey nonceKey,
            Supplier<UUID> nonceSupplier,
            Function<Material, ItemStack> itemFactory,
            Function<byte[], ItemStack> itemDeserializer) {
        this.schemaKey = Objects.requireNonNull(schemaKey, "action schema key");
        this.typeKey = Objects.requireNonNull(typeKey, "action type key");
        this.effectKey = Objects.requireNonNull(effectKey, "action effect key");
        this.nonceKey = Objects.requireNonNull(nonceKey, "action nonce key");
        this.nonceSupplier = Objects.requireNonNull(nonceSupplier, "action nonce supplier");
        this.itemFactory = Objects.requireNonNull(itemFactory, "action item factory");
        this.itemDeserializer = Objects.requireNonNull(itemDeserializer, "action item deserializer");
    }

    public ItemStack createReducer(long effectMillis) {
        return createReducer(Material.CLOCK, effectMillis);
    }

    public ItemStack createReducer(Material material, long effectMillis) {
        return create(material, IncubationItemActionType.REDUCE, effectMillis);
    }

    public ItemStack createInstantHatch() {
        return createInstantHatch(Material.NETHER_STAR);
    }

    public ItemStack createInstantHatch(Material material) {
        return create(material, IncubationItemActionType.COMPLETE, 0);
    }

    public CapturedIncubationItemAction capture(ItemStack source, int slot, EggInventoryHand hand) {
        ItemStack stack = requireItem(source).clone();
        ActionEffect action = requireAction(stack);
        UUID nonce = freshNonce();
        ItemMeta meta = requireMeta(stack);
        meta.getPersistentDataContainer().set(nonceKey, PersistentDataType.STRING, nonce.toString());
        stack.setItemMeta(meta);

        byte[] payload = serializeAmountOne(stack);
        EggItemIdentity identity = new EggItemIdentity(
                slot,
                Objects.requireNonNull(hand, "incubation action item hand"),
                stack.getType().getKey().asString(),
                nonce,
                PaperIncubationItemActionSnapshot.fingerprint(payload),
                stack.getAmount(),
                PaperIncubationItemActionSnapshot.extensions(payload, action.type(), action.effectMillis()));
        return new CapturedIncubationItemAction(stack, action.type(), action.effectMillis(), identity);
    }

    /**
     * What a support item would do, without touching it.
     *
     * <p>Read-only and side-effect free, unlike {@link #capture}, which stamps a fresh nonce into the stack
     * so escrow can match it. Placed eggs need to describe an item in a menu before the player has decided
     * to spend it, and re-nonceing an item the player is only looking at would invalidate anything already
     * referring to it.
     *
     * <p>Empty for a stack that is not one of ours, and for one of ours whose identity does not read back —
     * a menu must not offer to spend an item this codec would later refuse.
     */
    public Optional<SupportEffect> effect(ItemStack source) {
        if (source == null || source.getType() == Material.AIR || source.getAmount() < 1) {
            return Optional.empty();
        }
        ItemMeta meta = source.getItemMeta();
        if (meta == null) return Optional.empty();
        return readAction(meta.getPersistentDataContainer())
                .map(action -> new SupportEffect(action.type(), action.effectMillis()));
    }

    /** A support item's declared type and magnitude. */
    public record SupportEffect(IncubationItemActionType type, long effectMillis) {
        public SupportEffect {
            Objects.requireNonNull(type, "support effect type");
        }

        public boolean reducer() {
            return type == IncubationItemActionType.REDUCE;
        }

        public boolean instant() {
            return type == IncubationItemActionType.COMPLETE;
        }
    }

    public Optional<ObservedEggStack> observe(ItemStack source, int slot) {        if (source == null || source.getType() == Material.AIR || source.getAmount() < 1) return Optional.empty();
        ItemMeta meta = source.getItemMeta();
        if (meta == null) return Optional.empty();
        PersistentDataContainer data = meta.getPersistentDataContainer();
        if (!hasAnyMarker(data)) return Optional.empty();

        Optional<ActionEffect> action = readAction(data);
        Optional<UUID> nonce = readNonce(data);
        boolean identityValid = action.isPresent() && nonce.isPresent();
        String fingerprint = INVALID_FINGERPRINT;
        if (identityValid) {
            try {
                fingerprint = PaperIncubationItemActionSnapshot.fingerprint(serializeAmountOne(source));
            } catch (IllegalArgumentException invalidSnapshot) {
                identityValid = false;
            }
        }
        String actionId = action.map(value -> value.type().name().toLowerCase(Locale.ROOT))
                .orElse(INVALID_ACTION_ID);
        return Optional.of(new ObservedEggStack(
                slot,
                source.getType().getKey().asString(),
                actionId,
                nonce.orElse(null),
                fingerprint,
                source.getAmount(),
                source.getMaxStackSize(),
                identityValid));
    }

    public ItemStack restore(EggItemIdentity identity) {
        byte[] payload = PaperIncubationItemActionSnapshot.payload(identity);
        if (!PaperIncubationItemActionSnapshot.fingerprint(payload).equals(identity.fingerprint())) {
            throw new IllegalArgumentException("Paper incubation action snapshot fingerprint does not match identity");
        }
        ItemStack restored = requireItem(itemDeserializer.apply(payload));
        restored.setAmount(1);
        ActionEffect action = requireAction(restored);
        IncubationItemActionItemContract.requireMatches(identity, action.type(), action.effectMillis());
        ObservedEggStack observed = observe(restored, identity.inventorySlot())
                .orElseThrow(() -> new IllegalArgumentException("Paper incubation action snapshot lacks canonical identity"));
        if (!observed.identityValid()
                || !identity.materialKey().equals(observed.materialKey())
                || !identity.itemNonce().equals(observed.nonce())
                || !identity.fingerprint().equals(observed.fingerprint())) {
            throw new IllegalArgumentException("Paper incubation action snapshot identity does not match transaction identity");
        }
        return restored;
    }

    boolean supports(EggItemIdentity identity) {
        try {
            restore(identity);
            return true;
        } catch (RuntimeException invalid) {
            return false;
        }
    }

    private ItemStack create(Material material, IncubationItemActionType type, long effectMillis) {
        validateEffect(type, effectMillis);
        if (material == null || material == Material.AIR) {
            throw new IllegalArgumentException("incubation action material must be an item");
        }
        ItemStack stack = requireItem(itemFactory.apply(material));
        stack.setAmount(1);
        ItemMeta meta = requireMeta(stack);
        PersistentDataContainer data = meta.getPersistentDataContainer();
        data.set(schemaKey, PersistentDataType.INTEGER, ITEM_SCHEMA);
        data.set(typeKey, PersistentDataType.STRING, type.name());
        data.set(effectKey, PersistentDataType.LONG, effectMillis);
        data.set(nonceKey, PersistentDataType.STRING, freshNonce().toString());
        stack.setItemMeta(meta);
        return stack;
    }

    private ActionEffect requireAction(ItemStack stack) {
        ItemMeta meta = requireMeta(stack);
        return readAction(meta.getPersistentDataContainer())
                .orElseThrow(() -> new IllegalArgumentException("malformed or unsupported incubation action item"));
    }

    private Optional<ActionEffect> readAction(PersistentDataContainer data) {
        Integer schema = data.get(schemaKey, PersistentDataType.INTEGER);
        String typeName = data.get(typeKey, PersistentDataType.STRING);
        Long effectMillis = data.get(effectKey, PersistentDataType.LONG);
        if (schema == null || schema != ITEM_SCHEMA || typeName == null || effectMillis == null) {
            return Optional.empty();
        }
        try {
            IncubationItemActionType type = IncubationItemActionType.valueOf(typeName);
            validateEffect(type, effectMillis);
            return Optional.of(new ActionEffect(type, effectMillis));
        } catch (IllegalArgumentException invalid) {
            return Optional.empty();
        }
    }

    private Optional<UUID> readNonce(PersistentDataContainer data) {
        String raw = data.get(nonceKey, PersistentDataType.STRING);
        if (raw == null) return Optional.empty();
        try {
            return Optional.of(UUID.fromString(raw));
        } catch (IllegalArgumentException invalid) {
            return Optional.empty();
        }
    }

    private boolean hasAnyMarker(PersistentDataContainer data) {
        return data.has(schemaKey) || data.has(typeKey) || data.has(effectKey) || data.has(nonceKey);
    }

    private UUID freshNonce() {
        return Objects.requireNonNull(nonceSupplier.get(), "incubation action item nonce");
    }

    private static void validateEffect(IncubationItemActionType type, long effectMillis) {
        Objects.requireNonNull(type, "incubation action item type");
        if (type == IncubationItemActionType.REDUCE && effectMillis <= 0) {
            throw new IllegalArgumentException("REDUCE action item effect must be positive");
        }
        if (type == IncubationItemActionType.COMPLETE && effectMillis != 0) {
            throw new IllegalArgumentException("COMPLETE action item effect must be zero");
        }
    }

    private static byte[] serializeAmountOne(ItemStack item) {
        ItemStack normalized = item.clone();
        normalized.setAmount(1);
        return normalized.serializeAsBytes();
    }

    private static ItemStack requireItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || item.getAmount() < 1) {
            throw new IllegalArgumentException("incubation action item stack is required");
        }
        return item;
    }

    private static ItemMeta requireMeta(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) throw new IllegalArgumentException("incubation action item metadata is required");
        return meta;
    }

    private record ActionEffect(IncubationItemActionType type, long effectMillis) {}
}
