package io.github.salyvn.omnipet.thirdparty.mmoitems;

import java.time.Duration;

import org.bukkit.inventory.ItemStack;

import io.github.salyvn.omnipet.api.EggType;
import io.github.salyvn.omnipet.api.OmniPet;
import io.github.salyvn.omnipet.api.item.ItemIdentifier;
import io.github.salyvn.omnipet.api.item.PetItem;
import io.github.salyvn.omnipet.thirdparty.mmoitems.stats.EggHatcherStat;
import io.github.salyvn.omnipet.thirdparty.mmoitems.stats.EggStat;
import io.github.salyvn.omnipet.thirdparty.mmoitems.stats.PetEvolverStat;
import io.github.salyvn.omnipet.thirdparty.mmoitems.stats.PetFoodStat;
import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.api.item.mmoitem.LiveMMOItem;
import net.Indyuce.mmoitems.stat.data.BooleanData;
import net.Indyuce.mmoitems.stat.data.DoubleData;
import net.Indyuce.mmoitems.stat.data.StringData;

public class MMOItemsIdentifierImpl implements ItemIdentifier {
	@Override
	public PetItem identify(ItemStack stack, OmniPet api) {
		if (MMOItems.getType(stack) == null) return null;
		LiveMMOItem mmo = new LiveMMOItem(stack);

		DoubleData doubleData = firstDouble(mmo, MMOItemsHook.PET_FOOD_STAT, MMOItemsHook.LEGACY_PET_FOOD_STAT);
		if (doubleData != null && !doubleData.isEmpty()) return new Food(doubleData);

		doubleData = firstDouble(mmo, MMOItemsHook.EGG_HATCHER_STAT, MMOItemsHook.LEGACY_EGG_HATCHER_STAT);
		if (doubleData != null && !doubleData.isEmpty()) return new Hatcher(doubleData);

		StringData stringData = firstString(mmo, MMOItemsHook.EGG_STAT, MMOItemsHook.LEGACY_EGG_STAT);
		if (stringData != null && !stringData.isEmpty()) {
			EggType egg = api.eggs().get(stringData.getString());
			if (egg != null) return new Egg(egg);
		}

		BooleanData boolData = firstBoolean(mmo, MMOItemsHook.PET_EVOLVER_STAT, MMOItemsHook.LEGACY_PET_EVOLVER_STAT);
		if (boolData != null && boolData.isEnabled()) return new Evolver(boolData);

		return null;
	}

	private static DoubleData firstDouble(LiveMMOItem item, PetFoodStat primary, PetFoodStat legacy) {
		DoubleData value = (DoubleData) item.getData(primary);
		return value != null && !value.isEmpty() ? value : (DoubleData) item.getData(legacy);
	}

	private static DoubleData firstDouble(LiveMMOItem item, EggHatcherStat primary, EggHatcherStat legacy) {
		DoubleData value = (DoubleData) item.getData(primary);
		return value != null && !value.isEmpty() ? value : (DoubleData) item.getData(legacy);
	}

	private static StringData firstString(LiveMMOItem item, EggStat primary, EggStat legacy) {
		StringData value = (StringData) item.getData(primary);
		return value != null && !value.isEmpty() ? value : (StringData) item.getData(legacy);
	}

	private static BooleanData firstBoolean(LiveMMOItem item, PetEvolverStat primary, PetEvolverStat legacy) {
		BooleanData value = (BooleanData) item.getData(primary);
		return value != null && !value.isEmpty() ? value : (BooleanData) item.getData(legacy);
	}

	private record Food(DoubleData data) implements PetItem.Food {
		@Override
		public double stamina() {
			return data.getValue();
		}
	}

	private record Hatcher(DoubleData data) implements PetItem.Hatcher {
		@Override
		public Duration duration() {
			return Duration.ofSeconds((long) data.getValue());
		}
	}

	private record Egg(EggType egg) implements PetItem.Egg {
		@Override
		public EggType egg() {
			return egg;
		}
	}

	private record Evolver(BooleanData data) implements PetItem.Evolver {}
}
