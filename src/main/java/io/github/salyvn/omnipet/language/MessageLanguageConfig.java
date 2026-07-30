package io.github.salyvn.omnipet.language;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

import org.bukkit.plugin.java.JavaPlugin;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.salyvn.omnipet.OmniPetPlugin;
import io.github.salyvn.omnipet.api.EggType;
import io.github.salyvn.omnipet.api.Pet;
import io.github.salyvn.omnipet.impl.component.GeneralComponent;
import io.github.salyvn.omnipet.utils.Codecs;
import io.github.salyvn.omnipet.utils.ParseUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public record MessageLanguageConfig(
		List<String> summon,
		List<String> recall,
		List<String> summonAndRecall,
		List<Component> storageFull,
		List<String> beginHatching,
		List<String> hatched,
		List<String> released,
		LevelingMessageLanguageConfig leveling,
		StaminaMessageLanguageConfig stamina) {
	public static final MapCodec<MessageLanguageConfig> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			Codec.list(Codec.STRING).optionalFieldOf("summon", Collections.emptyList()).forGetter(MessageLanguageConfig::summon),
			Codec.list(Codec.STRING).optionalFieldOf("recall", Collections.emptyList()).forGetter(MessageLanguageConfig::recall),
			Codec.list(Codec.STRING).optionalFieldOf("summonAndRecall", Collections.emptyList()).forGetter(MessageLanguageConfig::summonAndRecall),
			Codec.list(Codecs.MINIMESSAGE).optionalFieldOf("storageFull", Collections.emptyList()).forGetter(MessageLanguageConfig::storageFull),
			Codec.list(Codec.STRING).optionalFieldOf("beginHatching", Collections.emptyList()).forGetter(MessageLanguageConfig::beginHatching),
			Codec.list(Codec.STRING).optionalFieldOf("hatched", Collections.emptyList()).forGetter(MessageLanguageConfig::hatched),
			Codec.list(Codec.STRING).optionalFieldOf("released", Collections.emptyList()).forGetter(MessageLanguageConfig::released),
			LevelingMessageLanguageConfig.CODEC.fieldOf("leveling").forGetter(MessageLanguageConfig::leveling),
			StaminaMessageLanguageConfig.CODEC.fieldOf("stamina").forGetter(MessageLanguageConfig::stamina))
			.apply(i, MessageLanguageConfig::new));
	public static final Codec<MessageLanguageConfig> CODEC = MAP_CODEC.codec();

	public static Component petName(Pet pet) {
		GeneralComponent general = pet.component(GeneralComponent.class);
		return general != null
				? general.name()
				: Component.text(JavaPlugin.getPlugin(OmniPetPlugin.class).pets().inverse().get(pet.type()));
	}

	public List<Component> getSummon(Pet pet) {
		return summon.stream()
				.map(s -> MiniMessage.miniMessage().deserialize(
						s,
						Placeholder.component("pet_name", petName(pet))))
				.toList();
	}

	public List<Component> getRecall(Pet pet) {
		return recall.stream()
				.map(s -> MiniMessage.miniMessage().deserialize(
						s,
						Placeholder.component("pet_name", petName(pet))))
				.toList();
	}

	public List<Component> getSummonAndRecall(Pet summoned, Pet recalled) {
		return summonAndRecall.stream()
				.map(s -> MiniMessage.miniMessage().deserialize(
						s,
						Placeholder.component("summoned_pet", petName(summoned)),
						Placeholder.component("recalled_pet", petName(recalled))))
				.toList();
	}

	public List<Component> getBeginHatching(EggType eggType, Duration duration) {
		return beginHatching.stream()
				.map(s -> MiniMessage.miniMessage().deserialize(
						s,
						Placeholder.component("egg_name", eggType.name()),
						Placeholder.unparsed("egg_hatch_duration", ParseUtils.toString(duration))))
				.toList();
	}

	public List<Component> getHatched(EggType eggType, Pet pet) {
		return hatched.stream()
				.map(s -> MiniMessage.miniMessage().deserialize(
						s,
						Placeholder.component("egg_name", eggType.name()),
						Placeholder.component("pet_name", petName(pet))))
				.toList();
	}

	public List<Component> getReleased(Pet pet) {
		return released.stream()
				.map(s -> MiniMessage.miniMessage().deserialize(
						s,
						Placeholder.component("pet_name", petName(pet))))
				.toList();
	}
}
