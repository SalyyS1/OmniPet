package io.github.salyvn.omnipet.impl.item;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

public record PetItemKeys(NamespacedKey food, NamespacedKey evolver, NamespacedKey egg, NamespacedKey hatcher) {
	public PetItemKeys(Plugin plugin) {
		this(
				new NamespacedKey(plugin, "food"),
				new NamespacedKey(plugin, "evolver"),
				new NamespacedKey(plugin, "egg"),
				new NamespacedKey(plugin, "hatcher"));
	}

	/** Read-only keys written by legacy releases so existing items remain usable. */
	public static PetItemKeys legacy() {
		return new PetItemKeys(
				NamespacedKey.fromString("passivepet:food"),
				NamespacedKey.fromString("passivepet:evolver"),
				NamespacedKey.fromString("passivepet:egg"),
				NamespacedKey.fromString("passivepet:hatcher"));
	}
}
