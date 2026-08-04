package io.github.salyvn.omnipet.paper.management;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import net.kyori.adventure.text.Component;

import io.github.salyvn.omnipet.paper.config.OmniPetConfig;
import io.github.salyvn.omnipet.core.incubation.EggEscrowItemObservation;
import io.github.salyvn.omnipet.core.incubation.EggInventoryHand;
import io.github.salyvn.omnipet.core.incubation.EggItemIdentity;
import io.github.salyvn.omnipet.paper.incubation.EggInventoryMutationResult;
import io.github.salyvn.omnipet.paper.text.Durations;
import io.github.salyvn.omnipet.paper.text.MessageKey;
import io.github.salyvn.omnipet.paper.text.Messages;

/** Exact PDC identity for standalone cultivation items; generic materials never qualify. */
public final class PaperPetConsumableInventory implements PetConsumableInventoryPort {
    private static final int SCHEMA = 1;
    private final NamespacedKey schemaKey;
    private final NamespacedKey typeKey;
    private final NamespacedKey nonceKey;
    private final NamespacedKey experienceKey;
    private final NamespacedKey requiredLevelKey;
    private final NamespacedKey requiredEvolutionKey;
    private final Set<UUID> consumed = ConcurrentHashMap.newKeySet();
    private volatile OmniPetConfig.CultivationItems config;

    public PaperPetConsumableInventory(Plugin plugin, OmniPetConfig.CultivationItems config) {
        Objects.requireNonNull(plugin, "cultivation item plugin");
        schemaKey = new NamespacedKey(plugin, "cultivation_schema");
        typeKey = new NamespacedKey(plugin, "cultivation_type");
        nonceKey = new NamespacedKey(plugin, "cultivation_nonce");
        experienceKey = new NamespacedKey(plugin, "cultivation_experience");
        requiredLevelKey = new NamespacedKey(plugin, "cultivation_required_level");
        requiredEvolutionKey = new NamespacedKey(plugin, "cultivation_required_evolution");
        updateConfig(config);
    }

    public void updateConfig(OmniPetConfig.CultivationItems next) {
        Objects.requireNonNull(next, "cultivation item config");
        material(next.experienceMaterial());
        material(next.breakthroughMaterial());
        config = next;
    }

    public ItemStack create(Kind kind) {
        requireMainThread();
        OmniPetConfig.CultivationItems current = config;
        boolean candy = kind == Kind.EXPERIENCE_CANDY;
        ItemStack item = new ItemStack(material(candy
                ? current.experienceMaterial() : current.breakthroughMaterial()));
        item.setAmount(1);
        ItemMeta meta = requireMeta(item);
        // Named and described through the catalog: these are vanilla materials, so an unlabelled one is
        // indistinguishable from an ordinary bottle or star and says nothing about how to redeem it.
        meta.displayName(Messages.line(candy
                ? MessageKey.GUI_CANDY_ITEM_NAME : MessageKey.GUI_BREAKTHROUGH_ITEM_NAME));
        meta.lore(java.util.List.of(
                candy
                        ? Messages.line(MessageKey.GUI_CANDY_ITEM_DETAIL,
                                Messages.of("detail", Durations.decimal(current.experienceAmount())))
                        : Messages.line(MessageKey.GUI_BREAKTHROUGH_ITEM_DETAIL,
                                Messages.of("detail", String.valueOf(current.breakthroughRequiredLevel()))),
                Component.empty(),
                Messages.line(candy
                        ? MessageKey.GUI_CANDY_ITEM_HINT : MessageKey.GUI_BREAKTHROUGH_ITEM_HINT)));
        PersistentDataContainer data = meta.getPersistentDataContainer();
        data.set(schemaKey, PersistentDataType.INTEGER, SCHEMA);
        data.set(typeKey, PersistentDataType.STRING, kind.name());
        data.set(nonceKey, PersistentDataType.STRING, UUID.randomUUID().toString());
        data.set(experienceKey, PersistentDataType.DOUBLE,
                candy ? current.experienceAmount() : 0D);
        data.set(requiredLevelKey, PersistentDataType.INTEGER,
                kind == Kind.BREAKTHROUGH_STONE ? current.breakthroughRequiredLevel() : 1);
        data.set(requiredEvolutionKey, PersistentDataType.INTEGER,
                kind == Kind.BREAKTHROUGH_STONE ? current.breakthroughRequiredEvolution() : 0);
        item.setItemMeta(meta);
        return item;
    }

    @Override
    public CaptureResult capture(UUID viewerId, Kind kind) {
        requireMainThread();
        Player player = Bukkit.getPlayer(viewerId);
        if (player == null || !player.isOnline()) return capture(CaptureResult.Status.OFFLINE, null, "player is offline");
        int slot = player.getInventory().getHeldItemSlot();
        ItemStack item = player.getInventory().getItem(slot);
        Decoded decoded = decode(item);
        if (decoded == null) return capture(CaptureResult.Status.NOT_FOUND, null,
                "hold an issued OmniPet cultivation item in the main hand");
        if (decoded.kind() != kind) return capture(CaptureResult.Status.NOT_FOUND, null,
                "the main-hand cultivation item has the wrong action type");
        byte[] payload = normalizedBytes(item);
        EggItemIdentity identity = new EggItemIdentity(
                slot, EggInventoryHand.MAIN_HAND, item.getType().getKey().asString(), decoded.nonce(),
                fingerprint(payload), 1, PaperCultivationItemSnapshot.extensions(payload));
        Capture receipt = new Capture(
                viewerId, identity, kind,
                decoded.experience(), decoded.requiredLevel(), decoded.requiredEvolution());
        return capture(CaptureResult.Status.CAPTURED, receipt, "cultivation item captured");
    }

    @Override
    public ConsumeResult consumeOne(Capture capture) {
        requireMainThread();
        if (consumed.contains(capture.nonce())) {
            return consume(ConsumeResult.Status.ALREADY_CONSUMED, "cultivation item was already consumed in this runtime");
        }
        Player player = Bukkit.getPlayer(capture.viewerId());
        if (player == null || !player.isOnline()) return consume(ConsumeResult.Status.OFFLINE, "player is offline");
        ItemStack current = player.getInventory().getItem(capture.slot());
        Decoded decoded = decode(current);
        if (decoded == null || decoded.kind() != capture.kind() || !decoded.nonce().equals(capture.nonce())) {
            return consume(ConsumeResult.Status.NOT_MATCHING, "captured cultivation item identity changed");
        }
        if (!fingerprint(normalizedBytes(current)).equals(capture.fingerprint())) {
            return consume(ConsumeResult.Status.AMBIGUOUS, "captured cultivation item fingerprint changed");
        }
        if (current.getAmount() != 1) {
            return consume(ConsumeResult.Status.AMBIGUOUS, "cultivation items must remain unstacked");
        }
        player.getInventory().setItem(capture.slot(), null);
        consumed.add(capture.nonce());
        return consume(ConsumeResult.Status.CONSUMED, "exact cultivation item consumed");
    }

    @Override
    public EggEscrowItemObservation observe(UUID playerId, EggItemIdentity identity) {
        requireMainThread();
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) return EggEscrowItemObservation.AMBIGUOUS;
        ItemStack current = player.getInventory().getItem(identity.inventorySlot());
        if (current != null && !current.getType().isAir()) {
            return matches(current, identity)
                    ? EggEscrowItemObservation.MATCHING_ITEM_PRESENT
                    : EggEscrowItemObservation.AMBIGUOUS;
        }
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            if (slot == identity.inventorySlot()) continue;
            ItemStack candidate = player.getInventory().getItem(slot);
            Decoded decoded = decode(candidate);
            if (decoded != null && decoded.nonce().equals(identity.itemNonce())) {
                return EggEscrowItemObservation.AMBIGUOUS;
            }
        }
        return EggEscrowItemObservation.MATCHING_ITEM_ABSENT;
    }

    @Override
    public EggInventoryMutationResult removeOne(UUID playerId, EggItemIdentity identity) {
        requireMainThread();
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) return EggInventoryMutationResult.FAILED;
        ItemStack current = player.getInventory().getItem(identity.inventorySlot());
        if (current == null || current.getType().isAir()) return EggInventoryMutationResult.NOT_MATCHING;
        if (!matches(current, identity)) return EggInventoryMutationResult.AMBIGUOUS;
        player.getInventory().setItem(identity.inventorySlot(), null);
        consumed.add(identity.itemNonce());
        return EggInventoryMutationResult.REMOVED;
    }

    @Override
    public EggInventoryMutationResult refundOne(UUID playerId, EggItemIdentity identity) {
        requireMainThread();
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) return EggInventoryMutationResult.FAILED;
        ItemStack current = player.getInventory().getItem(identity.inventorySlot());
        if (current != null && !current.getType().isAir()) {
            return matches(current, identity)
                    ? EggInventoryMutationResult.ALREADY_PRESENT
                    : EggInventoryMutationResult.AMBIGUOUS;
        }
        try {
            ItemStack restored = PaperCultivationItemSnapshot.restore(identity);
            if (!matches(restored, identity)) return EggInventoryMutationResult.AMBIGUOUS;
            player.getInventory().setItem(identity.inventorySlot(), restored);
            consumed.remove(identity.itemNonce());
            return EggInventoryMutationResult.REFUNDED;
        } catch (RuntimeException failure) {
            return EggInventoryMutationResult.AMBIGUOUS;
        }
    }

    private Decoded decode(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || item.getAmount() != 1) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        PersistentDataContainer data = meta.getPersistentDataContainer();
        Integer schema = data.get(schemaKey, PersistentDataType.INTEGER);
        String type = data.get(typeKey, PersistentDataType.STRING);
        String nonce = data.get(nonceKey, PersistentDataType.STRING);
        Double experience = data.get(experienceKey, PersistentDataType.DOUBLE);
        Integer level = data.get(requiredLevelKey, PersistentDataType.INTEGER);
        Integer evolution = data.get(requiredEvolutionKey, PersistentDataType.INTEGER);
        if (schema == null || schema != SCHEMA || type == null || nonce == null
                || experience == null || level == null || evolution == null) return null;
        try {
            Kind kind = Kind.valueOf(type);
            UUID id = UUID.fromString(nonce);
            if (!Double.isFinite(experience) || experience < 0 || level < 1 || evolution < 0) return null;
            return new Decoded(kind, id, experience, level, evolution);
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    private static String fingerprint(byte[] payload) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(payload));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static byte[] normalizedBytes(ItemStack item) {
        ItemStack normalized = item.clone();
        normalized.setAmount(1);
        byte[] payload = normalized.serializeAsBytes();
        if (payload.length == 0 || payload.length > 8 * 1024) {
            throw new IllegalArgumentException("cultivation item snapshot exceeds 8 KiB");
        }
        return payload;
    }

    private boolean matches(ItemStack item, EggItemIdentity identity) {
        if (item == null || item.getAmount() != 1
                || !item.getType().getKey().asString().equals(identity.materialKey())) return false;
        Decoded decoded = decode(item);
        return decoded != null
                && decoded.nonce().equals(identity.itemNonce())
                && fingerprint(normalizedBytes(item)).equals(identity.fingerprint());
    }

    private static Material material(String name) {
        Material material = Material.matchMaterial(name);
        if (material == null || material == Material.AIR || !material.isItem()) {
            throw new IllegalArgumentException("unknown cultivation item material: " + name);
        }
        return material;
    }

    private static ItemMeta requireMeta(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) throw new IllegalArgumentException("cultivation item has no metadata");
        return meta;
    }

    private static void requireMainThread() {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("cultivation inventory requires the Paper main thread");
    }

    private static CaptureResult capture(CaptureResult.Status status, Capture capture, String detail) {
        return new CaptureResult(status, capture, detail);
    }

    private static ConsumeResult consume(ConsumeResult.Status status, String detail) {
        return new ConsumeResult(status, detail);
    }

    private record Decoded(Kind kind, UUID nonce, double experience, int requiredLevel, int requiredEvolution) {}
}
