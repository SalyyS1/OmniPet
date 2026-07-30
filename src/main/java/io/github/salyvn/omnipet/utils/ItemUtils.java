package io.github.salyvn.omnipet.utils;

import java.net.MalformedURLException;
import java.net.URI;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.profile.PlayerProfile;

public class ItemUtils {
	public static PlayerProfile skinFromUrl(String url) {
		try {
			PlayerProfile profile = Bukkit.createPlayerProfile(UUID.randomUUID());
			profile.getTextures().setSkin(URI.create(url).toURL());
			return profile;
		} catch (IllegalArgumentException | MalformedURLException e) {
			throw new IllegalArgumentException("Invalid pet texture URL: " + url, e);
		}
	}
}
