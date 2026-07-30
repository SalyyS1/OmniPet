package io.github.salyvn.omnipet.thirdparty.mmoitems.stats;

import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;

import io.github.salyvn.omnipet.OmniPetPlugin;
import io.github.salyvn.omnipet.api.OmniPet;
import net.Indyuce.mmoitems.gui.edition.EditionInventory;
import net.Indyuce.mmoitems.stat.type.StringStat;

public class EggStat extends StringStat {
	public EggStat() {
		this("OMNIPET_EGG");
	}

	public EggStat(String id) {
		super(
				id,
				Material.EGG,
				"Pet Egg",
				new String[] {
						"The ID of the egg type to turn this",
						"item into an egg."
				},
				new String[] { "all" });
	}

	@Override
	public void whenInput(EditionInventory inv, String message, Object... info) {
		OmniPet api = JavaPlugin.getPlugin(OmniPetPlugin.class);
		Objects.requireNonNull(api.eggs().get(message), "Egg with ID %s does not exists".formatted(message));
		super.whenInput(inv, message, info);
	}
}
