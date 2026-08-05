package io.github.salyvn.omnipet.paper.text;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import io.github.salyvn.omnipet.core.persistence.YamlDocuments;

/**
 * Immutable player-facing text catalog backed by {@code messages.yml}.
 *
 * <p>Deliberately lenient, unlike {@code OmniPetConfigLoader}: an unknown key warns and is ignored,
 * a missing key falls back to its {@link MessageKey} default, and a malformed MiniMessage value
 * degrades to literal text. A typo in a lore line must never disable pets.
 *
 * <p>Instances are immutable after construction. Reload builds a replacement and swaps the
 * reference, so a bad edit leaves the previous catalog live.
 */
public final class MessageCatalog {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final Map<MessageKey, String> values;
    /** Parsed components for argument-free lookups, so static lines are not re-parsed per render. */
    private final Map<MessageKey, Component> cache = new ConcurrentHashMap<>();

    private MessageCatalog(Map<MessageKey, String> values) {
        this.values = Map.copyOf(values);
    }

    /** A catalog using only the built-in defaults. */
    public static MessageCatalog defaults() {
        return new MessageCatalog(Map.of());
    }

    /**
     * Loads {@code messages.yml}, warning about unknown keys and non-text values instead of failing.
     *
     * @param warnings receives one human-readable line per ignored entry
     */
    public static MessageCatalog load(Path file, Consumer<String> warnings) throws IOException {
        Objects.requireNonNull(file, "messages file");
        Objects.requireNonNull(warnings, "warning sink");
        if (Files.isSymbolicLink(file)) throw new IOException("OmniPet messages.yml cannot be a symbolic link");
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) return defaults();
        return parse(Files.readString(file, StandardCharsets.UTF_8), warnings);
    }

    /** Parses a YAML document into a catalog. Package-visible entry point for tests. */
    public static MessageCatalog parse(String yaml, Consumer<String> warnings) {
        Objects.requireNonNull(warnings, "warning sink");
        Map<String, Object> flattened = new java.util.LinkedHashMap<>();
        flatten("", YamlDocuments.readMap(yaml), flattened);
        Map<MessageKey, String> resolved = new EnumMap<>(MessageKey.class);
        flattened.forEach((path, value) -> {
            MessageKey key = MessageKey.byPath(path);
            if (key == null) {
                warnings.accept("unknown messages.yml key ignored: " + path);
                return;
            }
            if (value instanceof String text) {
                resolved.put(key, text);
            } else if (value instanceof List<?> lines) {
                resolved.put(key, joinLines(path, lines, warnings));
            } else {
                warnings.accept("messages.yml key must be text or a text list, using the default: " + path);
            }
        });
        return new MessageCatalog(resolved);
    }

    /** The raw MiniMessage string for a key: the file value when present, otherwise the default. */
    public String raw(MessageKey key) {
        Objects.requireNonNull(key, "message key");
        return values.getOrDefault(key, key.defaultValue());
    }

    /**
     * This catalog layered over {@code fallback}: a key absent here falls through to it.
     *
     * <p>What makes a partial translation usable. A pack covering half the keys shows translated text
     * where it has it and the underlying layer elsewhere, rather than being rejected for incompleteness.
     */
    public MessageCatalog withFallback(MessageCatalog fallback) {
        Objects.requireNonNull(fallback, "fallback catalog");
        if (fallback.values.isEmpty()) return this;
        Map<MessageKey, String> merged = new EnumMap<>(MessageKey.class);
        merged.putAll(fallback.values);
        merged.putAll(values);
        return new MessageCatalog(merged);
    }

    /** True when the file supplied this key, rather than the built-in default being used. */
    public boolean overridden(MessageKey key) {
        return values.containsKey(Objects.requireNonNull(key, "message key"));
    }

    /** Renders a single line. Argument-free lookups are cached. */
    public Component line(MessageKey key, TagResolver... resolvers) {
        Objects.requireNonNull(key, "message key");
        if (resolvers == null || resolvers.length == 0) {
            return cache.computeIfAbsent(key, cached -> render(cached, raw(cached)));
        }
        return render(key, raw(key), resolvers);
    }

    /** Renders a multi-line block, splitting the resolved value on newlines. */
    public List<Component> lore(MessageKey key, TagResolver... resolvers) {
        Objects.requireNonNull(key, "message key");
        String raw = raw(key);
        if (raw.indexOf('\n') < 0) return List.of(line(key, resolvers));
        List<Component> lines = new ArrayList<>();
        for (String part : raw.split("\n", -1)) lines.add(render(key, part, resolvers));
        return List.copyOf(lines);
    }

    private Component render(MessageKey key, String raw, TagResolver... resolvers) {
        try {
            return resolvers == null || resolvers.length == 0
                    ? MINI_MESSAGE.deserialize(raw)
                    : MINI_MESSAGE.deserialize(raw, resolvers);
        } catch (RuntimeException malformed) {
            // Deliberate, and deliberately silent. MiniMessage in its default lenient mode does not
            // throw on a malformed value: an unknown or unclosed tag renders as literal text, and even
            // a resolver that throws is swallowed internally (verified empirically against the bundled
            // Adventure). This catch is a belt-and-braces guard for a future strict-mode build, where
            // a ParsingException would otherwise escape into a click handler. There is nothing to log
            // because there is no reachable failure to report, and a warning here would sit in a render
            // path where it could fire per frame.
            return Component.text(raw);
        }
    }

    private static String joinLines(String path, List<?> lines, Consumer<String> warnings) {
        StringBuilder joined = new StringBuilder();
        for (Object line : lines) {
            if (!(line instanceof String text)) {
                warnings.accept("messages.yml list entry must be text, skipping one line of: " + path);
                continue;
            }
            if (!joined.isEmpty()) joined.append('\n');
            joined.append(text);
        }
        return joined.toString();
    }

    private static void flatten(String prefix, Map<String, Object> source, Map<String, Object> target) {
        source.forEach((key, value) -> {
            String path = prefix.isEmpty() ? key : prefix + "." + key;
            if (value instanceof Map<?, ?> nested) {
                Map<String, Object> typed = new java.util.LinkedHashMap<>();
                nested.forEach((nestedKey, nestedValue) -> typed.put(String.valueOf(nestedKey), nestedValue));
                flatten(path, typed, target);
            } else {
                target.put(path, value);
            }
        });
    }
}
