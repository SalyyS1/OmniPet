package io.github.salyvn.omnipet.paper.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Reads an optional {@code appearance:} block for one item.
 *
 * <p>Lenient, following the {@code messages.yml} and {@code gui:} precedent rather than the strict
 * {@code storage:} one: an unknown key warns and a bad value falls back to the built-in appearance,
 * because a typo in a cosmetic setting must never stop players receiving their items.
 */
public final class ItemAppearanceCodec {
    private static final java.util.Set<String> KEYS =
            java.util.Set.of("material", "customModelData", "glint", "itemFlags", "lore");

    public ItemAppearance parse(Object node, String path, Consumer<String> warnings) {
        if (node == null) return ItemAppearance.defaults();
        if (!(node instanceof Map<?, ?> source)) {
            warnings.accept(path + " must be a map; using the built-in appearance");
            return ItemAppearance.defaults();
        }
        Map<String, Object> values = new LinkedHashMap<>();
        source.forEach((key, value) -> values.put(String.valueOf(key), value));
        values.keySet().stream()
                .filter(key -> !KEYS.contains(key))
                .forEach(key -> warnings.accept(path + "." + key + " is not a known appearance key"));

        return new ItemAppearance(
                text(values.get("material"), path + ".material", warnings),
                modelData(values.get("customModelData"), path + ".customModelData", warnings),
                bool(values.get("glint"), path + ".glint", warnings),
                strings(values.get("itemFlags"), path + ".itemFlags", warnings),
                lore(values.get("lore"), path + ".lore", warnings));
    }

    /** Writes the block back, omitting it entirely when nothing was configured. */
    public Map<String, Object> encode(ItemAppearance appearance) {
        LinkedHashMap<String, Object> node = new LinkedHashMap<>();
        appearance.material().ifPresent(value -> node.put("material", value));
        appearance.customModelData().ifPresent(value -> node.put("customModelData", value));
        if (appearance.glint()) node.put("glint", true);
        if (!appearance.itemFlags().isEmpty()) node.put("itemFlags", List.copyOf(appearance.itemFlags()));
        if (!appearance.extraLore().isEmpty()) node.put("lore", List.copyOf(appearance.extraLore()));
        return node;
    }

    private static Optional<String> text(Object value, String path, Consumer<String> warnings) {
        if (value == null) return Optional.empty();
        if (!(value instanceof String text) || text.isBlank()) {
            warnings.accept(path + " must be text; ignoring it");
            return Optional.empty();
        }
        return Optional.of(text.trim());
    }

    private static Optional<Integer> modelData(Object value, String path, Consumer<String> warnings) {
        if (value == null) return Optional.empty();
        if (!(value instanceof Number number) || number.intValue() < 0) {
            warnings.accept(path + " must be a non-negative integer; ignoring it");
            return Optional.empty();
        }
        return Optional.of(number.intValue());
    }

    private static boolean bool(Object value, String path, Consumer<String> warnings) {
        if (value == null) return false;
        if (!(value instanceof Boolean flag)) {
            warnings.accept(path + " must be true or false; using false");
            return false;
        }
        return flag;
    }

    private static List<String> strings(Object value, String path, Consumer<String> warnings) {
        if (value == null) return List.of();
        if (!(value instanceof List<?> list)) {
            warnings.accept(path + " must be a list; ignoring it");
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Object entry : list) {
            if (entry instanceof String text && !text.isBlank()) result.add(text.trim());
            else warnings.accept(path + " has a non-text entry; ignoring it");
        }
        return List.copyOf(result);
    }

    /**
     * Reads lore, dropping the overflow rather than rejecting the config.
     *
     * <p>The bound exists because the durable egg snapshot refuses a serialized stack over 8 KiB, and
     * lore is the one part an operator can grow without limit. Truncating with a warning keeps a
     * verbose config from minting an egg that cannot later be captured.
     */
    private static List<String> lore(Object value, String path, Consumer<String> warnings) {
        List<String> lines = strings(value, path, warnings);
        List<String> bounded = new ArrayList<>();
        for (String line : lines) {
            if (bounded.size() == ItemAppearance.MAX_EXTRA_LORE_LINES) {
                warnings.accept(path + " exceeds " + ItemAppearance.MAX_EXTRA_LORE_LINES
                        + " lines; the rest were dropped");
                break;
            }
            if (line.length() > ItemAppearance.MAX_LORE_LINE_LENGTH) {
                warnings.accept(path + " has a line over " + ItemAppearance.MAX_LORE_LINE_LENGTH
                        + " characters; it was truncated");
                bounded.add(line.substring(0, ItemAppearance.MAX_LORE_LINE_LENGTH));
                continue;
            }
            bounded.add(line);
        }
        return List.copyOf(bounded);
    }
}
