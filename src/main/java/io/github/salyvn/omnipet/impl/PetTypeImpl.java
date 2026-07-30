package io.github.salyvn.omnipet.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import com.mojang.serialization.Codec;

import io.github.salyvn.omnipet.api.Pet;
import io.github.salyvn.omnipet.api.PetComponent;
import io.github.salyvn.omnipet.api.PetType;
import io.github.salyvn.omnipet.api.PetComponent.Config;
import io.github.salyvn.omnipet.utils.Codecs;

public record PetTypeImpl(
		Map<Class<? extends PetComponent.Config<?>>, PetComponent.Config<?>> classToConfig,
		Map<String, PetComponent.Config<?>> idToConfig,
		Codec<Map<String, PetComponent>> componentsCodec) implements PetType {
	public static final Codec<PetTypeImpl> CODEC = Codecs.PET_COMPONENTS.xmap(PetTypeImpl::of, PetTypeImpl::idToConfig);

	@SuppressWarnings("unchecked")
	public static PetTypeImpl of(Map<String, PetComponent.Config<?>> idToConfig) {
		Map<Class<? extends Config<?>>, Config<?>> classToConfig = idToConfig.entrySet()
				.stream()
				.collect(Collectors.toMap(
						e -> (Class<? extends PetComponent.Config<?>>) e.getValue().getClass(),
						e -> e.getValue()));
		Codec<Map<String, PetComponent>> componentsCodec = Codec.dispatchedMap(
				Codec.STRING,
				id -> idToConfig.get(id).stateCodec());
		return new PetTypeImpl(classToConfig, idToConfig, componentsCodec);
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T extends PetComponent.Config<?>> T config(Class<T> configType) {
		return (T) classToConfig.get(configType);
	}

	@Override
	public Pet createDefault() {
		Map<String, PetComponent> idToComponent = new HashMap<>();
		Map<Class<? extends PetComponent>, PetComponent> classToComponent = new HashMap<>();

		for (Map.Entry<String, PetComponent.Config<?>> entry : idToConfig.entrySet()) {
			PetComponent component = entry.getValue().createDefaultState();
			idToComponent.put(entry.getKey(), component);
			classToComponent.put(component.getClass(), component);
		}

		return new PetImpl(this, classToComponent, idToComponent);
	}
}
