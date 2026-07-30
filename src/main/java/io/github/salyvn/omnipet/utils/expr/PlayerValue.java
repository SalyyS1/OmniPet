package io.github.salyvn.omnipet.utils.expr;

import java.util.HashMap;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import io.github.nahkd123.tinyexpr.Value;
import io.github.nahkd123.tinyexpr.impl.NullValue;

public record PlayerValue(Player player) implements Value {
	private class GiveItem implements Value {
		@Override
		public Value call(Value[] params) {
			ItemStack stack = params[0].unwrapAs(ItemStack.class);
			HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
			leftover.values().forEach(s -> player.getWorld().dropItem(player.getLocation().add(0, 0.5, 0), stack));
			return NullValue.NULL;
		}
	}

	@Override
	public Value get(String name) {
		return switch (name) {
		case "giveItem" -> new GiveItem();
		default -> Value.super.get(name);
		};
	}

	@Override
	public Object unwrap() {
		return player;
	}

	@Override
	public final String toString() {
		return "[Player %s]".formatted(player.getName());
	}
}
