package io.github.salyvn.omnipet;

import io.github.salyvn.omnipet.command.OmniPetCommands;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;

public class OmniPetBootstrap implements PluginBootstrap {
	@Override
	public void bootstrap(BootstrapContext context) {
		context.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, e -> {
			Commands registrar = e.registrar();
			registrar.getDispatcher().register(OmniPetCommands.root("pets"));
			registrar.getDispatcher().register(OmniPetCommands.root("pet"));
		});
	}
}
