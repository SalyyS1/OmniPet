package io.github.salyvn.omnipet.paper.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

class MessageKeyDefaultsTest {
    private static final Pattern DOTTED_PATH = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*(?:\\.[a-z0-9]+(?:-[a-z0-9]+)*)*");

    @Test
    void everyKeyHasANonBlankDefault() {
        for (MessageKey key : MessageKey.values()) {
            assertFalse(key.defaultValue().isBlank(), () -> key.name() + " has a blank default");
        }
    }

    @Test
    void everyPathIsUniqueAndLowerKebabDotted() {
        Set<String> seen = new HashSet<>();
        for (MessageKey key : MessageKey.values()) {
            assertTrue(seen.add(key.path()), () -> "duplicate path: " + key.path());
            assertTrue(DOTTED_PATH.matcher(key.path()).matches(), () -> "malformed path: " + key.path());
        }
    }

    @Test
    void everyPathResolvesBackToItsKey() {
        for (MessageKey key : MessageKey.values()) {
            assertSame(key, MessageKey.byPath(key.path()));
        }
        assertNull(MessageKey.byPath("vault.does-not-exist"));
    }

    @Test
    void noKeyReachesIntoTheOperatorAuditNamespace() {
        for (MessageKey key : MessageKey.values()) {
            assertFalse(key.path().startsWith("admin."),
                    () -> key.path() + " is operator output and must stay hardcoded");
        }
    }

    @Test
    void everyPlaceholderUsedInADefaultIsOneOfTheAgreedNames() {
        Set<String> allowed = Set.of(
                "pet", "player", "amount", "total", "page", "pages", "status", "reason",
                "detail", "provider", "cost", "balance", "level", "exp", "remaining",
                "usage", "description", "field", "format", "example", "stat", "modifier");
        Pattern placeholder = Pattern.compile("<([a-z]+)>");

        for (MessageKey key : MessageKey.values()) {
            var matcher = placeholder.matcher(key.defaultValue());
            while (matcher.find()) {
                String name = matcher.group(1);
                if (MiniMessageTags.COLORS.contains(name)) continue;
                assertTrue(allowed.contains(name),
                        () -> key.path() + " uses an unapproved placeholder: <" + name + ">");
            }
        }
    }

    @Test
    void theGeneratedFileCoversExactlyTheEnum() {
        var tree = MessageCatalogFile.defaultTree();
        int leaves = tree.values().stream()
                .mapToInt(value -> value instanceof java.util.Map<?, ?> group ? group.size() : 1)
                .sum();

        assertEquals(MessageKey.values().length, leaves);
    }

    /** MiniMessage colour tags share the {@code <name>} shape with placeholders. */
    private static final class MiniMessageTags {
        private static final Set<String> COLORS = Set.of(
                "black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple", "gold",
                "gray", "dark_gray", "blue", "green", "aqua", "red", "light_purple", "yellow", "white",
                "bold", "italic", "underlined", "strikethrough", "obfuscated", "reset", "newline");

        private MiniMessageTags() {}
    }
}
