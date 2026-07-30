package io.github.salyvn.omnipet.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.bukkit.plugin.java.JavaPlugin;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

import io.github.salyvn.omnipet.OmniPetPlugin;
import io.github.salyvn.omnipet.api.LoreProvider;
import io.github.salyvn.omnipet.api.Pet;
import io.github.salyvn.omnipet.api.PetComponent;
import io.github.salyvn.omnipet.api.ScopeSource;
import io.github.salyvn.omnipet.utils.Codecs;
import io.github.nahkd123.tinyexpr.Value;
import io.github.nahkd123.tinyexpr.impl.MapValue;
import io.github.nahkd123.tinyexpr.impl.NullValue;

public record PetImpl(
		PetTypeImpl type,
		Map<Class<? extends PetComponent>, PetComponent> classToComponent,
		Map<String, PetComponent> idToComponent) implements Pet {
	public static final MapCodec<PetImpl> MAP_CODEC = Codecs.PET_ID
			.xmap(p -> (PetTypeImpl) p, p -> p)
			.dispatchMap("type", PetImpl::type, t -> t.componentsCodec()
					.xmap(map -> PetImpl.of(t, map), pet -> pet.idToComponent)
					.fieldOf("components"));
	public static final Codec<PetImpl> CODEC = MAP_CODEC.codec();

	public static PetImpl of(PetTypeImpl type, Map<String, PetComponent> idToComponent) {
		idToComponent = new HashMap<>(idToComponent);

		for (String id : type.idToConfig().keySet()) {
			if (idToComponent.containsKey(id)) continue;
			idToComponent.put(id, type.idToConfig().get(id).createDefaultState());
		}

		// Include both persisted and defaulted components in class-based lookups.
		Map<Class<? extends PetComponent>, PetComponent> classToComponent = new HashMap<>();
		for (PetComponent component : idToComponent.values()) classToComponent.put(component.getClass(), component);

		return new PetImpl(type, classToComponent, idToComponent);
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T extends PetComponent> T component(Class<T> type) {
		return (T) classToComponent.get(type);
	}

	@Override
	public LoreProvider lore(String id) {
		PetComponent component = idToComponent.get(id);
		return component instanceof LoreProvider provider ? provider : null;
	}

	@Override
	public Function<String, Value> evalContext() {
		OmniPetPlugin plugin = JavaPlugin.getPlugin(OmniPetPlugin.class);
		Map<String, Value> map = Stream.concat(
				plugin.forScope(new ScopeSource.Pet(this)).stream(),
				plugin.forScope(new ScopeSource.Global()).stream())
				.collect(Collectors.toMap(Pair::getFirst, Pair::getSecond));

		return name -> switch (name) {
		case "math" -> MapValue.MATH;
		default -> map.containsKey(name) ? map.get(name)
				: idToComponent.get(name) instanceof Value v ? v
				: NullValue.NULL;
		};
	}
}
