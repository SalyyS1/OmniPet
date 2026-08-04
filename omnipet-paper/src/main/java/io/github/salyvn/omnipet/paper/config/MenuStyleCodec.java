package io.github.salyvn.omnipet.paper.config;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Reads the optional {@code gui.menus:} section.
 *
 * <p>Lenient, following the {@code messages.yml} and {@code gui:} precedent: an unknown menu, an
 * unknown button, or a bad value warns and falls back to what the plugin already drew. A menu layout is
 * cosmetic, and a typo in it must never stop players opening their vault.
 *
 * <p>Menu and button names are checked against {@link MenuStyle#knownMenus()} so a typo is reported at
 * load rather than silently doing nothing — a button the operator thought they had moved staying where
 * it was, with no explanation, is the confusing failure this avoids.
 */
public final class MenuStyleCodec {
    private static final Set<String> MENU_KEYS = Set.of("size", "filler", "buttons");
    private static final Set<String> BUTTON_KEYS = Set.of("material", "slot");

    /** Reads every configured menu, keyed by menu name. */
    public Map<String, MenuStyle> parse(Object node, String path, Consumer<String> warnings) {
        if (node == null) return Map.of();
        Map<String, Object> menus = map(node, path, warnings);
        if (menus.isEmpty()) return Map.of();
        Map<String, java.util.Set<String>> known = MenuStyle.knownMenus();
        LinkedHashMap<String, MenuStyle> result = new LinkedHashMap<>();
        menus.forEach((menu, value) -> {
            if (!known.containsKey(menu)) {
                warnings.accept(path + "." + menu + " is not a menu OmniPet can restyle; known menus are "
                        + known.keySet());
                return;
            }
            MenuStyle style = menu(value, path + "." + menu, known.get(menu), warnings);
            if (!style.isDefault()) result.put(menu, style);
        });
        return Map.copyOf(result);
    }

    /** Writes the section back, omitting any menu left entirely at its defaults. */
    public Map<String, Object> encode(Map<String, MenuStyle> menus) {
        LinkedHashMap<String, Object> root = new LinkedHashMap<>();
        menus.forEach((name, style) -> {
            if (style.isDefault()) return;
            LinkedHashMap<String, Object> node = new LinkedHashMap<>();
            style.size().ifPresent(size -> node.put("size", size));
            style.filler().ifPresent(filler -> node.put("filler", filler));
            LinkedHashMap<String, Object> buttons = new LinkedHashMap<>();
            style.buttons().forEach((button, buttonStyle) -> {
                if (buttonStyle.isDefault()) return;
                LinkedHashMap<String, Object> entry = new LinkedHashMap<>();
                buttonStyle.material().ifPresent(material -> entry.put("material", material));
                buttonStyle.slot().ifPresent(slot -> entry.put("slot", slot));
                buttons.put(button, entry);
            });
            if (!buttons.isEmpty()) node.put("buttons", buttons);
            root.put(name, node);
        });
        return Map.copyOf(root);
    }

    private MenuStyle menu(
            Object node, String path, Set<String> knownButtons, Consumer<String> warnings) {
        Map<String, Object> values = map(node, path, warnings);
        values.keySet().stream()
                .filter(key -> !MENU_KEYS.contains(key))
                .forEach(key -> warnings.accept(path + "." + key + " is not a known menu key"));
        return new MenuStyle(
                size(values.get("size"), path + ".size", warnings),
                text(values.get("filler"), path + ".filler", warnings),
                buttons(values.get("buttons"), path + ".buttons", knownButtons, warnings));
    }

    private Map<String, MenuStyle.ButtonStyle> buttons(
            Object node, String path, Set<String> knownButtons, Consumer<String> warnings) {
        if (node == null) return Map.of();
        Map<String, Object> values = map(node, path, warnings);
        LinkedHashMap<String, MenuStyle.ButtonStyle> result = new LinkedHashMap<>();
        values.forEach((button, value) -> {
            if (!knownButtons.contains(button)) {
                warnings.accept(path + "." + button + " is not a button on this menu; known buttons are "
                        + knownButtons);
                return;
            }
            Map<String, Object> fields = map(value, path + "." + button, warnings);
            fields.keySet().stream()
                    .filter(key -> !BUTTON_KEYS.contains(key))
                    .forEach(key -> warnings.accept(
                            path + "." + button + "." + key + " is not a known button key"));
            MenuStyle.ButtonStyle style = new MenuStyle.ButtonStyle(
                    text(fields.get("material"), path + "." + button + ".material", warnings),
                    slot(fields.get("slot"), path + "." + button + ".slot", warnings));
            if (!style.isDefault()) result.put(button, style);
        });
        return Map.copyOf(result);
    }

    private static Map<String, Object> map(Object node, String path, Consumer<String> warnings) {
        if (node == null) return Map.of();
        if (!(node instanceof Map<?, ?> source)) {
            warnings.accept(path + " must be a map; ignoring it");
            return Map.of();
        }
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        source.forEach((key, value) -> values.put(String.valueOf(key), value));
        return values;
    }

    private static Optional<String> text(Object value, String path, Consumer<String> warnings) {
        if (value == null) return Optional.empty();
        if (!(value instanceof String text) || text.isBlank()) {
            warnings.accept(path + " must be text; ignoring it");
            return Optional.empty();
        }
        return Optional.of(text.trim());
    }

    /** An inventory size must be rows of nine, so a bad one is rejected rather than clamped. */
    private static Optional<Integer> size(Object value, String path, Consumer<String> warnings) {
        if (value == null) return Optional.empty();
        if (!(value instanceof Number number) || !MenuStyle.validSize(number.intValue())) {
            warnings.accept(path + " must be a multiple of 9 up to " + MenuStyle.MAX_SIZE
                    + "; using the built-in size");
            return Optional.empty();
        }
        return Optional.of(number.intValue());
    }

    private static Optional<Integer> slot(Object value, String path, Consumer<String> warnings) {
        if (value == null) return Optional.empty();
        if (!(value instanceof Number number) || number.intValue() < 0
                || number.intValue() >= MenuStyle.MAX_SIZE) {
            warnings.accept(path + " must be a slot index from 0 to " + (MenuStyle.MAX_SIZE - 1)
                    + "; using the built-in slot");
            return Optional.empty();
        }
        return Optional.of(number.intValue());
    }
}
