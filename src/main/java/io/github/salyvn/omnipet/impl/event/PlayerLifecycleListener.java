package io.github.salyvn.omnipet.impl.event;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import io.github.salyvn.omnipet.OmniPetPlugin;

public class PlayerLifecycleListener implements Listener {
	private OmniPetPlugin plugin;

	public PlayerLifecycleListener(OmniPetPlugin plugin) {
		this.plugin = plugin;
	}

	@EventHandler
	public void onJoin(PlayerJoinEvent e) {
		plugin.loadPlayer(e.getPlayer());
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent e) {
		plugin.unloadPlayer(e.getPlayer());
	}
}
