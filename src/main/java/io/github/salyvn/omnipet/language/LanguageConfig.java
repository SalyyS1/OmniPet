package io.github.salyvn.omnipet.language;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record LanguageConfig(HudLanguageConfig hud, MessageLanguageConfig messages, ComponentLanguageConfig components) {
	public static final MapCodec<LanguageConfig> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			HudLanguageConfig.CODEC.fieldOf("hud").forGetter(LanguageConfig::hud),
			MessageLanguageConfig.CODEC.fieldOf("messages").forGetter(LanguageConfig::messages),
			ComponentLanguageConfig.CODEC.fieldOf("components").forGetter(LanguageConfig::components))
			.apply(i, LanguageConfig::new));
	public static final Codec<LanguageConfig> CODEC = MAP_CODEC.codec();
}
