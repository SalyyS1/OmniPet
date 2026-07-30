package io.github.salyvn.omnipet.thirdparty.mmoitems.stats;

import org.bukkit.Material;

import net.Indyuce.mmoitems.stat.type.DoubleStat;

public class EggHatcherStat extends DoubleStat {
	public EggHatcherStat() {
		this("OMNIPET_EGG_HATCHER");
	}

	public EggHatcherStat(String id) {
		super(
				id,
				Material.CLOCK,
				"Pet Egg Hatch Speedup",
				new String[] {
						"The number of seconds to remove from the",
						"egg's hatching time.",
						"",
						"Use 0 to disable."
				},
				new String[] { "all" },
				true);
	}
}
