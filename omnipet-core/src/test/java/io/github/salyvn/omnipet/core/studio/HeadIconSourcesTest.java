package io.github.salyvn.omnipet.core.studio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.salyvn.omnipet.core.domain.DisplayDefinition;
import io.github.salyvn.omnipet.core.domain.HeadIcon;
import io.github.salyvn.omnipet.core.domain.PetTier;
import io.github.salyvn.omnipet.core.persistence.InMemoryRegistrySnapshotRepository;
import io.github.salyvn.omnipet.core.persistence.YamlPetDefinitionRepository;

class HeadIconSourcesTest {
    @TempDir
    Path temporaryDirectory;

    /** The one-token paste shape an operator copies out of a skin site. */
    private static final String PASTED_BASE64 = base64(
            "{\"textures\":{\"SKIN\":{\"url\":\"https://textures.minecraft.net/texture/"
                    + "a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2\"}}}");

    @Test
    void aPastedBase64BlobIsAcceptedAsASingleToken() {
        HeadIconSources.Detected detected = HeadIconSources.detect(PASTED_BASE64);

        assertEquals("BASE64", detected.source());
        assertEquals(PASTED_BASE64, detected.value());
    }

    @Test
    void surroundingWhitespaceFromAPasteIsTrimmed() {
        assertEquals(PASTED_BASE64, HeadIconSources.detect("  " + PASTED_BASE64 + "\n").value());
    }

    @Test
    void anHttpsTextureUrlIsDetected() {
        String url = "https://textures.minecraft.net/texture/abc123";

        assertEquals(new HeadIconSources.Detected("TEXTURE_URL", url), HeadIconSources.detect(url));
    }

    @Test
    void aPlainHttpUrlIsAlsoAccepted() {
        assertEquals("TEXTURE_URL", HeadIconSources.detect("http://example.test/skin.png").source());
    }

    @Test
    void aBareSixtyFourCharacterHashExpandsToTheMojangTextureHost() {
        String hash = "a".repeat(64);

        HeadIconSources.Detected detected = HeadIconSources.detect(hash);

        assertEquals("TEXTURE_URL", detected.source());
        assertEquals("https://textures.minecraft.net/texture/" + hash, detected.value());
    }

    @Test
    void anUppercaseHashIsNormalisedToLowercase() {
        assertTrue(HeadIconSources.detect("A".repeat(64)).value().endsWith("a".repeat(64)));
    }

    @Test
    void theLegacyTwoTokenFormStillWorksForEverySource() {
        assertEquals(new HeadIconSources.Detected("BASE64", PASTED_BASE64),
                HeadIconSources.detect("BASE64 " + PASTED_BASE64));
        assertEquals(new HeadIconSources.Detected("TEXTURE_URL", "https://example.test/a.png"),
                HeadIconSources.detect("TEXTURE_URL https://example.test/a.png"));
        assertEquals(new HeadIconSources.Detected("HEAD_CATALOG", "wolf_head"),
                HeadIconSources.detect("HEAD_CATALOG wolf_head"));
    }

    @Test
    void theLegacySourceTokenIsCaseInsensitive() {
        assertEquals("HEAD_CATALOG", HeadIconSources.detect("head_catalog wolf_head").source());
    }

    @Test
    void headCatalogIsReachableOnlyThroughTheExplicitForm() {
        // No catalog provider ships yet, so a bare value must never resolve to HEAD_CATALOG.
        assertThrows(IllegalArgumentException.class, () -> HeadIconSources.detect("wolf_head"));
    }

    @Test
    void anExplicitTextureUrlThatIsNotHttpIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> HeadIconSources.detect("TEXTURE_URL textures.minecraft.net/texture/abc"));
    }

    @Test
    void aBase64PayloadWithoutSkinUrlIsRejected() {
        String encoded = base64("{\"textures\":{\"CAPE\":{\"url\":\"https://example.test/c.png\"}}}");

        assertThrows(IllegalArgumentException.class, () -> HeadIconSources.detect(encoded));
    }

    @Test
    void aBase64PayloadWithARelativeUrlIsRejected() {
        String encoded = base64("{\"textures\":{\"SKIN\":{\"url\":\"/texture/abc\"}}}");

        assertThrows(IllegalArgumentException.class, () -> HeadIconSources.detect(encoded));
    }

    @Test
    void aBase64PayloadWithABlankUrlIsRejected() {
        String encoded = base64("{\"textures\":{\"SKIN\":{\"url\":\"\"}}}");

        assertThrows(IllegalArgumentException.class, () -> HeadIconSources.detect(encoded));
    }

    @Test
    void anOversizePayloadIsRejectedBeforeDecoding() {
        String oversize = "A".repeat(HeadIconSources.MAX_BASE64_LENGTH + 4);

        assertThrows(IllegalArgumentException.class, () -> HeadIconSources.detect(oversize));
    }

    @Test
    void aNonBase64SingleTokenIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> HeadIconSources.detect("!!!not-base64!!!"));
    }

    @Test
    void anUnknownSourceKeywordIsRejectedRatherThanTreatedAsAValue() {
        assertThrows(IllegalArgumentException.class, () -> HeadIconSources.detect("SKULL_OWNER Notch"));
    }

    @Test
    void blankInputIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> HeadIconSources.detect("   "));
        assertThrows(IllegalArgumentException.class, () -> HeadIconSources.detect(null));
    }

    @Test
    void aChatAcceptedPayloadAlsoPassesSaveTimeValidation() throws IOException {
        // Chat detection and PetDefinitionStudioService must agree, or an operator would be told the
        // value is fine and then have Save reject it.
        HeadIconSources.Detected detected = HeadIconSources.detect(PASTED_BASE64);
        assertEquals("BASE64", detected.source());

        YamlPetDefinitionRepository definitions =
                new YamlPetDefinitionRepository(temporaryDirectory.resolve("pets"));
        InMemoryRegistrySnapshotRepository registry = new InMemoryRegistrySnapshotRepository();
        PetDefinitionStudioService service =
                new PetDefinitionStudioService(definitions, registry, ignored -> {});
        service.reload();
        StudioPetDraft draft = StudioPetDraft.create(
                "wolf",
                registry.current().generation(),
                PetTier.D,
                new HeadIcon(detected.source(), detected.value()),
                new DisplayDefinition(DisplayDefinition.Provider.HEAD, null),
                Map.of());

        var saved = service.save(draft, UUID.randomUUID());

        assertEquals(1, saved.definition().definition().revision());
    }

    private static String base64(String json) {
        return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }
}
