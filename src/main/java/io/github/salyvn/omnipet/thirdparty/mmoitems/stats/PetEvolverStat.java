package io.github.salyvn.omnipet.thirdparty.mmoitems.stats;

import org.bukkit.Material;

import net.Indyuce.mmoitems.stat.type.BooleanStat;

public class PetEvolverStat extends BooleanStat {
	public PetEvolverStat() {
		this("OMNIPET_PET_EVOLVER");
	}

	public PetEvolverStat(String id) {
		super(
				id,
				Material.BEACON,
				"Pet Evolve",
				new String[] { "Evolve the pet when consuming the item" },
				new String[] { "all" });
	}
}
