package io.github.salyvn.omnipet.thirdparty.mmoitems;

import io.github.salyvn.omnipet.api.OmniPet;
import io.github.salyvn.omnipet.api.ScopeSource;
import io.github.salyvn.omnipet.thirdparty.ThirdPartyHook;
import io.github.salyvn.omnipet.thirdparty.mmoitems.stats.EggStat;
import io.github.salyvn.omnipet.thirdparty.mmoitems.stats.PetEvolverStat;
import io.github.salyvn.omnipet.thirdparty.mmoitems.stats.PetFoodStat;
import io.github.salyvn.omnipet.thirdparty.mmoitems.expr.MMOItemsFactoryValue;
import io.github.salyvn.omnipet.thirdparty.mmoitems.stats.EggHatcherStat;
import net.Indyuce.mmoitems.MMOItems;

public class MMOItemsHook implements ThirdPartyHook {
	public static final PetFoodStat PET_FOOD_STAT = new PetFoodStat();
	public static final PetEvolverStat PET_EVOLVER_STAT = new PetEvolverStat();
	public static final EggStat EGG_STAT = new EggStat();
	public static final EggHatcherStat EGG_HATCHER_STAT = new EggHatcherStat();
	public static final PetFoodStat LEGACY_PET_FOOD_STAT = new PetFoodStat("PASSIVEPET_PET_FOOD");
	public static final PetEvolverStat LEGACY_PET_EVOLVER_STAT = new PetEvolverStat("PASSIVEPET_PET_EVOLVER");
	public static final EggStat LEGACY_EGG_STAT = new EggStat("PASSIVEPET_EGG");
	public static final EggHatcherStat LEGACY_EGG_HATCHER_STAT = new EggHatcherStat("PASSIVEPET_EGG_HATCHER");

	@Override
	public void onLoad(OmniPet api) {
		MMOItems.plugin.getStats().register(PET_FOOD_STAT);
		MMOItems.plugin.getStats().register(PET_EVOLVER_STAT);
		MMOItems.plugin.getStats().register(EGG_HATCHER_STAT);
		MMOItems.plugin.getStats().register(EGG_STAT);
		MMOItems.plugin.getStats().register(LEGACY_PET_FOOD_STAT);
		MMOItems.plugin.getStats().register(LEGACY_PET_EVOLVER_STAT);
		MMOItems.plugin.getStats().register(LEGACY_EGG_HATCHER_STAT);
		MMOItems.plugin.getStats().register(LEGACY_EGG_STAT);
		api.registerItemIdentifier(new MMOItemsIdentifierImpl());
		api.registerProvider("mmoitems", ScopeSource.Global.class, g -> MMOItemsFactoryValue.FACTORY);
	}
}
