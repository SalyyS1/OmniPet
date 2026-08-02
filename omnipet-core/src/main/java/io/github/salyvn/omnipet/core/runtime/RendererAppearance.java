package io.github.salyvn.omnipet.core.runtime;

import java.util.Locale;

public record RendererAppearance(
        String provider,
        String assetId,
        String fallbackHeadSource,
        String fallbackHeadValue) {
    public RendererAppearance {
        provider = require(provider, "renderer provider").toUpperCase(Locale.ROOT);
        assetId = assetId == null ? "" : assetId.trim();
        fallbackHeadSource = require(fallbackHeadSource, "fallback head source").toUpperCase(Locale.ROOT);
        fallbackHeadValue = require(fallbackHeadValue, "fallback head value");
        if (provider.equals("MODELENGINE") && assetId.isBlank()) {
            throw new IllegalArgumentException("ModelEngine appearance requires an asset ID");
        }
    }

    private static String require(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required");
        return value.trim();
    }
}
