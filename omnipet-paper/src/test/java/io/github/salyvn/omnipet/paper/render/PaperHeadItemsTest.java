package io.github.salyvn.omnipet.paper.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.Test;

import io.github.salyvn.omnipet.core.runtime.RendererAppearance;

class PaperHeadItemsTest {
    @Test
    void directAndBase64SourcesResolveWithoutAProvider() {
        assertEquals("https://textures.minecraft.net/texture/a", PaperHeadItems.skinUrl(
                appearance("TEXTURE_URL", "https://textures.minecraft.net/texture/a")).toString());
        String encoded = Base64.getEncoder().encodeToString(
                "{\"textures\":{\"SKIN\":{\"url\":\"https://textures.minecraft.net/texture/b\"}}}"
                        .getBytes(StandardCharsets.UTF_8));
        assertEquals("https://textures.minecraft.net/texture/b",
                PaperHeadItems.skinUrl(appearance("BASE64", encoded)).toString());
    }

    @Test
    void absentHeadCatalogUsesVanillaHeadAndInvalidFallbackFailsClosed() {
        assertNull(PaperHeadItems.skinUrl(appearance("HEAD_CATALOG", "dragon")));
        assertThrows(IllegalArgumentException.class,
                () -> PaperHeadItems.skinUrl(appearance("FILE", "local.png")));
    }

    private static RendererAppearance appearance(String source, String value) {
        return new RendererAppearance("HEAD", "", source, value);
    }
}
