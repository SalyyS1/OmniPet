package io.github.salyvn.omnipet.impl.item;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

import io.github.salyvn.omnipet.api.item.PetItem;
import io.github.salyvn.omnipet.utils.ItemTemplate;

public record PetEvolverItemImpl(ItemTemplate template) implements PetItem.Evolver {
	public static final MapCodec<PetEvolverItemImpl> MAP_CODEC = ItemTemplate.MAP_CODEC.xmap(PetEvolverItemImpl::new, PetEvolverItemImpl::template);
	public static final Codec<PetEvolverItemImpl> CODEC = MAP_CODEC.codec();

	public ItemStack build(PetItemKeys keys) {
		ItemStack stack = template.create();
		ItemMeta meta = stack.getItemMeta();
		meta.getPersistentDataContainer().set(keys.evolver(), PersistentDataType.BOOLEAN, true);
		stack.setItemMeta(meta);
		return stack;
	}

	public static boolean isEvolver(ItemStack stack, PetItemKeys keys) {
		return stack.getPersistentDataContainer().getOrDefault(keys.evolver(), PersistentDataType.BOOLEAN, false);
	}
}
