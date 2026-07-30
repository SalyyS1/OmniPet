package io.github.salyvn.omnipet.impl.item;

import java.time.Duration;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.salyvn.omnipet.api.item.PetItem;
import io.github.salyvn.omnipet.utils.Codecs;
import io.github.salyvn.omnipet.utils.ItemTemplate;

public record PetHatcherItemImpl(ItemTemplate template, Duration duration) implements PetItem.Hatcher {
	public static final MapCodec<PetHatcherItemImpl> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			ItemTemplate.MAP_CODEC.forGetter(PetHatcherItemImpl::template),
			Codecs.DURATION.fieldOf("duration").forGetter(PetHatcherItemImpl::duration))
			.apply(i, PetHatcherItemImpl::new));
	public static final Codec<PetHatcherItemImpl> CODEC = MAP_CODEC.codec();

	public ItemStack build(PetItemKeys keys, String id) {
		ItemStack stack = template.create();
		ItemMeta meta = stack.getItemMeta();
		meta.getPersistentDataContainer().set(keys.hatcher(), PersistentDataType.STRING, id);
		stack.setItemMeta(meta);
		return stack;
	}

	public static String getHatcherId(ItemStack stack, PetItemKeys keys) {
		return stack.getPersistentDataContainer().get(keys.hatcher(), PersistentDataType.STRING);
	}
}
