package io.github.salyvn.omnipet.core.runtime;

import java.util.Locale;

/**
 * What a rendered pet looks like, including the name shown above it.
 *
 * <p>The name belongs here rather than on the spawn request because renaming a pet is an appearance
 * change: it flows through {@code updateAppearance}, which already exists on the port, instead of needing
 * a new method every implementor would have to grow.
 *
 * <p>The level is carried beside the name rather than baked into it, so the nameplate template can place
 * the two independently. An operator who wants no level writes a template without {@code <level>}; one
 * who wants it in front writes it in front. Baking it in made both impossible.
 *
 * @param displayName the name to show above the pet, or empty for no nameplate at all
 * @param level the pet's level, or null when it could not be read
 */
public record RendererAppearance(
        String provider,
        String assetId,
        String fallbackHeadSource,
        String fallbackHeadValue,
        String displayName,
        Integer level) {
    /** How long a nameplate may be. Long enough for a name and a level, short enough not to be a banner. */
    public static final int MAX_DISPLAY_NAME = 64;

    public RendererAppearance {
        provider = require(provider, "renderer provider").toUpperCase(Locale.ROOT);
        assetId = assetId == null ? "" : assetId.trim();
        fallbackHeadSource = require(fallbackHeadSource, "fallback head source").toUpperCase(Locale.ROOT);
        fallbackHeadValue = require(fallbackHeadValue, "fallback head value");
        // Empty rather than rejected: no name is a legitimate state, and it is the state every pet was in
        // before nameplates existed.
        displayName = displayName == null ? "" : displayName.trim();
        if (displayName.length() > MAX_DISPLAY_NAME) {
            throw new IllegalArgumentException("renderer display name is too long");
        }
        if (provider.equals("MODELENGINE") && assetId.isBlank()) {
            throw new IllegalArgumentException("ModelEngine appearance requires an asset ID");
        }
    }

    /** An appearance carrying a name but no level, for a caller that could not read one. */
    public RendererAppearance(
            String provider,
            String assetId,
            String fallbackHeadSource,
            String fallbackHeadValue,
            String displayName) {
        this(provider, assetId, fallbackHeadSource, fallbackHeadValue, displayName, null);
    }

    /** An appearance with no nameplate, for a caller that does not have a name to show. */
    public RendererAppearance(
            String provider, String assetId, String fallbackHeadSource, String fallbackHeadValue) {
        this(provider, assetId, fallbackHeadSource, fallbackHeadValue, "", null);
    }

    /** Whether this pet should carry a nameplate. */
    public boolean named() {
        return !displayName.isEmpty();
    }

    private static String require(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required");
        return value.trim();
    }
}
