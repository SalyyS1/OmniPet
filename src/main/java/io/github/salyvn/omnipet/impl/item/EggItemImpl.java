package io.github.salyvn.omnipet.impl.item;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

import io.github.salyvn.omnipet.api.EggType;
import io.github.salyvn.omnipet.api.OmniPet;
import io.github.salyvn.omnipet.api.item.PetItem;
import io.github.salyvn.omnipet.utils.ItemTemplate;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

public record EggItemImpl(ItemTemplate template) {
	public static final MapCodec<EggItemImpl> MAP_CODEC = ItemTemplate.MAP_CODEC.xmap(EggItemImpl::new, EggItemImpl::template);
	public static final Codec<EggItemImpl> CODEC = MAP_CODEC.codec();

	public ItemStack build(PetItemKeys keys, EggType type, OmniPet api) {
		ItemStack stack = template.create(MiniMessage.miniMessage(), new TagResolver[] { Placeholder.component("egg_name", type.name()) }, null);
		ItemMeta meta = stack.getItemMeta();
		meta.getPersistentDataContainer().set(keys.egg(), PersistentDataType.STRING, api.eggs().inverse().get(type));
		stack.setItemMeta(meta);
		return stack;
	}

	public static EggType getEggType(ItemStack stack, PetItemKeys keys, OmniPet api) {
		String id = stack.getPersistentDataContainer().get(keys.egg(), PersistentDataType.STRING);
		return id != null ? api.eggs().get(id) : null;
	}

	public static record Wrapper(EggType egg) implements PetItem.Egg {}
}
