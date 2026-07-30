package io.github.salyvn.omnipet.api;

import java.util.function.Function;

import io.github.nahkd123.tinyexpr.Value;

public interface Pet {
	PetType type();
	<T extends PetComponent> T component(Class<T> type);
	LoreProvider lore(String id);
	Function<String, Value> evalContext();
}
