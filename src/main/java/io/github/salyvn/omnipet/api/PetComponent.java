package io.github.salyvn.omnipet.api;

import com.mojang.serialization.Codec;

public interface PetComponent {
	default void initialize(Pet pet, PetPlayer owner) {}

	default void onSummon() {}
	default void onRecall() {}

	interface Config<T extends PetComponent> {
		Codec<T> stateCodec();
		T createDefaultState();
	}
}
