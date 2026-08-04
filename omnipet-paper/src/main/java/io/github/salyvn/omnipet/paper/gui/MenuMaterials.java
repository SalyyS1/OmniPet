package io.github.salyvn.omnipet.paper.gui;

import java.util.Locale;
import java.util.Objects;

import org.bukkit.Material;

import io.github.salyvn.omnipet.paper.config.GuiSettings;
import io.github.salyvn.omnipet.paper.config.MenuStyle;

/**
 * Resolves one button's operator-chosen material, for a renderer that manages its own layout.
 *
 * <p>{@link MenuLayout} is the right tool where the whole menu can be laid out freely. The vault is
 * not such a menu: pet rows occupy slots 0 through 44 and the bottom row carries the controls, and the
 * pagination arithmetic is derived from that shape. Its size and pet slots therefore stay fixed while
 * its control <em>materials</em> remain configurable, which is the part an operator actually asks for
 * when they want a menu to match their server's look.
 */
public final class MenuMaterials {
    private MenuMaterials() {}

    /** The configured material for {@code menu.button}, or {@code fallback}. */
    public static Material of(String menu, String button, Material fallback) {
        Objects.requireNonNull(menu, "menu name");
        Objects.requireNonNull(button, "button name");
        Objects.requireNonNull(fallback, "fallback material");
        MenuStyle style = GuiSettings.gui().menu(menu);
        return style.button(button).material()
                .map(name -> resolve(menu, "buttons." + button, name, fallback))
                .orElse(fallback);
    }

    /**
     * The configured background pane for a menu, or {@code fallback}.
     *
     * <p>Separate from {@link #of} because {@code filler} is a property of the menu rather than one of
     * its buttons, so it sits outside the {@code buttons:} map in config.
     */
    public static Material filler(String menu, Material fallback) {
        Objects.requireNonNull(menu, "menu name");
        Objects.requireNonNull(fallback, "fallback material");
        return GuiSettings.gui().menu(menu).filler()
                .map(name -> resolve(menu, "filler", name, fallback))
                .orElse(fallback);
    }

    private static Material resolve(String menu, String path, String name, Material fallback) {
        Material resolved = Material.matchMaterial(name.trim().toUpperCase(Locale.ROOT));
        if (resolved == null || resolved == Material.AIR || !resolved.isItem()) {
            GuiSettings.warn("gui.menus." + menu + "." + path
                    + " is not a usable item material: " + name + "; using the built-in one");
            return fallback;
        }
        return resolved;
    }
}
