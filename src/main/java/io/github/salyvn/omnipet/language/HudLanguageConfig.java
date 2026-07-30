package io.github.salyvn.omnipet.language;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.salyvn.omnipet.api.Egg;
import io.github.salyvn.omnipet.utils.ParseUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

public record HudLanguageConfig(String hatchingBossbar) {
	public static final MapCodec<HudLanguageConfig> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			Codec.STRING.optionalFieldOf("hatchingBossbar", "<egg_name> - <egg_hatch_duration>").forGetter(HudLanguageConfig::hatchingBossbar))
			.apply(i, HudLanguageConfig::new));
	public static final Codec<HudLanguageConfig> CODEC = MAP_CODEC.codec();

	public Component getHatchingBossbar(Egg egg) {
		return MiniMessage.miniMessage().deserialize(hatchingBossbar, new TagResolver[] {
				Placeholder.component("egg_name", egg.type().name()),
				Placeholder.unparsed("egg_hatch_duration", ParseUtils.toString(egg.timeLeft()))
		});
	}
}
