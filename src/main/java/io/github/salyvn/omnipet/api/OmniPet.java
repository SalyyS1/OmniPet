package io.github.salyvn.omnipet.api;

import java.util.UUID;
import java.util.function.Function;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.google.common.collect.BiMap;
import com.mojang.serialization.Codec;

import io.github.salyvn.omnipet.api.item.ItemIdentifier;
import io.github.salyvn.omnipet.api.item.PetItem;
import io.github.nahkd123.tinyexpr.Value;

public interface OmniPet {
	BiMap<String, Codec<? extends PetComponent.Config<?>>> components();
	BiMap<String, ? extends PetType> pets();
	BiMap<String, ? extends EggType> eggs();

	<T extends PetComponent> void registerComponent(String id, Codec<? extends PetComponent.Config<T>> codec);
	void registerItemIdentifier(ItemIdentifier identifier);
	<T extends ScopeSource> void registerProvider(String id, Class<T> type, Function<T, Value> provider);
	void reloadData();

	PetPlayer player(UUID uuid);
	default PetPlayer player(Player player) { return player(player.getUniqueId()); }
	PetPlayer loadData(UUID uuid);
	void saveData(PetPlayer data);

	PetItem identifyItem(ItemStack stack);
}
