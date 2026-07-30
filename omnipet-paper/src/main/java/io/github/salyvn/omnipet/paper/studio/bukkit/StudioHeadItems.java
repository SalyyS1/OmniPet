package io.github.salyvn.omnipet.paper.studio.bukkit;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerTextures;

import com.destroystokyo.paper.profile.ProfileProperty;
import com.destroystokyo.paper.profile.PlayerProfile;

import io.github.salyvn.omnipet.core.domain.HeadIcon;

final class StudioHeadItems {
    private StudioHeadItems() {}

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
            // The editor still shows the raw value and Save validation reports malformed input.
        }
        return stack;
    }
}
