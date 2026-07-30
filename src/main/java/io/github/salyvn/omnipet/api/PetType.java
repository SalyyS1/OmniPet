package io.github.salyvn.omnipet.api;

public interface PetType {
	<T extends PetComponent.Config<?>> T config(Class<T> configType);
	Pet createDefault();
}
