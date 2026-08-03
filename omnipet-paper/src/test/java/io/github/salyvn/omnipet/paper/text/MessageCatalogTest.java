package io.github.salyvn.omnipet.paper.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

class MessageCatalogTest {
    @Test
    void aFileValueWinsOverTheBuiltInDefault() {
        MessageCatalog catalog = MessageCatalog.parse("""
                vault:
                  refreshed: "<green>Refreshed</green>"
                """, warning -> {});

        assertTrue(catalog.overridden(MessageKey.VAULT_REFRESHED));
        assertEquals("Refreshed", plain(catalog.line(MessageKey.VAULT_REFRESHED)));
    }

    @Test
    void anOmittedKeyFallsBackToItsDefaultInsteadOfRenderingBlank() {
        MessageCatalog catalog = MessageCatalog.parse("vault:\n  refreshed: \"x\"\n", warning -> {});

        assertFalse(catalog.overridden(MessageKey.SKILL_SUCCEEDED));
        assertEquals("OmniPet: Pet skill cast succeeded.", plain(catalog.line(MessageKey.SKILL_SUCCEEDED)));
    }

    @Test
    void anUnknownKeyWarnsAndIsIgnoredWithoutThrowing() {
        List<String> warnings = new ArrayList<>();

        MessageCatalog catalog = MessageCatalog.parse("""
                vault:
                  refreshed: "kept"
                  not-a-real-key: "dropped"
                """, warnings::add);

        assertEquals(1, warnings.size());
        assertTrue(warnings.getFirst().contains("vault.not-a-real-key"));
        assertEquals("kept", plain(catalog.line(MessageKey.VAULT_REFRESHED)));
    }

    @Test
    void aNonTextValueWarnsAndKeepsTheDefault() {
        List<String> warnings = new ArrayList<>();

        MessageCatalog catalog = MessageCatalog.parse("vault:\n  refreshed: 42\n", warnings::add);

        assertEquals(1, warnings.size());
        assertFalse(catalog.overridden(MessageKey.VAULT_REFRESHED));
        assertEquals("OmniPet vault changed; refreshed the page.", plain(catalog.line(MessageKey.VAULT_REFRESHED)));
    }

    @Test
    void aMalformedTagDegradesToLiteralTextRatherThanThrowingIntoAClickHandler() {
        MessageCatalog catalog = MessageCatalog.parse(
                "vault:\n  refreshed: \"<click:not_a_real_action:x>broken</click>\"\n", warning -> {});

        Component rendered = catalog.line(MessageKey.VAULT_REFRESHED);

        assertNotNull(rendered);
        assertTrue(plain(rendered).contains("broken"));
    }

    @Test
    void aPlayerSuppliedValueContainingTagsRendersLiterally() {
        MessageCatalog catalog = MessageCatalog.defaults();

        String rendered = plain(catalog.line(
                MessageKey.VAULT_MUTATION_REJECTED, Messages.of("status", "<red>evil</red><click:run_command:/op me>")));

        assertTrue(rendered.contains("<red>evil</red><click:run_command:/op me>"));
    }

    @Test
    void anArgumentFreeLineIsParsedOnceAndCached() {
        MessageCatalog catalog = MessageCatalog.defaults();

        assertSame(catalog.line(MessageKey.SKILL_SUCCEEDED), catalog.line(MessageKey.SKILL_SUCCEEDED));
    }

    @Test
    void aListValueBecomesOneLinePerEntry() {
        MessageCatalog catalog = MessageCatalog.parse("""
                vault:
                  refreshed:
                    - "first"
                    - "second"
                """, warning -> {});

        List<Component> lore = catalog.lore(MessageKey.VAULT_REFRESHED);

        assertEquals(List.of("first", "second"), lore.stream().map(MessageCatalogTest::plain).toList());
    }

    @Test
    void aSingleLineValueIsAcceptedWhereLoreIsRequested() {
        assertEquals(1, MessageCatalog.defaults().lore(MessageKey.SKILL_SUCCEEDED).size());
    }

    @Test
    void anAbsentFileYieldsDefaultsInsteadOfFailing(@TempDir Path directory) throws IOException {
        MessageCatalog catalog = MessageCatalog.load(directory.resolve("messages.yml"), warning -> {});

        assertEquals("OmniPet: Pet skill cast succeeded.", plain(catalog.line(MessageKey.SKILL_SUCCEEDED)));
    }

    @Test
    void aGeneratedFileRoundTripsEveryDefaultUnchanged(@TempDir Path directory) throws IOException {
        Path file = directory.resolve("messages.yml");
        List<String> warnings = new ArrayList<>();
        MessageCatalogFile.writeDefaultsIfAbsent(file);

        MessageCatalog catalog = MessageCatalog.load(file, warnings::add);

        assertTrue(warnings.isEmpty(), () -> "unexpected warnings: " + warnings);
        for (MessageKey key : MessageKey.values()) {
            assertTrue(catalog.overridden(key), () -> "generated file is missing " + key.path());
            assertEquals(key.defaultValue(), catalog.raw(key), () -> "value drifted for " + key.path());
        }
    }

    @Test
    void writingDefaultsNeverOverwritesAnOperatorEdit(@TempDir Path directory) throws IOException {
        Path file = directory.resolve("messages.yml");
        Files.writeString(file, "vault:\n  refreshed: \"mine\"\n", StandardCharsets.UTF_8);

        MessageCatalogFile.writeDefaultsIfAbsent(file);

        assertEquals("vault:\n  refreshed: \"mine\"\n", Files.readString(file, StandardCharsets.UTF_8));
    }

    @Test
    void theAccessorFailsLoudlyWhenNothingIsBound() {
        Messages.unbind();

        assertThrows(IllegalStateException.class, Messages::catalog);
    }

    @Test
    void bindingSwapsTheActiveCatalog() {
        MessageCatalog replacement = MessageCatalog.parse(
                "vault:\n  refreshed: \"swapped\"\n", warning -> {});
        try {
            Messages.bind(replacement);

            assertEquals("swapped", plain(Messages.line(MessageKey.VAULT_REFRESHED)));
        } finally {
            Messages.unbind();
        }
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }
}
