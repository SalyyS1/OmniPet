package io.github.salyvn.omnipet.api;

import java.time.Duration;
import java.util.List;

import net.kyori.adventure.text.Component;

public interface EggType {
	Component name();
	Duration hatchDuration();

	/**
	 * <p>Get the rarity of this egg. The rarity value will be associated with the pet when it hatched
	 * from this egg, as long as pet type have {@code hatching} component. Use this to generate table
	 * of stats for example.</p>
	 * @return The egg rarity level.
	 */
	double rarity();

	/**
	 * <p>Get all possible pets that can be obtained from hatching this egg.</p>
	 * @return A list of possible pets.
	 */
	List<PetType> pets();
}
