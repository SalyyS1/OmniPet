package io.github.salyvn.omnipet.core.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.salyvn.omnipet.core.domain.PetDefinition;
import io.github.salyvn.omnipet.core.domain.PetDefinitionEnvelope;

class YamlPetDefinitionRepositoryTest {
    private static final String CURRENT_DEFINITION = """
            schemaVersion: 2
            definitionId: %s
            revision: 0
            classification: { tier: D }
            icon: { head: { source: TEXTURE_URL, value: value } }
            display: { provider: HEAD, model: null }
            %s
            """;

    private final PetDefinitionYamlCodec codec = new PetDefinitionYamlCodec();

    @TempDir
    Path temporary;

    @Test
    void usesExistingYamlExtensionForReadSaveArchiveAndReferenceScan() throws Exception {
        Path root = temporary.resolve("pets");
        Files.createDirectories(root);
        Path yamlFile = root.resolve("source.yaml");
        Files.writeString(yamlFile, CURRENT_DEFINITION.formatted("source", "links: [target]"));
        YamlPetDefinitionRepository repository = new YamlPetDefinitionRepository(root);

        PetDefinitionEnvelope loaded = repository.read("source").orElseThrow();
        assertEquals(Set.of("source"), repository.referenceScan("target"));

        PetDefinitionEnvelope saved = repository.saveDraft(new PetDefinitionDraft(loaded.definition(), 0));
        assertEquals(1, saved.definition().revision());
        assertTrue(Files.exists(yamlFile));
        assertFalse(Files.exists(root.resolve("source.yml")));

        repository.archive("source");
        assertFalse(Files.exists(yamlFile));
        try (var archived = Files.list(root.resolve("archive"))) {
            assertEquals(1, archived.count());
        }
    }

    @Test
    void rejectsAmbiguousExtensionsAndCaseFoldedCollisions() throws Exception {
        Path root = temporary.resolve("pets");
        Files.createDirectories(root);
        String definition = CURRENT_DEFINITION.formatted("pet", "");
        Files.writeString(root.resolve("pet.yml"), definition);
        Files.writeString(root.resolve("pet.yaml"), definition);
        YamlPetDefinitionRepository repository = new YamlPetDefinitionRepository(root);
        PetDefinition draft = codec.decode("pet", definition).definition();

        assertThrows(IOException.class, () -> repository.read("pet"));
        assertThrows(IOException.class, () -> repository.saveDraft(new PetDefinitionDraft(draft, 0)));
        assertThrows(IOException.class, () -> repository.referenceScan("target"));
        assertThrows(IOException.class, repository::list);
        assertThrows(IOException.class, () -> repository.archive("pet"));

        Path separateRoot = temporary.resolve("case-collision");
        Files.createDirectories(separateRoot);
        Files.writeString(separateRoot.resolve("Pet.yaml"), CURRENT_DEFINITION.formatted("Pet", ""));
        YamlPetDefinitionRepository separateRepository = new YamlPetDefinitionRepository(separateRoot);
        PetDefinition lowerCaseDraft = codec.decode("pet", definition).definition();
        assertThrows(IOException.class,
                () -> separateRepository.saveDraft(new PetDefinitionDraft(lowerCaseDraft, 0)));
        assertFalse(Files.exists(separateRoot.resolve("pet.yml")));
    }

    @Test
    void serializesConcurrentWritersForOneExpectedRevision() throws Exception {
        Path root = temporary.resolve("pets");
        YamlPetDefinitionRepository repository = new YamlPetDefinitionRepository(root);
        PetDefinition definition = codec.decode("pet", CURRENT_DEFINITION.formatted("pet", "")).definition();
        PetDefinitionDraft draft = new PetDefinitionDraft(definition, 0);
        int writerCount = 12;
        CountDownLatch ready = new CountDownLatch(writerCount);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(writerCount);

        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (int index = 0; index < writerCount; index++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    try {
                        return repository.saveDraft(draft);
                    } catch (Exception failure) {
                        return failure;
                    }
                }));
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            List<Object> results = new ArrayList<>();
            for (Future<Object> future : futures) results.add(future.get(10, TimeUnit.SECONDS));
            assertEquals(1, results.stream().filter(PetDefinitionEnvelope.class::isInstance).count());
            assertEquals(writerCount - 1, results.stream().filter(StaleRevisionException.class::isInstance).count());
            assertEquals(1, repository.read("pet").orElseThrow().definition().revision());
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void migratesLegacyGeneralDefinitionOnceAndPreservesRawComponents() throws Exception {
        Path root = temporary.resolve("pets");
        Files.createDirectories(root);
        Path file = root.resolve("legacy.yaml");
        String legacy = """
                schemaVersion: 1
                general:
                  texture: https://textures.minecraft.net/texture/legacy
                  name: Legacy Pet
                leveling:
                  max-level: 25
                custom-component:
                  nested: [one, two]
                """;
        Files.writeString(file, legacy);
        YamlPetDefinitionRepository repository = new YamlPetDefinitionRepository(root);

        PetDefinitionEnvelope migrated = repository.read("legacy").orElseThrow();
        String firstPersisted = Files.readString(file);
        assertEquals(PetDefinitionEnvelope.CURRENT_SCHEMA_VERSION, migrated.schemaVersion());
        assertTrue(firstPersisted.contains("schemaVersion: 2"));
        assertEquals(legacy, Files.readString(AtomicFileStore.backupPath(file)));

        Map<?, ?> extensions = assertInstanceOf(Map.class, migrated.definition().rawNode().get("extensions"));
        Map<?, ?> legacyComponents = assertInstanceOf(Map.class, extensions.get("legacyComponents"));
        assertEquals(Map.of("nested", List.of("one", "two")), legacyComponents.get("custom-component"));

        PetDefinitionEnvelope second = repository.read("legacy").orElseThrow();
        assertEquals(migrated.definition().rawNode(), second.definition().rawNode());
        assertEquals(firstPersisted, Files.readString(file));
    }
}
