package io.github.salyvn.omnipet.impl;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record OmniPetConfig(int globalMaxSlots, String slotPermission, String[] slotPermList) {
	public static final MapCodec<OmniPetConfig> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("globalMaxSlots", 1000).forGetter(OmniPetConfig::globalMaxSlots),
			Codec.STRING.optionalFieldOf("slotPermission", "petstorage.slot.%s").forGetter(OmniPetConfig::slotPermission))
			.apply(i, OmniPetConfig::new));
	public static final Codec<OmniPetConfig> CODEC = MAP_CODEC.codec();

	public OmniPetConfig(int globalMaxSlots, String slotPermission) {
		this(globalMaxSlots, slotPermission, new String[globalMaxSlots]);
		for (int i = 0; i < globalMaxSlots; i++) slotPermList[i] = slotPermission.formatted(i + 1);
	}
}
