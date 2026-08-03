package io.github.salyvn.omnipet.paper.text;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Writes the operator-owned {@code messages.yml} from the {@link MessageKey} defaults.
 *
 * <p>The file is generated from the enum rather than shipped as a static resource, so there is no
 * second copy of the text that could drift from the code. Deleting the file regenerates it on the
 * next start; removing a single key restores that key's default.
 */
public final class MessageCatalogFile {
    private static final String HEADER = """
            # OmniPet player-facing text.
            #
            # Values use MiniMessage: https://docs.advntr.dev/minimessage/format.html
            # Remove a key to restore its built-in default. An unknown key is ignored with a warning,
            # and a value with a broken tag is shown literally - neither disables the plugin.
            # Placeholders such as <pet> and <amount> are filled in literally and never re-parsed,
            # so a pet named "<red>" cannot colour anyone else's screen.
            #
            # Operator and console output (transaction pages, cursors, reconcile reasons, delivery
            # receipts) is deliberately absent: it is an audit trail and stays in the plugin.
            #
            # Reload with /pet admin reload. A broken edit keeps the previously loaded text.
            """;

    private MessageCatalogFile() {}

    /** Creates {@code messages.yml} with the built-in defaults when it does not already exist. */
    public static void writeDefaultsIfAbsent(Path file) throws IOException {
        Objects.requireNonNull(file, "messages file");
        if (Files.isSymbolicLink(file)) throw new IOException("OmniPet messages.yml cannot be a symbolic link");
        if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) return;
        Files.createDirectories(file.getParent());
        Files.writeString(file, defaultDocument(), StandardCharsets.UTF_8);
    }

    /** The full default document, header included. Groups are sorted by path. */
    public static String defaultDocument() {
        StringBuilder document = new StringBuilder(HEADER);
        String group = null;
        // Sort by path so the emitted YAML is order-independent of the enum's declaration order.
        // An out-of-order enum constant would otherwise emit a second mapping for its group and
        // produce a duplicate-key YAML document that SnakeYAML rejects.
        java.util.List<MessageKey> keys = java.util.Arrays.stream(MessageKey.values())
                .sorted(java.util.Comparator.comparing(MessageKey::path))
                .toList();
        for (MessageKey key : keys) {
            String path = key.path();
            int separator = path.indexOf('.');
            String prefix = separator < 0 ? "" : path.substring(0, separator);
            String leaf = separator < 0 ? path : path.substring(separator + 1);
            if (!prefix.equals(group)) {
                group = prefix;
                document.append('\n').append(prefix).append(":\n");
            }
            document.append("  ").append(leaf).append(": ").append(quote(key.defaultValue())).append('\n');
        }
        return document.toString();
    }

    /** The defaults as a nested map, used by tests to compare a parsed file against the enum. */
    public static Map<String, Object> defaultTree() {
        Map<String, Object> root = new LinkedHashMap<>();
        for (MessageKey key : MessageKey.values()) {
            String path = key.path();
            int separator = path.indexOf('.');
            if (separator < 0) {
                root.put(path, key.defaultValue());
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> group = (Map<String, Object>) root.computeIfAbsent(
                    path.substring(0, separator), ignored -> new LinkedHashMap<String, Object>());
            group.put(path.substring(separator + 1), key.defaultValue());
        }
        return root;
    }

    /**
     * Double-quotes a value so MiniMessage tags, colons, and leading characters survive YAML parsing
     * unchanged. Only backslash and the quote itself need escaping inside a YAML double-quoted
     * scalar; message text carries no control characters.
     */
    private static String quote(String value) {
        return '"' + value.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }
}
