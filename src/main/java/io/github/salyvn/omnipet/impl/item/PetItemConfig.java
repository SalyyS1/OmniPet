package io.github.salyvn.omnipet.impl.item;

import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableBiMap;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record PetItemConfig(
		BiMap<String, PetFoodItemImpl> foods,
		BiMap<String, PetHatcherItemImpl> hatchers,
		PetEvolverItemImpl evolver,
		EggItemImpl egg) {
	public static final MapCodec<PetItemConfig> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			Codec.unboundedMap(Codec.STRING, PetFoodItemImpl.CODEC).xmap(m -> (BiMap<String, PetFoodItemImpl>) ImmutableBiMap.copyOf(m), m -> m).optionalFieldOf("foods", ImmutableBiMap.of()).forGetter(PetItemConfig::foods),
			Codec.unboundedMap(Codec.STRING, PetHatcherItemImpl.CODEC).xmap(m -> (BiMap<String, PetHatcherItemImpl>) ImmutableBiMap.copyOf(m), m -> m).optionalFieldOf("hatchers", ImmutableBiMap.of()).forGetter(PetItemConfig::hatchers),
			PetEvolverItemImpl.CODEC.fieldOf("evolver").forGetter(PetItemConfig::evolver),
			EggItemImpl.CODEC.fieldOf("egg").forGetter(PetItemConfig::egg))
			.apply(i, PetItemConfig::new));
	public static final Codec<PetItemConfig> CODEC = MAP_CODEC.codec();
}
