package io.github.salyvn.omnipet.language;

import java.util.Collections;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.salyvn.omnipet.api.Pet;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public record LevelingMessageLanguageConfig(List<String> levelUp, List<String> evolve) {
	public static final MapCodec<LevelingMessageLanguageConfig> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			Codec.list(Codec.STRING).optionalFieldOf("levelUp", Collections.emptyList()).forGetter(LevelingMessageLanguageConfig::levelUp),
			Codec.list(Codec.STRING).optionalFieldOf("evolve", Collections.emptyList()).forGetter(LevelingMessageLanguageConfig::evolve))
			.apply(i, LevelingMessageLanguageConfig::new));
	public static final Codec<LevelingMessageLanguageConfig> CODEC = MAP_CODEC.codec();

	public List<Component> getLevelUp(Pet pet, int prev, int next) {
		return levelUp.stream()
				.map(s -> MiniMessage.miniMessage().deserialize(
						s,
						Placeholder.component("pet_name", MessageLanguageConfig.petName(pet)),
						Placeholder.unparsed("prev_level", Integer.toString(prev)),
						Placeholder.unparsed("next_level", Integer.toString(next))))
				.toList();
	}

	public List<Component> getEvolve(Pet pet, int prev, int next) {
		return evolve.stream()
				.map(s -> MiniMessage.miniMessage().deserialize(
						s,
						Placeholder.component("pet_name", MessageLanguageConfig.petName(pet)),
						Placeholder.unparsed("prev_evolution", Integer.toString(prev)),
						Placeholder.unparsed("next_evolution", Integer.toString(next))))
				.toList();
	}
}
