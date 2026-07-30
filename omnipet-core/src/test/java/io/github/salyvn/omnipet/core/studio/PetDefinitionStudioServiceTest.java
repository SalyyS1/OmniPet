package io.github.salyvn.omnipet.core.studio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.salyvn.omnipet.core.domain.DisplayDefinition;
import io.github.salyvn.omnipet.core.domain.HeadIcon;
import io.github.salyvn.omnipet.core.domain.PetDefinition;
import io.github.salyvn.omnipet.core.domain.PetTier;
import io.github.salyvn.omnipet.core.persistence.FoundationRegistryLoader;
import io.github.salyvn.omnipet.core.persistence.InMemoryRegistrySnapshotRepository;
import io.github.salyvn.omnipet.core.persistence.PetDefinitionDraft;
import io.github.salyvn.omnipet.core.persistence.YamlPetDefinitionRepository;

class PetDefinitionStudioServiceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void savesDiskAndLiveRegistryAsOneGeneration() throws IOException {
        Fixture fixture = fixtureWithDefinition();
        PetDefinition current = fixture.registry.current().definitions().get("wolf");
        StudioPetDraft draft = StudioPetDraft.edit(
                current,
                StudioPetDraft.semanticHash(current.rawNode()),
                fixture.registry.current().generation()).withTier(PetTier.S);

        var saved = fixture.service.save(draft, UUID.randomUUID());

        assertEquals(2, saved.definition().definition().revision());
        assertEquals(PetTier.S, fixture.registry.current().definitions().get("wolf").tier());
        assertEquals(PetTier.S, fixture.definitions.read("wolf").orElseThrow().definition().tier());
    }

    @Test
    void rollsBackDiskAndRegistryWhenActivationFails() throws IOException {
        Fixture fixture = fixtureWithDefinition(snapshot -> {
            if (snapshot.definitions().get("wolf").tier() == PetTier.S) {
                throw new IllegalStateException("activation failed");
            }
        });
        Path definitionFile = temporaryDirectory.resolve("pets/wolf.yml");
        String original = Files.readString(definitionFile);
        PetDefinition current = fixture.registry.current().definitions().get("wolf");
        StudioPetDraft draft = StudioPetDraft.edit(
                current,
                StudioPetDraft.semanticHash(current.rawNode()),
                fixture.registry.current().generation()).withTier(PetTier.S);

        assertThrows(IllegalStateException.class, () -> fixture.service.save(draft, UUID.randomUUID()));

        assertEquals(original, Files.readString(definitionFile));
        assertEquals(1, fixture.definitions.read("wolf").orElseThrow().definition().revision());
        assertEquals(PetTier.D, fixture.registry.current().definitions().get("wolf").tier());
        assertEquals(1, fixture.registry.current().generation());
    }

    @Test
    void repeatedIdempotencyKeyDoesNotWriteTwice() throws IOException {
        YamlPetDefinitionRepository definitions = new YamlPetDefinitionRepository(temporaryDirectory.resolve("pets"));
        InMemoryRegistrySnapshotRepository registry = new InMemoryRegistrySnapshotRepository();
        PetDefinitionStudioService service = new PetDefinitionStudioService(definitions, registry, ignored -> {});
        service.reload();
        StudioPetDraft draft = StudioPetDraft.create(
                "fox",
                registry.current().generation(),
                PetTier.C,
                new HeadIcon("TEXTURE_URL", "https://example.invalid/fox.png"),
                new DisplayDefinition(DisplayDefinition.Provider.HEAD, null),
                Map.of("custom", Map.of("kept", true)));
        UUID key = UUID.randomUUID();

        var first = service.save(draft, key);
        var second = service.save(draft, key);

        assertEquals(first, second);
        assertEquals(1, definitions.read("fox").orElseThrow().definition().revision());
        assertEquals(2, registry.current().generation());
    }

    @Test
    void createThenEditKeepsDiskAndLiveRawNodesIdentical() throws IOException {
        YamlPetDefinitionRepository definitions = new YamlPetDefinitionRepository(temporaryDirectory.resolve("pets"));
        InMemoryRegistrySnapshotRepository registry = new InMemoryRegistrySnapshotRepository();
        PetDefinitionStudioService service = new PetDefinitionStudioService(definitions, registry, ignored -> {});
        service.reload();
        StudioPetDraft create = StudioPetDraft.create(
                "fox", registry.current().generation(), PetTier.C,
                new HeadIcon("TEXTURE_URL", "https://example.invalid/fox.png"),
                new DisplayDefinition(DisplayDefinition.Provider.HEAD, null), Map.of("custom", Map.of("kept", true)))
                .withIcon(new HeadIcon("TEXTURE_URL", "https://example.invalid/fox.png"))
                .withDisplay(new DisplayDefinition(DisplayDefinition.Provider.HEAD, null));
        var first = service.save(create, UUID.randomUUID());
        StudioPetDraft edit = StudioPetDraft.edit(first.definition().definition(),
                StudioPetDraft.semanticHash(first.definition().definition().rawNode()), registry.current().generation())
                .withTier(PetTier.S);

        service.save(edit, UUID.randomUUID());

        Map<String, Object> liveRaw = registry.current().definitions().get("fox").rawNode();
        Map<String, Object> diskRaw = definitions.read("fox").orElseThrow().definition().rawNode();
        assertEquals(liveRaw, diskRaw, () -> "live=" + typed(liveRaw) + " disk=" + typed(diskRaw));
        assertEquals(2, definitions.read("fox").orElseThrow().definition().revision());
    }

    @Test
    void rejectsAStaleSemanticHashBeforeWriting() throws IOException {
        Fixture fixture = fixtureWithDefinition();
        PetDefinition current = fixture.registry.current().definitions().get("wolf");
        StudioPetDraft stale = StudioPetDraft.edit(current, "stale", fixture.registry.current().generation());

        assertThrows(StudioConflictException.class, () -> fixture.service.save(stale, UUID.randomUUID()));
        assertEquals(1, fixture.definitions.read("wolf").orElseThrow().definition().revision());
    }

    @Test
    void rejectsMalformedBase64HeadBeforeWriting() throws IOException {
        Fixture fixture = fixtureWithDefinition();
        PetDefinition current = fixture.registry.current().definitions().get("wolf");
        StudioPetDraft malformed = StudioPetDraft.edit(
                current,
                StudioPetDraft.semanticHash(current.rawNode()),
                fixture.registry.current().generation())
                .withIcon(new HeadIcon("BASE64", "not-base64"));
        StudioPetDraft wrongPayload = malformed.withIcon(new HeadIcon(
                "BASE64",
                Base64.getEncoder().encodeToString("not a texture object".getBytes(java.nio.charset.StandardCharsets.UTF_8))));
        StudioPetDraft wrongShape = malformed.withIcon(new HeadIcon(
                "BASE64",
                Base64.getEncoder().encodeToString(
                        "{\"textures\":1,\"SKIN\":2,\"url\":3}".getBytes(java.nio.charset.StandardCharsets.UTF_8))));
        StudioPetDraft yamlPayload = malformed.withIcon(new HeadIcon(
                "BASE64",
                Base64.getEncoder().encodeToString(
                        "textures:\n  SKIN:\n    url: https://textures.minecraft.net/texture/abc\n"
                                .getBytes(java.nio.charset.StandardCharsets.UTF_8))));

        assertThrows(IllegalArgumentException.class, () -> fixture.service.save(malformed, UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class, () -> fixture.service.save(wrongPayload, UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class, () -> fixture.service.save(wrongShape, UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class, () -> fixture.service.save(yamlPayload, UUID.randomUUID()));
        assertEquals(1, fixture.definitions.read("wolf").orElseThrow().definition().revision());
        assertEquals(new HeadIcon("TEXTURE_URL", "https://example.invalid/wolf.png"),
                fixture.registry.current().definitions().get("wolf").icon());
    }

    @Test
    void acceptsAValidBase64MinecraftTexture() throws IOException {
        Fixture fixture = fixtureWithDefinition();
        PetDefinition current = fixture.registry.current().definitions().get("wolf");
        String payload = "{\"textures\":{\"SKIN\":{\"url\":\"https://textures.minecraft.net/texture/abc\"}}}";
        StudioPetDraft draft = StudioPetDraft.edit(current, StudioPetDraft.semanticHash(current.rawNode()),
                fixture.registry.current().generation()).withIcon(new HeadIcon(
                        "BASE64",
                        Base64.getEncoder().encodeToString(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8))));

        fixture.service.save(draft, UUID.randomUUID());

        assertEquals("BASE64", fixture.registry.current().definitions().get("wolf").icon().source());
    }

    @Test
    void archivesThroughTheSameRegistryTransaction() throws IOException {
        Fixture fixture = fixtureWithDefinition();
        PetDefinition current = fixture.registry.current().definitions().get("wolf");

        fixture.service.archive(
                "wolf",
                current.revision(),
                StudioPetDraft.semanticHash(current.rawNode()),
                fixture.registry.current().generation(),
                UUID.randomUUID());

        assertEquals(false, fixture.registry.current().definitions().containsKey("wolf"));
        assertEquals(false, fixture.definitions.read("wolf").isPresent());
        assertEquals(2, fixture.registry.current().generation());
    }

    @Test
    void restoresArchivedFileWhenActivationFails() throws IOException {
        Fixture fixture = fixtureWithDefinition(snapshot -> {
            if (!snapshot.definitions().containsKey("wolf")) throw new IllegalStateException("activation failed");
        });
        PetDefinition current = fixture.registry.current().definitions().get("wolf");

        assertThrows(IllegalStateException.class, () -> fixture.service.archive(
                "wolf",
                current.revision(),
                StudioPetDraft.semanticHash(current.rawNode()),
                fixture.registry.current().generation(),
                UUID.randomUUID()));

        assertEquals(true, fixture.definitions.read("wolf").isPresent());
        assertEquals(true, fixture.registry.current().definitions().containsKey("wolf"));
        assertEquals(1, fixture.registry.current().generation());
    }

    private Fixture fixtureWithDefinition() throws IOException {
        return fixtureWithDefinition(ignored -> {});
    }

    private Fixture fixtureWithDefinition(java.util.function.Consumer<io.github.salyvn.omnipet.core.persistence.RegistrySnapshot> activation)
            throws IOException {
        YamlPetDefinitionRepository definitions = new YamlPetDefinitionRepository(temporaryDirectory.resolve("pets"));
        PetDefinition initial = new PetDefinition(
                "wolf",
                0,
                PetTier.D,
                new HeadIcon("TEXTURE_URL", "https://example.invalid/wolf.png"),
                new DisplayDefinition(DisplayDefinition.Provider.HEAD, null),
                Map.of("classification", Map.of("tier", "D"), "custom", Map.of("kept", true)));
        definitions.saveDraft(new PetDefinitionDraft(initial, 0));
        InMemoryRegistrySnapshotRepository registry = new InMemoryRegistrySnapshotRepository();
        new FoundationRegistryLoader().load(definitions, registry);
        PetDefinitionStudioService service = new PetDefinitionStudioService(definitions, registry, activation);
        return new Fixture(definitions, registry, service);
    }

    private record Fixture(
            YamlPetDefinitionRepository definitions,
            InMemoryRegistrySnapshotRepository registry,
            PetDefinitionStudioService service) {}

    private static String typed(Object value) {
        if (value instanceof Map<?, ?> map) {
            return map.entrySet().stream().map(entry -> entry.getKey() + "=" + typed(entry.getValue()))
                    .collect(java.util.stream.Collectors.joining(",", "{", "}"));
        }
        if (value instanceof java.util.List<?> list) {
            return list.stream().map(PetDefinitionStudioServiceTest::typed)
                    .collect(java.util.stream.Collectors.joining(",", "[", "]"));
        }
        return value == null ? "null" : value.getClass().getSimpleName() + "(" + value + ")";
    }
}
