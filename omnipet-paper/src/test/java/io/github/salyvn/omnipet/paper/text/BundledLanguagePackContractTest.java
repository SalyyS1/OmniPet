package io.github.salyvn.omnipet.paper.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The translations shipped inside the jar, checked against the enum they are translating.
 *
 * <p>A pack is allowed to be incomplete — {@link MessageLocalesTest} pins that a missing key falls back to
 * English rather than failing to load, because no translation is ever finished in one go. The failure mode
 * that leaves is silent: a mistyped path, a stale key kept after a rename, or a placeholder dropped in
 * translation all degrade to English or to a literal {@code <detail>} on someone's screen, and nothing says
 * so. Nothing validated the shipped pack at all before this.
 *
 * <p>So: every path in the pack must exist, every placeholder the English text uses must survive, and the
 * pack must actually load through the real loader. Coverage is reported rather than required, so an
 * in-progress translation still ships.
 */
class BundledLanguagePackContractTest {
    private static final Pattern PLACEHOLDER = Pattern.compile("<([a-z]+)>");

    @TempDir
    Path dataRoot;

    @Test
    void everyBundledLocaleIsReadable() throws IOException {
        assertFalse(BundledLanguagePacks.available().isEmpty(), "this build ships no translation");
        for (String locale : BundledLanguagePacks.available()) {
            assertNotNull(BundledLanguagePacks.read(locale),
                    () -> "lang/" + locale + ".yml is listed but not on the classpath");
        }
    }

    /**
     * A path in the pack that no longer exists in the enum.
     *
     * <p>The likeliest cause is a typo, and the second likeliest is a key renamed in Java without the pack
     * being updated. Either way that line is dead: the loader warns and drops it, and every player on that
     * locale silently reads English for it.
     */
    @Test
    void noBundledLocaleCarriesAPathTheEnumDoesNotHave() throws IOException {
        for (String locale : BundledLanguagePacks.available()) {
            List<String> unknown = new ArrayList<>();
            for (String path : paths(BundledLanguagePacks.read(locale))) {
                if (MessageKey.byPath(path) == null) unknown.add(path);
            }
            assertTrue(unknown.isEmpty(),
                    () -> "lang/" + locale + ".yml has paths the enum does not: " + unknown);
        }
    }

    /**
     * A placeholder dropped in translation.
     *
     * <p>Placeholders are filled in literally, so a translated line missing {@code <amount>} does not fail —
     * it just quietly stops showing the number, and the player is told less than an English-speaking one.
     * Extra placeholders matter too: an unfilled {@code <total>} reaches the screen as that literal text.
     */
    @Test
    void everyTranslatedLineKeepsThePlaceholdersItsEnglishTextUses() throws IOException {
        for (String locale : BundledLanguagePacks.available()) {
            List<String> problems = new ArrayList<>();
            entries(BundledLanguagePacks.read(locale)).forEach((path, translated) -> {
                MessageKey key = MessageKey.byPath(path);
                if (key == null) return;
                var expected = placeholders(key.defaultValue());
                var actual = placeholders(translated);
                if (!actual.containsAll(expected)) {
                    problems.add(path + " dropped " + minus(expected, actual));
                }
                if (!expected.containsAll(actual)) {
                    problems.add(path + " invented " + minus(actual, expected));
                }
            });
            assertTrue(problems.isEmpty(), () -> "lang/" + locale + ".yml: " + problems);
        }
    }

    /** The pack has to survive the real loader, not just a YAML parse. */
    @Test
    void everyBundledLocaleLoadsThroughTheRealLoaderWithoutWarnings() throws IOException {
        for (String locale : BundledLanguagePacks.available()) {
            List<String> warnings = new ArrayList<>();
            Path file = MessageLocales.packFile(dataRoot, locale);
            Files.createDirectories(file.getParent());
            Files.writeString(file, BundledLanguagePacks.read(locale), StandardCharsets.UTF_8);

            MessageCatalog catalog = MessageLocales.load(dataRoot, locale, warnings::add);

            assertTrue(warnings.isEmpty(), () -> "lang/" + locale + ".yml: " + warnings);
            // Every key resolves to something, whether translated or fallen back to English.
            for (MessageKey key : MessageKey.values()) {
                assertFalse(catalog.raw(key).isBlank(),
                        () -> locale + " resolved " + key.path() + " to nothing");
            }
        }
    }

    /**
     * Reports how complete each pack is, without demanding completeness.
     *
     * <p>Named as a test so the number shows up in the report: a translation that quietly drifts from 100%
     * to 60% across a few features is worth seeing, and worth not blocking a build over.
     */
    @Test
    void everyBundledLocaleTranslatesMostOfTheEnum() throws IOException {
        for (String locale : BundledLanguagePacks.available()) {
            int translated = (int) paths(BundledLanguagePacks.read(locale)).stream()
                    .filter(path -> MessageKey.byPath(path) != null)
                    .count();
            int total = MessageKey.values().length;

            assertTrue(translated * 100 / total >= 80,
                    () -> "lang/" + locale + ".yml covers only " + translated + "/" + total
                            + " keys; new player-facing text needs translating");
        }
    }

    private static List<String> minus(List<String> from, List<String> other) {
        List<String> result = new ArrayList<>(from);
        result.removeAll(other);
        return result;
    }

    /** Placeholder names in one line, excluding MiniMessage colour tags, which share the shape. */
    private static List<String> placeholders(String value) {
        List<String> found = new ArrayList<>();
        Matcher matcher = PLACEHOLDER.matcher(value);
        while (matcher.find()) {
            String name = matcher.group(1);
            if (MiniMessageColorNames.contains(name)) continue;
            if (!found.contains(name)) found.add(name);
        }
        return found;
    }

    private static List<String> paths(String document) {
        return new ArrayList<>(entries(document).keySet());
    }

    /**
     * Dotted path to raw value for one pack.
     *
     * <p>Parsed by indentation rather than through a YAML library so the test reports the pack's own paths
     * verbatim, which is what an operator has to go and fix.
     */
    private static java.util.LinkedHashMap<String, String> entries(String document) {
        java.util.LinkedHashMap<String, String> result = new java.util.LinkedHashMap<>();
        String[] prefix = new String[8];
        for (String raw : document.split("\r?\n")) {
            if (raw.isBlank() || raw.stripLeading().startsWith("#")) continue;
            int indent = raw.length() - raw.stripLeading().length();
            int depth = indent / 2;
            if (depth >= prefix.length) continue;
            String line = raw.strip();
            int colon = line.indexOf(':');
            if (colon < 0) continue;
            String name = line.substring(0, colon).strip();
            String value = line.substring(colon + 1).strip();
            if (value.isEmpty()) {
                prefix[depth] = name;
                for (int deeper = depth + 1; deeper < prefix.length; deeper++) prefix[deeper] = null;
                continue;
            }
            StringBuilder path = new StringBuilder();
            for (int level = 0; level < depth; level++) {
                if (prefix[level] == null) continue;
                path.append(prefix[level]).append('.');
            }
            result.put(path + name, unquote(value));
        }
        return result;
    }

    private static String unquote(String value) {
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    /** The MiniMessage colour names that share {@code <name>} with a placeholder. */
    private static final class MiniMessageColorNames {
        private static final java.util.Set<String> NAMES = java.util.Set.of(
                "black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple", "gold",
                "gray", "dark_gray", "blue", "green", "aqua", "red", "light_purple", "yellow", "white",
                "bold", "italic", "underlined", "strikethrough", "obfuscated", "reset", "newline", "br");

        static boolean contains(String name) {
            return NAMES.contains(name);
        }
    }

    /** Guards the parser itself: a helper that silently found nothing would make every check vacuous. */
    @Test
    void theParserFindsTheKeysItIsMeantTo() {
        var parsed = entries("""
                hub:
                  vault: "Kho"
                gui:
                  title:
                    hatch: "Ap trung"
                """);

        assertEquals(2, parsed.size());
        assertEquals("Kho", parsed.get("hub.vault"));
        assertEquals("Ap trung", parsed.get("gui.title.hatch"));
    }
}
