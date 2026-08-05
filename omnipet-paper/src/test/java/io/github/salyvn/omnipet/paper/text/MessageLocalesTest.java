package io.github.salyvn.omnipet.paper.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Which text file wins, and what happens when one of them is incomplete.
 *
 * <p>The layering is the whole point. {@code messages.yml} is generated on first start and many servers
 * already have one edited in place, so a language pack has to sit *underneath* it rather than replace it.
 * And a translation is never finished all at once, so a missing key has to fall through instead of
 * failing to load.
 */
class MessageLocalesTest {
    @TempDir
    Path dataRoot;

    @Test
    void theDefaultLocaleIsTheBuiltInEnglishText() throws IOException {
        MessageCatalog catalog = MessageLocales.load(dataRoot, "en", warning -> {});

        assertEquals(MessageKey.HUB_VAULT.defaultValue(), catalog.raw(MessageKey.HUB_VAULT));
    }

    @Test
    void aBlankOrAbsentLocaleIsTreatedAsTheDefault() throws IOException {
        assertEquals(MessageKey.HUB_VAULT.defaultValue(),
                MessageLocales.load(dataRoot, "  ", warning -> {}).raw(MessageKey.HUB_VAULT));
        assertEquals(MessageKey.HUB_VAULT.defaultValue(),
                MessageLocales.load(dataRoot, null, warning -> {}).raw(MessageKey.HUB_VAULT));
    }

    @Test
    void aLanguagePackSuppliesTextTheEnumWouldOtherwiseGive() throws IOException {
        writePack("vi", "hub:\n  vault: \"Kho thu cung\"\n");

        MessageCatalog catalog = MessageLocales.load(dataRoot, "vi", warning -> {});

        assertEquals("Kho thu cung", catalog.raw(MessageKey.HUB_VAULT));
    }

    @Test
    void aKeyMissingFromThePackFallsBackToEnglishRatherThanFailing() throws IOException {
        // A translation is never finished in one go. Rejecting a partial pack would mean nobody could
        // ship one until every key was done.
        writePack("vi", "hub:\n  vault: \"Kho thu cung\"\n");

        MessageCatalog catalog = MessageLocales.load(dataRoot, "vi", warning -> {});

        assertEquals("Kho thu cung", catalog.raw(MessageKey.HUB_VAULT));
        assertEquals(MessageKey.HUB_HATCH.defaultValue(), catalog.raw(MessageKey.HUB_HATCH),
                "an untranslated key must still render");
    }

    @Test
    void theOperatorsOwnFileOutranksTheLanguagePack() throws IOException {
        // messages.yml is where an operator's edits live. A pack must never silently overrule them.
        writePack("vi", "hub:\n  vault: \"Kho thu cung\"\n");
        Files.writeString(dataRoot.resolve("messages.yml"),
                "hub:\n  vault: \"My own wording\"\n", StandardCharsets.UTF_8);

        MessageCatalog catalog = MessageLocales.load(dataRoot, "vi", warning -> {});

        assertEquals("My own wording", catalog.raw(MessageKey.HUB_VAULT));
    }

    @Test
    void aMissingPackWarnsAndUsesEnglishInsteadOfRefusingToStart() throws IOException {
        List<String> warnings = new ArrayList<>();

        MessageCatalog catalog = MessageLocales.load(dataRoot, "de", warnings::add);

        assertEquals(MessageKey.HUB_VAULT.defaultValue(), catalog.raw(MessageKey.HUB_VAULT));
        assertTrue(warnings.stream().anyMatch(w -> w.contains("lang/de.yml")), warnings.toString());
    }

    @Test
    void localeTagsAreNormalisedSoOneFileServesEverySpelling() {
        Path expected = MessageLocales.packFile(dataRoot, "vi");

        assertEquals(expected, MessageLocales.packFile(dataRoot, "VI"));
        assertEquals(expected, MessageLocales.packFile(dataRoot, " vi "));
        assertEquals(MessageLocales.packFile(dataRoot, "vi-vn"), MessageLocales.packFile(dataRoot, "vi_VN"));
    }

    @Test
    void bundledPacksAreWrittenOnceAndNeverOverwritten() throws IOException {
        List<String> notes = new ArrayList<>();
        MessageLocales.writeBundledPacks(dataRoot, notes::add);

        Path vietnamese = MessageLocales.packFile(dataRoot, "vi");
        assertTrue(Files.isRegularFile(vietnamese), "the bundled pack must reach disk to be editable");
        assertTrue(notes.stream().anyMatch(n -> n.contains("vi")), notes.toString());

        // An operator's correction has to survive the next start, like every other generated file.
        Files.writeString(vietnamese, "hub:\n  vault: \"Edited by hand\"\n", StandardCharsets.UTF_8);
        MessageLocales.writeBundledPacks(dataRoot, warning -> {});

        assertEquals("hub:\n  vault: \"Edited by hand\"\n",
                Files.readString(vietnamese, StandardCharsets.UTF_8));
    }

    @Test
    void theBundledVietnamesePackCoversEveryKeyAndKeepsEveryPlaceholder() throws IOException {
        String document = BundledLanguagePacks.read("vi");
        assertFalse(document == null, "the advertised locale must actually ship");
        List<String> warnings = new ArrayList<>();
        MessageCatalog pack = MessageCatalog.parse(document, warnings::add);

        assertTrue(warnings.isEmpty(), "a shipped pack must not name keys the plugin does not have: " + warnings);

        List<String> untranslated = new ArrayList<>();
        List<String> brokenPlaceholders = new ArrayList<>();
        for (MessageKey key : MessageKey.values()) {
            if (!pack.overridden(key)) {
                untranslated.add(key.path());
                continue;
            }
            // A dropped placeholder is worse than an untranslated line: the value silently vanishes from
            // the rendered message instead of merely reading as English.
            for (String placeholder : placeholders(key.defaultValue())) {
                if (!pack.raw(key).contains(placeholder)) {
                    brokenPlaceholders.add(key.path() + " lost " + placeholder);
                }
            }
        }

        assertTrue(brokenPlaceholders.isEmpty(), brokenPlaceholders.toString());
        assertTrue(untranslated.isEmpty(), "keys with no Vietnamese text: " + untranslated);
    }

    /** The {@code <name>} tags a value fills in, ignoring MiniMessage colour tags. */
    private static List<String> placeholders(String value) {
        List<String> found = new ArrayList<>();
        java.util.regex.Matcher matcher =
                java.util.regex.Pattern.compile("<([a-z][a-z_]*)>").matcher(value);
        while (matcher.find()) {
            String name = matcher.group(1);
            if (PLACEHOLDER_NAMES.contains(name)) found.add(matcher.group());
        }
        return found;
    }

    /** Every placeholder the renderers actually supply, so a colour tag is not mistaken for one. */
    private static final java.util.Set<String> PLACEHOLDER_NAMES = java.util.Set.of(
            "pet", "amount", "total", "detail", "status", "level", "exp", "remaining", "page", "pages",
            "cost", "balance", "provider", "reason", "usage", "description", "field", "format", "example",
            "stat", "modifier");

    private void writePack(String locale, String yaml) throws IOException {
        Path file = MessageLocales.packFile(dataRoot, locale);
        Files.createDirectories(file.getParent());
        Files.writeString(file, yaml, StandardCharsets.UTF_8);
    }
}
