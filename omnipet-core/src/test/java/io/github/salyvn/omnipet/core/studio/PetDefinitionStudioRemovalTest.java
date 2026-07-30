package io.github.salyvn.omnipet.core.studio;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.salyvn.omnipet.core.domain.DisplayDefinition;
import io.github.salyvn.omnipet.core.domain.HeadIcon;
import io.github.salyvn.omnipet.core.domain.PetDefinition;
import io.github.salyvn.omnipet.core.domain.PetTier;
import io.github.salyvn.omnipet.core.persistence.AtomicFileStore;
import io.github.salyvn.omnipet.core.persistence.FoundationRegistryLoader;
import io.github.salyvn.omnipet.core.persistence.InMemoryRegistrySnapshotRepository;
import io.github.salyvn.omnipet.core.persistence.PetReferenceScanner;
import io.github.salyvn.omnipet.core.persistence.YamlPetDefinitionRepository;

class PetDefinitionStudioRemovalTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void hardDeleteRemovesDefinitionAndRepeatedKeyIsIdempotent() throws IOException {
        Fixture fixture = fixture(ignored -> {}, List.of());
        PetDefinition current = fixture.current();
        UUID key = UUID.randomUUID();

        var first = fixture.service.hardDelete("wolf", "wolf", current.revision(),
                StudioPetDraft.semanticHash(current.rawNode()), fixture.registry.current().generation(), key);
        var second = fixture.service.hardDelete("wolf", "wolf", current.revision(),
                StudioPetDraft.semanticHash(current.rawNode()), fixture.registry.current().generation(), key);

        assertEquals(first, second);
        assertFalse(fixture.registry.current().definitions().containsKey("wolf"));
        assertTrue(fixture.definitions.read("wolf").isEmpty());
    }

    @Test
    void hardDeleteRequiresExactIdAndBlocksReferences() throws IOException {
        Fixture exact = fixture(ignored -> {}, List.of());
        PetDefinition current = exact.current();
        assertThrows(StudioConflictException.class, () -> exact.service.hardDelete("wolf", "Wolf", current.revision(),
                StudioPetDraft.semanticHash(current.rawNode()), exact.registry.current().generation(), UUID.randomUUID()));
        assertTrue(exact.definitions.read("wolf").isPresent());

        Fixture referenced = fixture(ignored -> {}, List.of(id -> Set.of("studio-session:test")));
        PetDefinition referencedCurrent = referenced.current();
        assertThrows(StudioConflictException.class, () -> referenced.service.hardDelete("wolf", "wolf",
                referencedCurrent.revision(), StudioPetDraft.semanticHash(referencedCurrent.rawNode()),
                referenced.registry.current().generation(), UUID.randomUUID()));
        assertTrue(referenced.definitions.read("wolf").isPresent());
    }

    @Test
    void hardDeleteRejectsStaleRevisionHashAndGeneration() throws IOException {
        Fixture revision = fixture(ignored -> {}, List.of());
        PetDefinition current = revision.current();
        assertThrows(StudioConflictException.class, () -> revision.service.hardDelete("wolf", "wolf",
                current.revision() + 1, StudioPetDraft.semanticHash(current.rawNode()),
                revision.registry.current().generation(), UUID.randomUUID()));

        Fixture hash = fixture(ignored -> {}, List.of());
        PetDefinition hashCurrent = hash.current();
        assertThrows(StudioConflictException.class, () -> hash.service.hardDelete("wolf", "wolf",
                hashCurrent.revision(), "stale-hash", hash.registry.current().generation(), UUID.randomUUID()));

        Fixture generation = fixture(ignored -> {}, List.of());
        PetDefinition generationCurrent = generation.current();
        assertThrows(StudioConflictException.class, () -> generation.service.hardDelete("wolf", "wolf",
                generationCurrent.revision(), StudioPetDraft.semanticHash(generationCurrent.rawNode()),
                generation.registry.current().generation() + 1, UUID.randomUUID()));
    }

    @Test
    void activationFailureRestoresSourceAndBackupBytes() throws IOException {
        Fixture fixture = fixture(snapshot -> {
            if (!snapshot.definitions().containsKey("wolf")) throw new IllegalStateException("activation failed");
        }, List.of());
        Path source = fixture.root.resolve("pets/wolf.yml");
        Path backup = AtomicFileStore.backupPath(source);
        byte[] sourceBefore = Files.readAllBytes(source);
        byte[] backupBefore = "previous-backup-bytes".getBytes(StandardCharsets.UTF_8);
        Files.write(backup, backupBefore);
        PetDefinition current = fixture.current();

        assertThrows(IllegalStateException.class, () -> fixture.service.hardDelete("wolf", "wolf", current.revision(),
                StudioPetDraft.semanticHash(current.rawNode()), fixture.registry.current().generation(), UUID.randomUUID()));

        assertArrayEquals(sourceBefore, Files.readAllBytes(source));
        assertArrayEquals(backupBefore, Files.readAllBytes(backup));
        assertTrue(fixture.registry.current().definitions().containsKey("wolf"));
    }

    private Fixture fixture(Consumer<io.github.salyvn.omnipet.core.persistence.RegistrySnapshot> activation,
                            List<PetReferenceScanner> scanners) throws IOException {
        Path root = temporaryDirectory.resolve(UUID.randomUUID().toString());
        YamlPetDefinitionRepository definitions = new YamlPetDefinitionRepository(root.resolve("pets"));
        PetDefinition initial = new PetDefinition("wolf", 0, PetTier.D,
                new HeadIcon("TEXTURE_URL", "https://example.invalid/wolf.png"),
                new DisplayDefinition(DisplayDefinition.Provider.HEAD, null),
                Map.of("classification", Map.of("tier", "D"), "unknown", Map.of("kept", true)));
        definitions.saveDraft(new io.github.salyvn.omnipet.core.persistence.PetDefinitionDraft(initial, 0));
        InMemoryRegistrySnapshotRepository registry = new InMemoryRegistrySnapshotRepository();
        new FoundationRegistryLoader().load(definitions, registry);
        return new Fixture(root, definitions, registry,
                new PetDefinitionStudioService(definitions, registry, activation, scanners));
    }

    private record Fixture(Path root, YamlPetDefinitionRepository definitions,
                           InMemoryRegistrySnapshotRepository registry, PetDefinitionStudioService service) {
        private PetDefinition current() throws IOException {
            return definitions.read("wolf").orElseThrow().definition();
        }
    }
}
