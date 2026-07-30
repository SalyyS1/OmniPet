package io.github.salyvn.omnipet.thirdparty.mmoitems.expr;

import org.bukkit.inventory.ItemStack;

import io.github.nahkd123.tinyexpr.Value;
import net.Indyuce.mmoitems.api.item.build.MMOItemBuilder;

public record MMOItemBuilderValue(MMOItemBuilder builder) implements Value {
	@Override
	public Object unwrap() {
		return builder;
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> T unwrapAs(Class<T> type) {
		if (type == ItemStack.class) return (T) builder.build().newBuilder().build();
		return Value.super.unwrapAs(type);
	}
}
