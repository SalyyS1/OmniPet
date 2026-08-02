package io.github.salyvn.omnipet.paper.render;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;

import io.github.salyvn.omnipet.core.runtime.RendererAppearance;

final class PaperHeadItems {
    private static final Pattern TEXTURE_URL = Pattern.compile("\\\"url\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static final int MAX_BASE64_LENGTH = 16_384;

    private PaperHeadItems() {}

    static ItemStack create(RendererAppearance appearance) {
        ItemStack result = new ItemStack(Material.PLAYER_HEAD);
        URL skin = skinUrl(appearance);
        if (skin == null) return result;
        String value = appearance.fallbackHeadValue();
        SkullMeta meta = (SkullMeta) result.getItemMeta();
        PlayerProfile profile = Bukkit.createPlayerProfile(
                UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8)), null);
        PlayerTextures textures = profile.getTextures();
        textures.setSkin(skin);
        profile.setTextures(textures);
        meta.setOwnerProfile(profile);
        result.setItemMeta(meta);
        return result;
    }

    static URL skinUrl(RendererAppearance appearance) {
        String source = appearance.fallbackHeadSource().toUpperCase(Locale.ROOT);
        String value = appearance.fallbackHeadValue();
        return switch (source) {
            case "TEXTURE_URL" -> requireUrl(value);
            case "BASE64" -> requireUrl(extractBase64Url(value));
            case "HEAD_CATALOG" -> null;
            default -> throw new IllegalArgumentException("unsupported built-in HEAD source: " + source);
        };
    }

    private static String extractBase64Url(String encoded) {
        if (encoded.length() > MAX_BASE64_LENGTH) throw new IllegalArgumentException("HEAD BASE64 value is too large");
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("HEAD BASE64 value is invalid", failure);
        }
        Matcher matcher = TEXTURE_URL.matcher(new String(decoded, StandardCharsets.UTF_8));
        if (!matcher.find()) throw new IllegalArgumentException("HEAD BASE64 texture URL is missing");
        return matcher.group(1).replace("\\/", "/");
    }

    private static URL requireUrl(String value) {
        try {
            URI uri = URI.create(value);
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null) {
                throw new IllegalArgumentException("HEAD texture URL must be absolute HTTP(S)");
            }
            return uri.toURL();
        } catch (IllegalArgumentException | MalformedURLException failure) {
            throw new IllegalArgumentException("HEAD texture URL is invalid", failure);
        }
    }
}
