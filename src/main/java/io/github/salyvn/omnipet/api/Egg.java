package io.github.salyvn.omnipet.api;

import java.time.Duration;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.salyvn.omnipet.utils.Codecs;

public record Egg(EggType type, Duration timeLeft) {
	public static final MapCodec<Egg> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			Codecs.EGG_ID.fieldOf("type").forGetter(Egg::type),
			Codecs.DURATION.fieldOf("timeLeft").forGetter(Egg::timeLeft))
			.apply(i, Egg::new));
	public static final Codec<Egg> CODEC = MAP_CODEC.codec();
}
