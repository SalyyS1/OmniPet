package io.github.salyvn.omnipet.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

public record ItemTemplate(Material type, String name, List<String> lore, Boolean tooltip) {
	public static final MapCodec<ItemTemplate> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			Codecs.MATERIAL.optionalFieldOf("type").forGetter(t -> Optional.ofNullable(t.type)),
			Codec.STRING.optionalFieldOf("name").forGetter(t -> Optional.ofNullable(t.name)),
			Codec.list(Codec.STRING).optionalFieldOf("lore").forGetter(t -> Optional.ofNullable(t.lore)),
			Codec.BOOL.optionalFieldOf("tooltip").forGetter(t -> Optional.ofNullable(t.tooltip)))
			.apply(i, ItemTemplate::new));
	public static final Codec<ItemTemplate> CODEC = MAP_CODEC.codec();

	public ItemTemplate(Optional<Material> type, Optional<String> name, Optional<List<String>> lore, Optional<Boolean> tooltip) {
		this(type.orElse(null), name.orElse(null), lore.orElse(null), tooltip.orElse(null));
	}

	public ItemStack create(MiniMessage mm, TagResolver[] resolvers, Function<String, List<Component>> loreProvider) {
		TagResolver[] r = resolvers != null ? resolvers : new TagResolver[0];
		ItemStack stack = new ItemStack(type != null ? type : Material.STONE);
		ItemMeta meta = stack.getItemMeta();
		if (name != null) meta.itemName(mm.deserialize(name, r));
		if (tooltip != null && !tooltip) meta.setHideTooltip(true);

		if (lore != null) {
			List<Component> content = new ArrayList<>(lore.size());

			for (String line : lore) {
				if (line.startsWith("{{ ") && line.endsWith(" }}")) {
					if (loreProvider != null) {
						List<Component> provided = loreProvider.apply(line.substring(3, line.length() - 3));
						if (provided != null) content.addAll(provided);
					}
				} else content.add(mm.deserialize(line, r));
			}

			meta.lore(content);
		}

		stack.setItemMeta(meta);
		return stack;
	}

	public ItemStack create(MiniMessage mm) {
		return create(mm, null, null);
	}

	public ItemStack create() {
		return create(MiniMessage.miniMessage(), null, null);
	}
}
