package io.github.salyvn.omnipet.api.item;

import java.time.Duration;

import io.github.salyvn.omnipet.api.EggType;

public sealed interface PetItem {
	non-sealed interface Food extends PetItem { double stamina(); }
	non-sealed interface Egg extends PetItem { EggType egg(); }
	non-sealed interface Hatcher extends PetItem { Duration duration(); }
	non-sealed interface Evolver extends PetItem {}
}
