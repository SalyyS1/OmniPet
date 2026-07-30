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

public record StaminaMessageLanguageConfig(List<String> outOfStamina, List<String> fullStamina) {
	public static final MapCodec<StaminaMessageLanguageConfig> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			Codec.list(Codec.STRING).optionalFieldOf("outOfStamina", Collections.emptyList()).forGetter(StaminaMessageLanguageConfig::outOfStamina),
			Codec.list(Codec.STRING).optionalFieldOf("fullStamina", Collections.emptyList()).forGetter(StaminaMessageLanguageConfig::fullStamina))
			.apply(i, StaminaMessageLanguageConfig::new));
	public static final Codec<StaminaMessageLanguageConfig> CODEC = MAP_CODEC.codec();

	public List<Component> getOutOfStamina(Pet pet) {
		return outOfStamina.stream()
				.map(s -> MiniMessage.miniMessage().deserialize(
						s,
						Placeholder.component("pet_name", MessageLanguageConfig.petName(pet))))
				.toList();
	}

	public List<Component> getFullStamina(Pet pet) {
		return fullStamina.stream()
				.map(s -> MiniMessage.miniMessage().deserialize(
						s,
						Placeholder.component("pet_name", MessageLanguageConfig.petName(pet))))
				.toList();
	}
}
