package io.github.salyvn.omnipet.impl;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.salyvn.omnipet.OmniPetPlugin;
import io.github.salyvn.omnipet.api.Egg;
import io.github.salyvn.omnipet.api.Pet;
import io.github.salyvn.omnipet.api.PetPlayer;
import io.github.salyvn.omnipet.utils.CapacityUtils;
import io.github.salyvn.omnipet.utils.Codecs;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;

public class PetPlayerImpl implements PetPlayer {
	public static final MapCodec<PetPlayerImpl> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			Codecs.UUID_STRING.fieldOf("uuid").forGetter(PetPlayerImpl::uuid),
			Codec.list(PetImpl.CODEC).fieldOf("pets").forGetter(p -> p.pets),
			Codec.INT.fieldOf("currentPetIndex").forGetter(PetPlayerImpl::currentPetIndex),
			Egg.CODEC.optionalFieldOf("currentEgg").forGetter(p -> Optional.ofNullable(p.currentEgg)),
			Codec.INT.optionalFieldOf("capacity", 0).forGetter(PetPlayerImpl::capacity))
			.apply(i, (uuid, pets, currentPet, currentEgg, capacity) -> new PetPlayerImpl(uuid, pets, currentPet, currentEgg.orElse(null), capacity)));
	public static final Codec<PetPlayerImpl> CODEC = MAP_CODEC.codec();

	private UUID uuid;
	private List<PetImpl> pets;
	private PetImpl currentPet;
	private Egg currentEgg;
	private int lastCapacity = -1;

	private Player player = null;
	private ZonedDateTime hatchTime = null;
	private BossBar hatchingBossbar = null;

	public PetPlayerImpl(UUID uuid, List<PetImpl> pets, PetImpl currentPet, Egg currentEgg, int lastCapacity) {
		this.uuid = uuid;
		this.lastCapacity = lastCapacity;
		this.pets = new ArrayList<>(pets);
		this.currentPet = currentPet;
		this.currentEgg = currentEgg;
	}

	public PetPlayerImpl(UUID uuid, List<PetImpl> pets, int currentPetIndex, Egg currentEgg, int lastCapacity) {
		this(uuid, pets, currentPetAt(pets, currentPetIndex), currentEgg, lastCapacity);
	}

	private static PetImpl currentPetAt(List<PetImpl> pets, int index) {
		return index >= 0 && index < pets.size() ? pets.get(index) : null;
	}

	public void setupLiveData(OmniPetPlugin plugin) {
		player = Bukkit.getPlayer(uuid);
		for (PetImpl pet : pets) pet.idToComponent().values().forEach(c -> c.initialize(pet, this));
		if (currentPet != null) currentPet.idToComponent().values().forEach(c -> c.onSummon());
		updateHatchTime();
		refreshLiveData(plugin);
	}

	public void destroyLiveData() {
		if (player == null) return;
		if (currentPet != null) currentPet.idToComponent().values().forEach(c -> c.onRecall());

		if (hatchTime != null) {
			Duration timeLeft = Duration.between(ZonedDateTime.now(), hatchTime);
			currentEgg = new Egg(currentEgg.type(), timeLeft.isNegative() ? Duration.ZERO : timeLeft);
			hatchTime = null;
		}

		if (hatchingBossbar != null) {
			player.hideBossBar(hatchingBossbar);
			hatchingBossbar = null;
		}

		player = null;
	}

	public void refreshLiveData(OmniPetPlugin plugin) {
		if (player == null) return;

		if (currentEgg != null) {
			Egg current = currentEgg();
			Component label = plugin.getLanguageConfig().hud().getHatchingBossbar(current);
			float progress = hatchProgress(current.timeLeft(), current.type().hatchDuration());

			if (hatchingBossbar == null) {
				hatchingBossbar = BossBar.bossBar(label, progress, BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS);
				player.showBossBar(hatchingBossbar);
			} else {
				hatchingBossbar.name(label);
				hatchingBossbar.progress(progress);
			}
		} else if (hatchingBossbar != null) {
			player.hideBossBar(hatchingBossbar);
			hatchingBossbar = null;
		}
	}

	private static float hatchProgress(Duration timeLeft, Duration hatchDuration) {
		double totalSeconds = hatchDuration.getSeconds() + hatchDuration.getNano() / 1_000_000_000d;
		if (totalSeconds <= 0d) return 1f;

		double remainingSeconds = timeLeft.getSeconds() + timeLeft.getNano() / 1_000_000_000d;
		double progress = 1d - remainingSeconds / totalSeconds;
		return Double.isFinite(progress) ? (float) Math.clamp(progress, 0d, 1d) : 1f;
	}

	@Override
	public UUID uuid() {
		return uuid;
	}

	@Override
	public Player player() {
		if (player != null) return player;
		return PetPlayer.super.player();
	}

	@Override
	public OfflinePlayer offlinePlayer() {
		if (player != null) return player;
		return PetPlayer.super.offlinePlayer();
	}

	@Override
	public Pet currentPet() {
		return currentPet;
	}

	private int currentPetIndex() {
		return currentPet != null ? pets.indexOf(currentPet) : -1;
	}

	@Override
	public List<Pet> pets() {
		return Collections.unmodifiableList(pets);
	}

	@Override
	public int capacity() {
		if (player != null) {
			OmniPetConfig global = JavaPlugin.getPlugin(OmniPetPlugin.class).getGlobalConfig();
			lastCapacity = CapacityUtils.normalizeCachedCapacity(lastCapacity, global.globalMaxSlots());

			if (lastCapacity != -1) {
				if (lastCapacity < global.globalMaxSlots() && player.hasPermission(global.slotPermList()[lastCapacity])) lastCapacity = -1;
				if (lastCapacity > 0 && !player.hasPermission(global.slotPermList()[lastCapacity - 1])) lastCapacity = -1;
			}

			if (lastCapacity == -1) {
				lastCapacity = global.globalMaxSlots();

				for (int i = 0; i < global.globalMaxSlots(); i++) {
					if (!player.hasPermission(global.slotPermList()[i])) {
						lastCapacity = i;
						break;
					}
				}
			}
		}

		return lastCapacity;
	}

	@Override
	public void summon(Pet pet) {
		if (pet == currentPet) return;

		if (currentPet != null) {
			if (player != null) currentPet.idToComponent().values().forEach(c -> c.onRecall());
		}

		if (pet != null) {
			if (!(pet instanceof PetImpl petImpl)) throw new IllegalArgumentException("Invalid pet implementation");
			if (player != null) petImpl.idToComponent().values().forEach(c -> c.onSummon());
			currentPet = petImpl;
		} else {
			currentPet = null;
		}
	}

	@Override
	public void addPet(Pet pet) {
		if (!(pet instanceof PetImpl petImpl)) throw new IllegalArgumentException("Invalid pet implementation");
		if (pets.contains(petImpl)) return;
		pets.add(petImpl);
		if (player != null) petImpl.idToComponent().values().forEach(c -> c.initialize(petImpl, this));
	}

	@Override
	public void removePet(Pet pet) {
		if (!(pet instanceof PetImpl petImpl)) throw new IllegalArgumentException("Invalid pet implementation");
		if (currentPet == petImpl) summon(null);
		pets.remove(petImpl);
	}

	@Override
	public Egg currentEgg() {
		if (hatchTime == null) return currentEgg;
		if (currentEgg != null) return new Egg(currentEgg.type(), Duration.between(ZonedDateTime.now(), hatchTime));
		return null;
	}

	@Override
	public void setEgg(Egg egg) {
		currentEgg = egg;
		if (player != null) updateHatchTime();
	}

	private void updateHatchTime() {
		hatchTime = currentEgg != null ? ZonedDateTime.now().plus(currentEgg.timeLeft()) : null;
	}
}
