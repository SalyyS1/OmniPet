package io.github.salyvn.omnipet.paper.gui.hatch;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerTextures;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;

import io.github.salyvn.omnipet.core.domain.HeadIcon;

final class HatchHeadItems {
    private HatchHeadItems() {}

    static ItemStack apply(ItemStack stack, HeadIcon icon) {
        if (!(stack.getItemMeta() instanceof SkullMeta meta) || icon == null) return stack;
        try {
            UUID profileId = UUID.nameUUIDFromBytes(icon.value().getBytes(StandardCharsets.UTF_8));
            PlayerProfile profile = Bukkit.createProfile(profileId);
            switch (icon.source().toUpperCase(Locale.ROOT)) {
                case "TEXTURE_URL" -> {
                    PlayerTextures textures = profile.getTextures();
                    textures.setSkin(URI.create(icon.value()).toURL());
                    profile.setTextures(textures);
                }
                case "BASE64" -> profile.setProperty(new ProfileProperty("textures", icon.value()));
                default -> { return stack; }
            }
            meta.setPlayerProfile(profile);
            stack.setItemMeta(meta);
        } catch (RuntimeException | java.net.MalformedURLException ignored) {
            // Keep the mandatory head item usable even when an optional texture is malformed.
        }
        return stack;
    }
}
