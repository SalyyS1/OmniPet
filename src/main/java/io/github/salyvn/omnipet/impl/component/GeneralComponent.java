package io.github.salyvn.omnipet.impl.component;

import java.util.Collections;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.salyvn.omnipet.api.LoreProvider;
import io.github.salyvn.omnipet.api.PetComponent;
import io.github.salyvn.omnipet.utils.Codecs;
import net.kyori.adventure.text.Component;

public class GeneralComponent implements PetComponent, LoreProvider {
	private Config config;

	public GeneralComponent(Config config) {
		this.config = config;
	}

	@Override
	public List<Component> provideLore() {
		return config.description;
	}

	public Component name() {
		return config.name;
	}

	public String texture() {
		return config.texture.startsWith("https://textures.minecraft.net/texture/") ? config.texture : null;
	}

	public List<Component> description() {
		return config.description;
	}

	public static record Config(Component name, String texture, List<Component> description) implements PetComponent.Config<GeneralComponent> {
		public static final MapCodec<Config> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
				Codecs.MINIMESSAGE.optionalFieldOf("name", Component.text("Pet")).forGetter(Config::name),
				Codec.STRING.optionalFieldOf("texture", "").forGetter(Config::texture),
				Codec.list(Codecs.MINIMESSAGE).optionalFieldOf("description", Collections.emptyList()).forGetter(Config::description))
				.apply(i, Config::new));
		public static final Codec<Config> CODEC = MAP_CODEC.codec();

		@Override
		public Codec<GeneralComponent> stateCodec() {
			return Codec.unit(this::createDefaultState);
		}

		@Override
		public GeneralComponent createDefaultState() {
			return new GeneralComponent(this);
		}
	}
}
