package io.github.salyvn.omnipet.paper.config;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Operator-chosen layout and materials for one menu.
 *
 * <p>Buttons are keyed by a stable <em>name</em> rather than by slot, because a slot is what the
 * operator is allowed to change and several buttons pick their material from state — the hatch tile is
 * one material when ready, another while incubating, another when idle. Keying on the name lets an
 * operator restyle "the hatch tile" without having to enumerate every state it can be in, and lets a
 * state-dependent renderer keep choosing between its own variants.
 *
 * <p>Everything is optional. An absent menu, an absent button, or an absent field all mean "use what
 * the plugin already produced", so a config with no {@code gui.menus:} renders exactly as before.
 */
public record MenuStyle(
        Optional<Integer> size,
        Optional<String> filler,
        Map<String, ButtonStyle> buttons) {
    /** A chest inventory is rows of nine, up to six rows. */
    public static final int SLOTS_PER_ROW = 9;
    public static final int MAX_SIZE = 54;

    public MenuStyle {
        size = size == null ? Optional.empty() : size;
        filler = filler == null ? Optional.empty() : filler.filter(value -> !value.isBlank());
        buttons = buttons == null ? Map.of() : Map.copyOf(buttons);
    }

    public static MenuStyle defaults() {
        return new MenuStyle(Optional.empty(), Optional.empty(), Map.of());
    }

    /** The configured style for one button, or an empty style when the operator named none. */
    public ButtonStyle button(String name) {
        return buttons.getOrDefault(Objects.requireNonNull(name, "button name"), ButtonStyle.defaults());
    }

    public boolean isDefault() {
        return size.isEmpty() && filler.isEmpty() && buttons.isEmpty();
    }

    /** Whether {@code candidate} is a usable inventory size: a multiple of nine, at most six rows. */
    public static boolean validSize(int candidate) {
        return candidate > 0 && candidate <= MAX_SIZE && candidate % SLOTS_PER_ROW == 0;
    }

    /**
     * One button's material and position.
     *
     * <p>A material here replaces every state variant the renderer would have chosen. That is the
     * operator's call to make: naming one material for a tile that has three states means they want the
     * tile to look the same in all of them.
     */
    public record ButtonStyle(Optional<String> material, Optional<Integer> slot) {
        public ButtonStyle {
            material = material == null ? Optional.empty() : material.filter(value -> !value.isBlank());
            slot = slot == null ? Optional.empty() : slot;
        }

        public static ButtonStyle defaults() {
            return new ButtonStyle(Optional.empty(), Optional.empty());
        }

        public boolean isDefault() {
            return material.isEmpty() && slot.isEmpty();
        }
    }

    /** Every menu an operator may restyle, and the button names each one accepts. */
    public static Map<String, java.util.Set<String>> knownMenus() {
        Map<String, java.util.Set<String>> menus = new LinkedHashMap<>();
        menus.put("hub", java.util.Set.of("vault", "hatch", "slots", "help", "studio"));
        menus.put("vault", java.util.Set.of(
                "previous", "next", "hub", "sort", "filter", "status", "unlockSlot"));
        menus.put("hatch", java.util.Set.of(
                "startMain", "startOff", "incubation", "hub", "refresh", "redeemMain", "redeemOff"));
        menus.put("management", java.util.Set.of(
                "favorite", "lock", "moveUp", "moveDown", "candy", "breakthrough", "release",
                "refresh", "back", "hub"));
        menus.put("slot", java.util.Set.of("payVault", "payPoints", "confirm", "cancel", "hub"));
        menus.put("adminTransactions", java.util.Set.of("row", "refresh", "next"));
        menus.put("adminReconcile", java.util.Set.of(
                "charge", "noCharge", "refund", "sync", "subject", "back"));
        return Map.copyOf(menus);
    }
}
