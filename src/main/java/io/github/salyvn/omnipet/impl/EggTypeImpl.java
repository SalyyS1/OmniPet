package io.github.salyvn.omnipet.impl;

import java.time.Duration;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.salyvn.omnipet.api.EggType;
import io.github.salyvn.omnipet.api.PetType;
import io.github.salyvn.omnipet.utils.Codecs;
import net.kyori.adventure.text.Component;

public record EggTypeImpl(Component name, Duration hatchDuration, double rarity, List<PetType> pets) implements EggType {
	public static final MapCodec<EggTypeImpl> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			Codecs.MINIMESSAGE.fieldOf("name").forGetter(EggTypeImpl::name),
			Codecs.DURATION.fieldOf("duration").forGetter(EggTypeImpl::hatchDuration),
			Codec.DOUBLE.optionalFieldOf("rarity", 0d).forGetter(EggTypeImpl::rarity),
			Codec.list(Codecs.PET_ID).fieldOf("pets").forGetter(EggTypeImpl::pets))
			.apply(i, EggTypeImpl::new));
	public static final Codec<EggTypeImpl> CODEC = MAP_CODEC.codec();
}
