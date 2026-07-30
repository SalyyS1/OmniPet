package io.github.salyvn.omnipet.core.migration.legacy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.salyvn.omnipet.core.persistence.AtomicFileStore;
import io.github.salyvn.omnipet.core.persistence.YamlDocuments;

class LegacyEggDefinitionsMigratorTest {
    @TempDir
    Path temporary;

    private final LegacyEggDefinitionsMigrator migrator = new LegacyEggDefinitionsMigrator();

    @Test
    void preservesRawDefinitionsAndProducesDeterministicJournal() {
        String source = """
                rare:
                  duration: 3h
                  name: <blue>Rare Egg
                  rarity: 2
                  pets: [example_pet, nahara, example_pet]
                  vendor:
                    model: egg_rare
                    flags: [glow, tradeable]
                common:
                  pets: [example_pet]
                  rarity: 1
                  duration: 1h
                  name: <white>Common Egg
                """;
        String reordered = """
                common: {name: <white>Common Egg, duration: 1h, rarity: 1, pets: [example_pet]}
                rare:
                  vendor: {flags: [glow, tradeable], model: egg_rare}
                  pets: [example_pet, nahara, example_pet]
                  name: <blue>Rare Egg
                  rarity: 2
                  duration: 3h
                """;

        LegacyEggDefinitionsMigrationResult first = migrator.migrate(source);
        LegacyEggDefinitionsMigrationResult second = migrator.migrate(reordered);

        assertEquals(first.sourceSemanticSha256(), second.sourceSemanticSha256());
        assertEquals(first.journalPayload(), second.journalPayload());
        assertEquals(List.of("example_pet", "nahara", "example_pet"), first.eggDefinitions().get("rare").get("pets"));
        assertEquals(
                Map.of("flags", List.of("glow", "tradeable"), "model", "egg_rare"),
                first.eggDefinitions().get("rare").get("vendor"));

        Map<String, Object> journal = YamlDocuments.readMap(first.journalPayload());
        assertEquals(LegacyEggDefinitionsMigrator.JOURNAL_SCHEMA_VERSION, journal.get("schemaVersion"));
        assertEquals(LegacyEggDefinitionsMigrator.JOURNAL_KIND, journal.get("kind"));
        assertEquals(first.sourceSemanticSha256(), journal.get("sourceSemanticSha256"));
        assertTrue(journal.get("eggDefinitions") instanceof Map<?, ?>);
        assertFalse(first.journalWritten());
    }

    @Test
    void returnsDeeplyImmutableRawDefinitions() {
        LegacyEggDefinitionsMigrationResult result = migrator.migrate("""
                common:
                  pets: [example_pet]
                  extension: {enabled: true}
                """);

        assertThrows(UnsupportedOperationException.class, () -> result.eggDefinitions().put("rare", Map.of()));
        assertThrows(
                UnsupportedOperationException.class,
                () -> result.eggDefinitions().get("common").put("rarity", 1));
        @SuppressWarnings("unchecked")
        List<Object> pets = (List<Object>) result.eggDefinitions().get("common").get("pets");
        assertThrows(UnsupportedOperationException.class, () -> pets.add("nahara"));
    }

    @Test
    void rejectsMalformedDuplicateAndNonMapDefinitions() {
        assertThrows(IllegalArgumentException.class, () -> migrator.migrate("../egg: {}"));
        assertThrows(IllegalArgumentException.class, () -> migrator.migrate("Common: {}\ncommon: {}"));
        assertThrows(IllegalArgumentException.class, () -> migrator.migrate("common: not-a-map"));
        assertThrows(RuntimeException.class, () -> migrator.migrate("common: {}\ncommon: {}"));
    }

    @Test
    void validatesPetReferencesWithoutDeduplicatingThem() {
        assertThrows(IllegalArgumentException.class, () -> migrator.migrate("common: {pets: not-a-list}"));
        assertThrows(IllegalArgumentException.class, () -> migrator.migrate("common: {pets: [../pet]}"));
        assertThrows(IllegalArgumentException.class, () -> migrator.migrate("common: {pets: [1]}"));

        LegacyEggDefinitionsMigrationResult result = migrator.migrate("common: {pets: [Pet, pet]}\n");
        assertEquals(List.of("Pet", "pet"), result.eggDefinitions().get("common").get("pets"));
    }

    @Test
    void writesJournalAtomicallyAndSkipsIdenticalRepeat() throws Exception {
        Path source = temporary.resolve("eggs.yml");
        Path journal = temporary.resolve("migration").resolve("legacy-eggs.yml");
        String firstSource = "common: {name: Common, duration: 1h, rarity: 1, pets: [example_pet]}\n";
        Files.writeString(source, firstSource);

        LegacyEggDefinitionsMigrationResult first = migrator.migrate(source, journal);
        String firstJournal = Files.readString(journal);
        assertTrue(first.journalWritten());
        assertEquals(firstSource, Files.readString(source));
        assertFalse(Files.exists(AtomicFileStore.backupPath(journal)));

        LegacyEggDefinitionsMigrationResult repeated = migrator.migrate(source, journal);
        assertFalse(repeated.journalWritten());
        assertEquals(first.journalPayload(), repeated.journalPayload());
        assertFalse(Files.exists(AtomicFileStore.backupPath(journal)));

        String changedSource = "common: {name: Common, duration: 2h, rarity: 1, pets: [example_pet]}\n";
        Files.writeString(source, changedSource);
        LegacyEggDefinitionsMigrationResult changed = migrator.migrate(source, journal);

        assertTrue(changed.journalWritten());
        assertEquals(changedSource, Files.readString(source));
        assertEquals(firstJournal, Files.readString(AtomicFileStore.backupPath(journal)));
        assertEquals(changed.journalPayload(), Files.readString(journal));
    }

    @Test
    void neverAllowsJournalToReplaceLegacySource() throws Exception {
        Path source = temporary.resolve("eggs.yml");
        String yaml = "common: {pets: [example_pet]}\n";
        Files.writeString(source, yaml);

        assertThrows(IllegalArgumentException.class, () -> migrator.migrate(source, source));
        assertEquals(yaml, Files.readString(source));
    }
}
