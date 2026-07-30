package io.github.salyvn.omnipet.utils.expr;

import java.util.stream.Stream;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import io.github.nahkd123.tinyexpr.Value;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

public record ItemStackValue(ItemStack stack) implements Value {
	private class WithAmount implements Value {
		@Override
		public Value call(Value[] params) {
			stack.setAmount(params[0].unwrapAs(int.class));
			return ItemStackValue.this;
		}
	}

	private class WithName implements Value {
		@Override
		public Value call(Value[] params) {
			Component name = MiniMessage.miniMessage().deserialize(params[0].unwrapAs(String.class));
			ItemMeta meta = stack.getItemMeta();
			meta.itemName(name);
			stack.setItemMeta(meta);
			return ItemStackValue.this;
		}
	}

	private class WithLore implements Value {
		@Override
		public Value call(Value[] params) {
			MiniMessage mm = MiniMessage.miniMessage();
			ItemMeta meta = stack.getItemMeta();
			meta.lore(Stream.of(params)
					.map(v -> mm.deserialize(v.unwrapAs(String.class)))
					.toList());
			stack.setItemMeta(meta);
			return ItemStackValue.this;
		}
	}

	@Override
	public Value get(String name) {
		return switch (name) {
		case "withAmount" -> new WithAmount();
		case "withName" -> new WithName();
		case "withLore" -> new WithLore();
		default -> Value.super.get(name);
		};
	}

	@Override
	public Object unwrap() {
		return stack;
	}
}
