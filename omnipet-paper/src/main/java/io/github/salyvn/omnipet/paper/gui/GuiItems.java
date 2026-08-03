package io.github.salyvn.omnipet.paper.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * The single inventory item builder shared by every OmniPet menu.
 *
 * <p>Minecraft renders item display names and lore italic unless the decoration is explicitly
 * disabled, so every component produced here resolves {@link TextDecoration#ITALIC} to
 * {@link TextDecoration.State#FALSE}. A caller that deliberately set italic keeps its choice.
 */
public final class GuiItems {
    /** Default color for descriptive lore lines. */
    public static final NamedTextColor LORE_COLOR = NamedTextColor.GRAY;

    private GuiItems() {}

    /** Builds a colored, non-italic label. */
    public static Component label(String text, NamedTextColor color) {
        return upright(Component.text(Objects.requireNonNull(text, "label text"), color));
    }

    /** Builds a non-italic descriptive lore line. */
    public static Component lore(String text) {
        return label(text, LORE_COLOR);
    }

    /** Disables italic unless the component already decided that decoration. */
    public static Component upright(Component component) {
        return Objects.requireNonNull(component, "component")
                .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    /** Builds a new stack of the given material with a non-italic name and lore. */
    public static ItemStack of(Material material, Component name, List<Component> lore) {
        return of(new ItemStack(Objects.requireNonNull(material, "material")), name, lore);
    }

    /**
     * Applies a non-italic name and lore to an existing stack.
     *
     * <p>The stack's own meta instance is reused, so specialised meta such as a player-head skull
     * texture survives the call.
     */
    public static ItemStack of(ItemStack base, Component name, List<Component> lore) {
        Objects.requireNonNull(base, "base stack");
        Objects.requireNonNull(name, "display name");
        Objects.requireNonNull(lore, "lore");
        ItemMeta meta = base.getItemMeta();
        if (meta == null) throw new IllegalArgumentException("stack has no item meta: " + base.getType());
        meta.displayName(upright(name));
        List<Component> lines = new ArrayList<>(lore.size());
        for (Component line : lore) lines.add(upright(line));
        meta.lore(lines);
        base.setItemMeta(meta);
        return base;
    }

    /** The neutral background pane used to fill unused menu slots. */
    public static ItemStack filler() {
        return of(Material.GRAY_STAINED_GLASS_PANE, label(" ", LORE_COLOR), List.of());
    }
}
