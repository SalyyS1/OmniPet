package io.github.salyvn.omnipet.impl.item;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.salyvn.omnipet.api.item.PetItem;
import io.github.salyvn.omnipet.utils.ItemTemplate;

public record PetFoodItemImpl(ItemTemplate template, double stamina) implements PetItem.Food {
	public static final MapCodec<PetFoodItemImpl> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			ItemTemplate.MAP_CODEC.forGetter(PetFoodItemImpl::template),
			Codec.DOUBLE.fieldOf("stamina").forGetter(PetFoodItemImpl::stamina))
			.apply(i, PetFoodItemImpl::new));
	public static final Codec<PetFoodItemImpl> CODEC = MAP_CODEC.codec();

	public ItemStack build(PetItemKeys keys, String id) {
		ItemStack stack = template.create();
		ItemMeta meta = stack.getItemMeta();
		meta.getPersistentDataContainer().set(keys.food(), PersistentDataType.STRING, id);
		stack.setItemMeta(meta);
		return stack;
	}

	public static String getFoodId(ItemStack stack, PetItemKeys keys) {
		return stack.getPersistentDataContainer().get(keys.food(), PersistentDataType.STRING);
	}
}
