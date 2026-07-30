package io.github.salyvn.omnipet.impl.component;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.ItemDisplay.ItemDisplayTransform;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.NumberConversions;
import org.bukkit.util.Vector;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

import io.github.salyvn.omnipet.OmniPetPlugin;
import io.github.salyvn.omnipet.api.Pet;
import io.github.salyvn.omnipet.api.PetComponent;
import io.github.salyvn.omnipet.api.PetPlayer;
import io.github.salyvn.omnipet.utils.ItemUtils;

public class DisplayComponent implements PetComponent {
	private ItemStack item;
	private ItemDisplay display;
	private Interaction hitbox;
	private BukkitTask task;
	private PetPlayer owner;

	// Animation
	private long lastNano = 0;
	private double hoverProgress = 0d;
	private Location location;

	public DisplayComponent(Config config) {
		item = new ItemStack(Material.PLAYER_HEAD);
		if (item.getItemMeta() instanceof SkullMeta skullMeta) {
			skullMeta.setOwnerProfile(ItemUtils.skinFromUrl(config.texture));
			item.setItemMeta(skullMeta);
		}
	}

	@Override
	public void initialize(Pet pet, PetPlayer owner) {
		this.owner = owner;
	}

	@Override
	public void onSummon() {
		Player player = owner.player();
		location = player.getEyeLocation();

		if (display != null) {
			display.teleport(location);
			hitbox.teleport(location);
			return;
		}

		display = player.getWorld().spawn(location, ItemDisplay.class, e -> {
			e.setPersistent(false);
			e.setTeleportDuration(1);
			e.setItemStack(item);
			e.setItemDisplayTransform(ItemDisplayTransform.HEAD);
		});

		hitbox = player.getWorld().spawn(location, Interaction.class, e -> {
			e.setPersistent(false);
			e.setInteractionWidth(0.75f);
			e.setInteractionHeight(0.75f);
		});

		lastNano = System.nanoTime();
		task = Bukkit.getScheduler().runTaskTimer(
				JavaPlugin.getPlugin(OmniPetPlugin.class),
				() -> {
					long now = System.nanoTime();
					long deltaNano = now - lastNano;
					this.animationLoop(deltaNano / 1e9d);
					lastNano = now;
				},
				0L, 1L);
	}

	@Override
	public void onRecall() {
		if (display == null) return;
		task.cancel();
		task = null;
		display.remove();
		display = null;
		hitbox.remove();
		hitbox = null;
	}

	private void animationLoop(double deltaSec) {
		Player player = owner.player();
		Location target = player.getEyeLocation();
		Vector moveDirection = target.toVector().subtract(location.toVector());
		
		if (target.getWorld() != location.getWorld()) {
			location = target;
			display.teleport(location);
			hitbox.teleport(location);
		}

		// Display for this frame
		Location displayLocation = location.clone().add(0, Math.sin(Math.PI * 2 * hoverProgress) * 0.12, 0);
		displayLocation.setDirection(moveDirection.clone().setY(0d).multiply(-1).normalize());
		if (!NumberConversions.isFinite(displayLocation.getPitch())) displayLocation.setPitch(0f);
		if (!NumberConversions.isFinite(displayLocation.getYaw())) displayLocation.setYaw(0f);
		display.teleport(displayLocation);
		hitbox.teleport(location.clone().add(0, -0.5, 0));

		// Update for next frame
		hoverProgress = (hoverProgress + deltaSec * 0.5) % 1d;

		if (location.distanceSquared(target) >= 4 * 4) {
			double length = moveDirection.length();
			double speed = 4.3 + (length - 4) / 12 * 2.1;
			double moveAmount = length >= 16 ? (length - 4) : Math.min(length - 4, speed * deltaSec);
			location.add(moveDirection.clone().normalize().multiply(moveAmount));
		}
	}

	public boolean isPartOfDisplay(Entity e) {
		return e == display || e == hitbox;
	}

	public static record Config(String texture) implements PetComponent.Config<DisplayComponent> {
		public static final MapCodec<Config> MAP_CODEC = Codec.STRING.fieldOf("texture").xmap(Config::new, Config::texture);
		public static final Codec<Config> CODEC = MAP_CODEC.codec();

		@Override
		public Codec<DisplayComponent> stateCodec() {
			return Codec.unit(this::createDefaultState);
		}

		@Override
		public DisplayComponent createDefaultState() {
			return new DisplayComponent(this);
		}
	}
}
