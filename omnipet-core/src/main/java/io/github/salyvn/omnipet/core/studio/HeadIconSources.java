package io.github.salyvn.omnipet.core.studio;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Detects which head-icon source a raw operator input describes.
 *
 * <p>The Studio previously demanded two tokens, {@code <SOURCE> <value>}, so a pasted base64 texture
 * blob — one token — was rejected even though {@code PetDefinitionStudioService} already accepts
 * exactly that payload at save time. Detection lets the editor fail fast in chat while keeping
 * format truth in core, where the authoritative save-time validation lives.
 *
 * <p>Core-side by design: no Bukkit or Adventure import, so {@code checkCoreBoundary} stays green.
 */
public final class HeadIconSources {
    /** The same 16 KiB ceiling the save-time validator enforces. */
    public static final int MAX_BASE64_LENGTH = 16_384;

    private static final String TEXTURE_URL = "TEXTURE_URL";
    private static final String BASE64 = "BASE64";
    private static final String HEAD_CATALOG = "HEAD_CATALOG";
    private static final String TEXTURE_HOST = "https://textures.minecraft.net/texture/";

    private HeadIconSources() {}

    /**
     * Resolves a raw input into a source and value.
     *
     * <p>Resolution order, deliberately legacy-first so a two-token input can never be misread:
     * <ol>
     *   <li>{@code <TEXTURE_URL|BASE64|HEAD_CATALOG> <value>} — the explicit legacy form</li>
     *   <li>{@code http://} or {@code https://} prefix → {@code TEXTURE_URL}</li>
     *   <li>64 hex characters → {@code TEXTURE_URL} against the Mojang texture host</li>
     *   <li>otherwise a single token that decodes to a {@code textures.SKIN.url} object → {@code BASE64}</li>
     * </ol>
     *
     * <p>{@code HEAD_CATALOG} stays reachable only through the explicit two-token form, because no
     * catalog provider ships yet.
     *
     * @throws IllegalArgumentException with an operator-readable reason when nothing matches
     */
    public static Detected detect(String input) {
        String value = required(input);

        Detected legacy = legacy(value);
        if (legacy != null) return legacy;

        if (value.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException(
                    "expected a texture URL, a 64-character texture hash, a base64 texture payload, "
                            + "or <TEXTURE_URL|BASE64|HEAD_CATALOG> <value>");
        }
        if (isHttpUrl(value)) return new Detected(TEXTURE_URL, value);
        if (isTextureHash(value)) return new Detected(TEXTURE_URL, TEXTURE_HOST + value.toLowerCase(Locale.ROOT));
        validateBase64(value);
        return new Detected(BASE64, value);
    }

    /** The explicit {@code <SOURCE> <value>} form, or {@code null} when the input is not that shape. */
    private static Detected legacy(String value) {
        String[] parts = value.split("\\s+", 2);
        if (parts.length != 2) return null;
        String source = parts[0].toUpperCase(Locale.ROOT);
        if (!source.equals(TEXTURE_URL) && !source.equals(BASE64) && !source.equals(HEAD_CATALOG)) return null;
        String payload = parts[1].trim();
        if (payload.isEmpty()) throw new IllegalArgumentException("head icon value is required");
        if (source.equals(BASE64)) validateBase64(payload);
        if (source.equals(TEXTURE_URL) && !isHttpUrl(payload)) {
            throw new IllegalArgumentException("texture URL must start with http:// or https://");
        }
        return new Detected(source, payload);
    }

    private static boolean isHttpUrl(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.startsWith("http://") || lower.startsWith("https://");
    }

    private static boolean isTextureHash(String value) {
        if (value.length() != 64) return false;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            boolean hex = (character >= '0' && character <= '9')
                    || (character >= 'a' && character <= 'f')
                    || (character >= 'A' && character <= 'F');
            if (!hex) return false;
        }
        return true;
    }

    /**
     * Applies the same rules as the save-time validator, so a payload accepted in chat cannot be
     * rejected later at Save.
     */
    private static void validateBase64(String encoded) {
        if (encoded.length() > MAX_BASE64_LENGTH) {
            throw new IllegalArgumentException("base64 texture is larger than 16 KiB");
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("base64 texture is not valid base64", error);
        }
        Map<String, Object> skin;
        try {
            Map<String, Object> root = StrictJsonDocuments.readObject(new String(decoded, StandardCharsets.UTF_8));
            skin = requiredObject(requiredObject(root, "textures").get("SKIN"));
        } catch (RuntimeException error) {
            throw new IllegalArgumentException(
                    "base64 texture must decode to a Minecraft texture object with textures.SKIN.url", error);
        }
        if (!(skin.get("url") instanceof String url) || url.isBlank()) {
            throw new IllegalArgumentException("base64 texture is missing textures.SKIN.url");
        }
        URI uri;
        try {
            uri = URI.create(url);
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("base64 texture URL is malformed", error);
        }
        if (uri.getHost() == null
                || !("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))) {
            throw new IllegalArgumentException("base64 texture URL must be absolute HTTP(S)");
        }
    }

    private static Map<String, Object> requiredObject(Map<String, Object> parent, String key) {
        return requiredObject(parent.get(key));
    }

    private static Map<String, Object> requiredObject(Object value) {
        if (!(value instanceof Map<?, ?> map)) throw new IllegalArgumentException("texture node must be an object");
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, nested) -> result.put(String.valueOf(key), nested));
        return result;
    }

    private static String required(String input) {
        if (input == null || input.trim().isEmpty()) throw new IllegalArgumentException("head icon input is required");
        return input.trim();
    }

    /** A resolved head-icon source and its value. */
    public record Detected(String source, String value) {
        public Detected {
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(value, "value");
        }
    }
}
