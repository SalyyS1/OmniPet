package io.github.salyvn.omnipet.thirdparty;

import io.github.salyvn.omnipet.api.OmniPet;

public interface ThirdPartyHook {
	default void onLoad(OmniPet api) {}
	default void onEnable(OmniPet api) {}
	default void onDisable(OmniPet api) {}
}
