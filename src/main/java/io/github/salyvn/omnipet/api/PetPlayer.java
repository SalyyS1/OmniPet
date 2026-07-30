package io.github.salyvn.omnipet.api;

import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

public interface PetPlayer {
	UUID uuid();
	default Player player() { return Bukkit.getPlayer(uuid()); }
	default OfflinePlayer offlinePlayer() { return Bukkit.getOfflinePlayer(uuid()); }

	Pet currentPet();
	List<Pet> pets();
	int capacity();
	void summon(Pet pet);
	void addPet(Pet pet);
	void removePet(Pet pet);

	Egg currentEgg();
	void setEgg(Egg egg);
}
