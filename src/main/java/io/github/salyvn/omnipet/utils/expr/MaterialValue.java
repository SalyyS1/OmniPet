package io.github.salyvn.omnipet.utils.expr;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import io.github.nahkd123.tinyexpr.Value;

public class MaterialValue implements Value {
	public static final MaterialValue VALUE = new MaterialValue();

	private MaterialValue() {}

	@Override
	public Value get(String name) {
		try {
			Material mat = Material.valueOf(name);
			return new ItemStackValue(new ItemStack(mat));
		} catch (IllegalArgumentException e) {
			return Value.super.get(name);
		}
	}

	@Override
	public String toString() {
		return "[Materials]";
	}
}
