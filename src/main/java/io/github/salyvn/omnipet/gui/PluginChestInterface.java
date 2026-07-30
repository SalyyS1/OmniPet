package io.github.salyvn.omnipet.gui;

import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.InventoryHolder;

public interface PluginChestInterface extends InventoryHolder {
	void onClick(InventoryClickEvent event);
	void onClose(InventoryCloseEvent event);
	default void onRefresh() {}
}
