package io.github.salyvn.omnipet.thirdparty.mythiclib;

import io.github.salyvn.omnipet.api.OmniPet;
import io.github.salyvn.omnipet.api.ScopeSource;
import io.github.salyvn.omnipet.thirdparty.ThirdPartyHook;

public class MythicLibHook implements ThirdPartyHook {
	@Override
	public void onLoad(OmniPet api) {
		api.registerComponent("mythiclibBuffs", MythicLibBuffComponent.Config.CODEC);
		api.registerProvider("mythiclib", ScopeSource.Global.class, s -> MythicLibValue.FACTORY);
	}
}
