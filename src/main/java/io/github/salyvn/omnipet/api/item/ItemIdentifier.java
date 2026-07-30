package io.github.salyvn.omnipet.api.item;

import org.bukkit.inventory.ItemStack;

import io.github.salyvn.omnipet.api.OmniPet;

public interface ItemIdentifier {
	PetItem identify(ItemStack stack, OmniPet api);
}
