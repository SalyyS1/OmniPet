package io.github.salyvn.omnipet.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class AdvtrUtils {
	private AdvtrUtils() {}

	public static Component progressBar(int count, String symbol, double progress) {
		progress = Math.clamp(progress, 0, 1);
		int progressed = (int) (count * progress);
		int pending = count - progressed;
		return Component.text()
				.append(Component.text(symbol.repeat(progressed)).color(NamedTextColor.YELLOW))
				.append(Component.text(symbol.repeat(pending)).color(NamedTextColor.DARK_GRAY))
				.build();
	}
}
