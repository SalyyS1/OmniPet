package io.github.salyvn.omnipet.impl.event;

import java.time.Duration;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.Event.Result;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;

import io.github.salyvn.omnipet.OmniPetPlugin;
import io.github.salyvn.omnipet.api.Egg;
import io.github.salyvn.omnipet.api.EggType;
import io.github.salyvn.omnipet.api.Pet;
import io.github.salyvn.omnipet.api.PetPlayer;
import io.github.salyvn.omnipet.api.item.PetItem;
import io.github.salyvn.omnipet.impl.component.DisplayComponent;
import io.github.salyvn.omnipet.impl.component.LevelingComponent;
import io.github.salyvn.omnipet.impl.component.StaminaComponent;
import io.github.salyvn.omnipet.impl.component.trigger.TriggerComponent;
import io.github.nahkd123.tinyexpr.impl.DoubleValue;
import io.github.nahkd123.tinyexpr.impl.MapValue;
import io.github.nahkd123.tinyexpr.impl.NullValue;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;

public class PlayerActionListener implements Listener {
	private OmniPetPlugin plugin;

	public PlayerActionListener(OmniPetPlugin plugin) {
		this.plugin = plugin;
	}

	@EventHandler
	public void onWalk(PlayerMoveEvent e) {
		if (e instanceof PlayerTeleportEvent) return;
		Location from = e.getFrom();
		Location to = e.getTo();
		if (to == null || from.getWorld() == null || !from.getWorld().equals(to.getWorld())) return;

		double distanceSquared = from.distanceSquared(to);
		if (distanceSquared <= 0d) return;

		PetPlayer data = plugin.player(e.getPlayer());
		if (data == null) return;

		Pet pet = data.currentPet();
		if (pet == null) return;

		TriggerComponent triggerer = pet.component(TriggerComponent.class);
		if (triggerer == null) return;
		triggerer.trigger("walk", new MapValue(Map.of(
				"walkDistance", new DoubleValue(Math.sqrt(distanceSquared)))));
	}

	@EventHandler
	public void onInteract(PlayerInteractEvent e) {
		Player player = e.getPlayer();
		PetPlayer data = plugin.player(player);
		if (data == null) return;
		if (e.getHand() == null) return;
		ItemStack hand = player.getInventory().getItem(e.getHand());
		PetItem item = plugin.identifyItem(hand);

		if (item != null) {
			switch (item) {
			case PetItem.Egg egg: {
				e.setUseItemInHand(Result.DENY);
				if (data.currentEgg() != null) return;
				if (data.pets().size() >= data.capacity()) return;
				EggType eggType = egg.egg();
				if (eggType == null || eggType.pets().isEmpty()) return;
				data.setEgg(new Egg(eggType, eggType.hatchDuration()));
				hand.subtract();
				player.getInventory().setItem(e.getHand(), hand.isEmpty() ? null : hand);
				player.playSound(player.getEyeLocation(), Sound.UI_BUTTON_CLICK, SoundCategory.PLAYERS, 1f, 1f, 0L);
				plugin.getLanguageConfig().messages().getBeginHatching(eggType, eggType.hatchDuration()).forEach(player::sendMessage);
				return;
			}
			case PetItem.Hatcher hatcher: {
				e.setUseItemInHand(Result.DENY);
				if (data.currentEgg() == null) return;
				Duration timeLeft = data.currentEgg().timeLeft().minus(hatcher.duration());
				Egg newEgg = new Egg(data.currentEgg().type(), timeLeft.isNegative() ? Duration.ZERO : timeLeft);
				data.setEgg(newEgg);
				hand.subtract();
				player.getInventory().setItem(e.getHand(), hand.isEmpty() ? null : hand);
				player.playSound(player.getEyeLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1f, 1f, 0L);
				return;
			}
			default:
				break;
			}
		}
	}

	@EventHandler
	public void onInteractEntity(PlayerInteractEntityEvent e) {
		Player player = e.getPlayer();
		PetPlayer data = plugin.player(player);
		if (data == null) return;

		Pet pet = data.currentPet();
		if (pet == null) return;

		DisplayComponent display = pet.component(DisplayComponent.class);
		if (display == null) return;
		if (!display.isPartOfDisplay(e.getRightClicked())) return;

		ItemStack hand = player.getInventory().getItem(e.getHand());
		PetItem item = plugin.identifyItem(hand);

		if (item != null) {
			switch (item) {
			case PetItem.Food food: {
				StaminaComponent stamina = pet.component(StaminaComponent.class);
				if (stamina == null) return;
				if (stamina.getStamina() >= stamina.getMax()) return;

				stamina.add(food.stamina());
				hand.subtract();
				player.getInventory().setItem(e.getHand(), hand.isEmpty() ? null : hand);
				player.playSound(player.getEyeLocation(), Sound.ENTITY_GENERIC_EAT, SoundCategory.PLAYERS, 1f, 1f, 0L);
				if (stamina.getStamina() >= stamina.getMax()) plugin.getLanguageConfig().messages().stamina().getFullStamina(pet).forEach(player::sendMessage);
				return;
			}
			case PetItem.Evolver evolver: {
				LevelingComponent leveling = pet.component(LevelingComponent.class);
				if (leveling == null) return;
				if (leveling.getEvolution() >= leveling.getMaxEvolution()) return;

				leveling.evolve();
				hand.subtract();
				player.getInventory().setItem(e.getHand(), hand.isEmpty() ? null : hand);
				return;
			}
			default:
				break;
			}
		}

		TriggerComponent triggerer = pet.component(TriggerComponent.class);
		if (triggerer != null) triggerer.trigger("interact", NullValue.NULL);
	}

	@EventHandler
	public void onPunchEntity(PrePlayerAttackEntityEvent e) {
		Player player = e.getPlayer();
		PetPlayer data = plugin.player(player);
		if (data == null) return;

		Pet pet = data.currentPet();
		if (pet == null) return;

		DisplayComponent display = pet.component(DisplayComponent.class);
		if (display != null && display.isPartOfDisplay(e.getAttacked())) {
			TriggerComponent triggerer = pet.component(TriggerComponent.class);
			if (triggerer != null) triggerer.trigger("punch", NullValue.NULL);
		}
	}

	@EventHandler
	public void onTakingDamage(EntityDamageEvent e) {
		if (e.getEntity() instanceof Player player) {
			PetPlayer data = plugin.player(player);
			if (data == null) return;

			Pet pet = data.currentPet();
			if (pet == null) return;

			TriggerComponent triggerer = pet.component(TriggerComponent.class);
			if (triggerer != null) triggerer.trigger("takingDamage", new MapValue(Map.of(
					"damage", new DoubleValue(e.getDamage()),
					"realDamage", new DoubleValue(e.getFinalDamage()))));
		}
	}
}
