package io.github.salyvn.omnipet.paper.item;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import net.kyori.adventure.text.Component;

import io.github.salyvn.omnipet.paper.config.ItemAppearance;

/**
 * Applies operator-configured appearance to an OmniPet item.
 *
 * <p>Everything here is cosmetic. It runs at mint time, before the item's escrow fingerprint is ever
 * derived, so the resulting stack is simply the stack that gets hashed. It must never touch the
 * persistent-data keys, the item schema, or the amount, all of which escrow matches on.
 *
 * <p>Every value degrades individually. An unknown material or item flag warns and is skipped rather
 * than failing the mint, because an operator's typo must not stop eggs being handed out.
 */
public final class ItemAppearanceApplier {
    private ItemAppearanceApplier() {}

    /**
     * Resolves the configured material, falling back when it is absent or unknown.
     *
     * @param warnings receives one message per rejected value, naming the key
     */
    public static Material material(
            ItemAppearance appearance, Material fallback, String path, Consumer<String> warnings) {
        Objects.requireNonNull(appearance, "item appearance");
        Objects.requireNonNull(fallback, "fallback material");
        Optional<String> configured = appearance.material();
        if (configured.isEmpty()) return fallback;
        String raw = configured.get().trim().toUpperCase(Locale.ROOT);
        Material resolved = Material.matchMaterial(raw);
        if (resolved == null || resolved == Material.AIR || !resolved.isItem()) {
            warnings.accept(path + ".material is not a usable item material: " + configured.get()
                    + "; using " + fallback.getKey().getKey());
            return fallback;
        }
        return resolved;
    }

    /**
     * Applies model data, glint, flags, and extra lore to a stack that already carries its identity.
     *
     * <p>Extra lore is appended after the built-in lines rather than replacing them, so an operator
     * adding a server tagline cannot accidentally delete the line telling the player how to use the
     * item.
     */
    public static void apply(
            ItemStack stack,
            ItemAppearance appearance,
            List<Component> extraLore,
            String path,
            Consumer<String> warnings) {
        Objects.requireNonNull(stack, "item stack");
        Objects.requireNonNull(appearance, "item appearance");
        if (appearance.isDefault() && (extraLore == null || extraLore.isEmpty())) return;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return;

        appearance.customModelData().ifPresent(meta::setCustomModelData);
        if (appearance.glint()) meta.setEnchantmentGlintOverride(true);
        for (String flag : appearance.itemFlags()) {
            ItemFlag parsed = itemFlag(flag);
            if (parsed == null) {
                warnings.accept(path + ".itemFlags has an unknown flag: " + flag);
                continue;
            }
            meta.addItemFlags(parsed);
        }
        if (extraLore != null && !extraLore.isEmpty()) {
            List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
            lore.addAll(extraLore);
            meta.lore(lore);
        }
        stack.setItemMeta(meta);
    }

    private static ItemFlag itemFlag(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return ItemFlag.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }
}
