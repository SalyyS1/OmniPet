package io.github.salyvn.omnipet.paper.gui;

import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;

import org.bukkit.Material;

import io.github.salyvn.omnipet.paper.config.MenuStyle;

/**
 * Resolves an operator's slot and material choices for one button.
 *
 * <p>Shared because two menus had grown their own copy of this. Both refused a collision and both fell
 * back to the built-in slot, but only by coincidence: nothing kept the two from drifting, and a rule
 * fixed in one would have quietly stayed broken in the other.
 *
 * <p>Every rejection warns. A silently ignored slot is the worst outcome available here — the operator
 * sees their edit do nothing and has no way to tell whether the key was wrong, taken, or out of range.
 */
public final class MenuButtonStyle {
    private MenuButtonStyle() {}

    /**
     * The slot a button should occupy.
     *
     * @param claimant  the button already holding a slot, or null when it is free. Named in the warning,
     *                  because "already used" without saying by what leaves the operator guessing.
     * @param menuSize  the menu's size, or a non-positive value to skip the bounds check when the caller
     *                  checks it later against the real inventory
     * @return the configured slot when it is usable, otherwise {@code defaultSlot}
     */
    public static int slot(
            MenuStyle style,
            String menuPath,
            String button,
            int defaultSlot,
            int menuSize,
            java.util.function.IntFunction<String> claimant,
            Consumer<String> warnings) {
        int configured = style.button(button).slot().orElse(defaultSlot);
        if (configured == defaultSlot) return defaultSlot;
        String existing = claimant.apply(configured);
        if (existing != null) {
            warnings.accept(menuPath + ".buttons." + button + ".slot " + configured
                    + " is already used by " + existing + "; keeping its built-in slot " + defaultSlot);
            return defaultSlot;
        }
        if (menuSize > 0 && configured >= menuSize) {
            warnings.accept(menuPath + ".buttons." + button + ".slot " + configured
                    + " is outside this menu; keeping its built-in slot " + defaultSlot);
            return defaultSlot;
        }
        return configured;
    }

    /**
     * The material for a button.
     *
     * <p>A configured material replaces every state variant the renderer would pick between. That is
     * intentional: naming one material for a tile with three states says it should look the same in all
     * three.
     */
    public static Material material(
            MenuStyle style,
            String menuPath,
            String button,
            Material fallback,
            Consumer<String> warnings) {
        return style.button(button).material()
                .flatMap(name -> item(name, menuPath + ".buttons." + button + ".material", warnings))
                .orElse(fallback);
    }

    /** The named material, or empty after warning when it cannot be an item in an inventory. */
    public static Optional<Material> item(String raw, String path, Consumer<String> warnings) {
        Material resolved = Material.matchMaterial(raw.trim().toUpperCase(Locale.ROOT));
        if (resolved == null || resolved == Material.AIR || !resolved.isItem()) {
            warnings.accept(path + " is not a usable item material: " + raw + "; using the built-in one");
            return Optional.empty();
        }
        return Optional.of(resolved);
    }
}
