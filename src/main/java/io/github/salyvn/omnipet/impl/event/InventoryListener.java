package io.github.salyvn.omnipet.impl.event;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;

import io.github.salyvn.omnipet.gui.PluginChestInterface;

public class InventoryListener implements Listener {
	@EventHandler
	public void onClick(InventoryClickEvent event) {
		if (event.getInventory().getHolder() instanceof PluginChestInterface ui) ui.onClick(event);
	}

	@EventHandler
	public void onClose(InventoryCloseEvent event) {
		if (event.getInventory().getHolder() instanceof PluginChestInterface ui) ui.onClose(event);
	}
}
