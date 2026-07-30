package io.github.salyvn.omnipet.thirdparty.mmoitems.stats;

import org.bukkit.Material;

import net.Indyuce.mmoitems.stat.type.DoubleStat;

public class PetFoodStat extends DoubleStat {
	public PetFoodStat() {
		this("OMNIPET_PET_FOOD");
	}

	public PetFoodStat(String id) {
		super(
				id,
				Material.COOKED_BEEF,
				"Pet Food Stamina Amount",
				new String[] {
						"The amount of stamina to regain when",
						"feeding pet with this item.",
						"",
						"Use 0 to disable."
				},
				new String[] { "all" },
				true);
	}
}
