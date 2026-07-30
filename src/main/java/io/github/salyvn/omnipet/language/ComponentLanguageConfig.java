package io.github.salyvn.omnipet.language;

import java.util.Collections;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record ComponentLanguageConfig(List<String> leveling, List<String> stamina) {
	public static final MapCodec<ComponentLanguageConfig> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			Codec.list(Codec.STRING).optionalFieldOf("leveling", Collections.emptyList()).forGetter(ComponentLanguageConfig::leveling),
			Codec.list(Codec.STRING).optionalFieldOf("stamina", Collections.emptyList()).forGetter(ComponentLanguageConfig::stamina))
			.apply(i, ComponentLanguageConfig::new));
	public static final Codec<ComponentLanguageConfig> CODEC = MAP_CODEC.codec();
}
