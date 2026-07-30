package io.github.salyvn.omnipet.gui;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.salyvn.omnipet.utils.Codecs;
import io.github.salyvn.omnipet.utils.ItemTemplate;
import net.kyori.adventure.text.Component;

public record GuiConfig(
		Component title,
		int size,
		ItemTemplate border,
		ItemTemplate emptySlot,
		ItemTemplate lockedSlot,
		ItemTemplate voidSlot,
		ItemTemplate egg,
		ItemTemplate pet,
		ItemTemplate activePet,
		ItemTemplate nextPage,
		ItemTemplate prevPage) {
	public GuiConfig {
		if (!isSupportedSize(size)) throw new IllegalArgumentException("GUI size must be one of 27, 36, 45, or 54");
	}

	public static final MapCodec<GuiConfig> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			Codecs.MINIMESSAGE.optionalFieldOf("title", Component.text("Pets")).forGetter(GuiConfig::title),
			Codec.INT.comapFlatMap(size -> isSupportedSize(size)
					? DataResult.success(size)
					: DataResult.error(() -> "GUI size must be one of 27, 36, 45, or 54"), size -> size)
					.optionalFieldOf("size", 54).forGetter(GuiConfig::size),
			ItemTemplate.CODEC.fieldOf("border").forGetter(GuiConfig::border),
			ItemTemplate.CODEC.fieldOf("emptySlot").forGetter(GuiConfig::emptySlot),
			ItemTemplate.CODEC.fieldOf("lockedSlot").forGetter(GuiConfig::lockedSlot),
			ItemTemplate.CODEC.fieldOf("voidSlot").forGetter(GuiConfig::voidSlot),
			ItemTemplate.CODEC.fieldOf("egg").forGetter(GuiConfig::egg),
			ItemTemplate.CODEC.fieldOf("pet").forGetter(GuiConfig::pet),
			ItemTemplate.CODEC.fieldOf("activePet").forGetter(GuiConfig::activePet),
			ItemTemplate.CODEC.fieldOf("nextPage").forGetter(GuiConfig::nextPage),
			ItemTemplate.CODEC.fieldOf("prevPage").forGetter(GuiConfig::prevPage))
			.apply(i, GuiConfig::new));
	public static final Codec<GuiConfig> CODEC = MAP_CODEC.codec();

	private static boolean isSupportedSize(int size) {
		return size == 27 || size == 36 || size == 45 || size == 54;
	}
}
