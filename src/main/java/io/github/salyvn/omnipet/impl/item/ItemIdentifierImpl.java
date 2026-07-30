package io.github.salyvn.omnipet.impl.item;

import java.util.function.Supplier;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import io.github.salyvn.omnipet.api.EggType;
import io.github.salyvn.omnipet.api.OmniPet;
import io.github.salyvn.omnipet.api.item.ItemIdentifier;
import io.github.salyvn.omnipet.api.item.PetItem;

public record ItemIdentifierImpl(Supplier<PetItemConfig> config, PetItemKeys keys) implements ItemIdentifier {
	@Override
	public PetItem identify(ItemStack stack, OmniPet api) {
		if (stack == null || stack.getType() == Material.AIR) return null;
		PetItem item = identify(stack, api, keys);
		return item != null ? item : identify(stack, api, PetItemKeys.legacy());
	}

	private PetItem identify(ItemStack stack, OmniPet api, PetItemKeys itemKeys) {
		if (stack == null || stack.getType() == Material.AIR) return null;

		if (PetEvolverItemImpl.isEvolver(stack, itemKeys)) return config.get().evolver();

		String foodId = PetFoodItemImpl.getFoodId(stack, itemKeys);
		if (foodId != null) return config.get().foods().get(foodId);

		String hatcherId = PetHatcherItemImpl.getHatcherId(stack, itemKeys);
		if (hatcherId != null) return config.get().hatchers().get(hatcherId);

		EggType eggType = EggItemImpl.getEggType(stack, itemKeys, api);
		if (eggType != null) return new EggItemImpl.Wrapper(eggType);

		return null;
	}
}
