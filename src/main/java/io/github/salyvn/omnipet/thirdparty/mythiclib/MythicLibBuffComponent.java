package io.github.salyvn.omnipet.thirdparty.mythiclib;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.salyvn.omnipet.OmniPetPlugin;
import io.github.salyvn.omnipet.api.Pet;
import io.github.salyvn.omnipet.api.PetComponent;
import io.github.salyvn.omnipet.api.PetPlayer;
import io.github.salyvn.omnipet.impl.component.StaminaComponent;
import io.github.salyvn.omnipet.utils.Codecs;
import io.github.salyvn.omnipet.utils.ParsedExpr;
import io.github.nahkd123.tinyexpr.Value;
import io.lumine.mythic.lib.api.player.EquipmentSlot;
import io.lumine.mythic.lib.api.player.MMOPlayerData;
import io.lumine.mythic.lib.api.stat.StatInstance;
import io.lumine.mythic.lib.api.stat.StatMap;
import io.lumine.mythic.lib.api.stat.modifier.StatModifier;
import io.lumine.mythic.lib.player.modifier.ModifierSource;
import io.lumine.mythic.lib.player.modifier.ModifierType;

public class MythicLibBuffComponent implements PetComponent {
	private List<Map.Entry<UUID, Entry>> entries;
	private Pet pet;
	private PetPlayer owner;
	private boolean lastState = false;
	private boolean summoned = false;
	private BukkitTask task = null;
	private final Set<String> reportedInvalidStats = new HashSet<>();

	public MythicLibBuffComponent(Config config) {
		this.entries = new ArrayList<>(config.buffs.size());
		for (Entry e : config.buffs) entries.add(Map.entry(UUID.randomUUID(), e));
	}

	@Override
	public void initialize(Pet pet, PetPlayer owner) {
		this.pet = pet;
		this.owner = owner;
		StaminaComponent stamina = pet.component(StaminaComponent.class);
		lastState = stamina == null || stamina.getStamina() > 0d;
		if (stamina != null) stamina.subscribe(this::onStaminaStateChange);
	}

	private void onStaminaStateChange(StaminaComponent stamina) {
		boolean state = stamina.getStamina() > 0d;

		if (!summoned) {
			lastState = state;
			return;
		}

		if (state == lastState) return;
		lastState = state;
		if (state) onActivate(); else onDeactivate();
	}

	@Override
	public void onSummon() {
		summoned = true;
		if (lastState) onActivate();
	}

	@Override
	public void onRecall() {
		summoned = false;
		if (lastState) onDeactivate();
	}

	private void onActivate() {
		if (task != null) task.cancel();

		task = Bukkit.getScheduler().runTaskTimer(JavaPlugin.getPlugin(OmniPetPlugin.class), () -> {
			Player player = owner.player();
			if (player == null || !player.isOnline()) return;
			MMOPlayerData mmo = MMOPlayerData.get(player);
			StatMap statMap = mmo.getStatMap();
			Map<String, List<StatModifier>> additions = null;
			Map<String, List<UUID>> removals = null;
			Function<String, Value> context = pet.evalContext();

			for (Map.Entry<UUID, Entry> e : entries) {
				StatInstance inst = statMap.getInstance(e.getValue().stat);
				if (inst == null) {
					reportInvalidStat(e.getValue().stat, "unknown MythicLib stat");
					continue;
				}
				StatModifier mod = inst.getModifier(e.getKey());
				double val;
				try {
					val = e.getValue().value.eval(context).unwrapAs(double.class);
				} catch (RuntimeException exception) {
					reportInvalidStat(e.getValue().stat, "invalid buff expression: " + exception.getMessage());
					continue;
				}
				if (!Double.isFinite(val)) {
					reportInvalidStat(e.getValue().stat, "buff expression returned a non-finite value");
					continue;
				}

				if (mod != null) {
					if (mod.getValue() == val) continue;
					if (removals == null) removals = new HashMap<>();
					removals.computeIfAbsent(mod.getStat(), n -> new ArrayList<>()).add(mod.getUniqueId());
				}

				mod = new StatModifier(
						e.getKey(),
						"OmniPet Buff (%s)".formatted(e.getValue().stat),
						e.getValue().stat,
						val,
						e.getValue().type,
						EquipmentSlot.OTHER,
						ModifierSource.OTHER);
				if (additions == null) additions = new HashMap<>();
				additions.computeIfAbsent(mod.getStat(), n -> new ArrayList<>()).add(mod);
			}

			if (removals != null) {
				for (String stat : removals.keySet()) {
					StatInstance inst = statMap.getInstance(stat);
					for (UUID id : removals.get(stat)) inst.removeModifier(id);
				}
			}

			if (additions != null) {
				for (String stat : additions.keySet()) {
					StatInstance inst = statMap.getInstance(stat);
					for (StatModifier mod : additions.get(stat)) inst.registerModifier(mod);
				}
			}
		}, 0L, 20L);
	}

	private void onDeactivate() {
		if (task == null) return;
		task.cancel();
		task = null;
		Player player = owner.player();
		if (player == null) return;
		MMOPlayerData mmo = MMOPlayerData.get(player);
		StatMap statMap = mmo.getStatMap();
		for (Map.Entry<UUID, Entry> e : entries) {
			StatInstance instance = statMap.getInstance(e.getValue().stat);
			if (instance != null) instance.removeModifier(e.getKey());
		}
	}

	private void reportInvalidStat(String stat, String reason) {
		if (!reportedInvalidStats.add(stat + ':' + reason)) return;
		JavaPlugin.getPlugin(OmniPetPlugin.class).getLogger()
				.warning("Skipping pet buff '%s': %s".formatted(stat, reason));
	}

	private static record Entry(String stat, ModifierType type, ParsedExpr value) {
		public static final MapCodec<Entry> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
				Codec.STRING.fieldOf("stat").forGetter(Entry::stat),
				Codecs.enumOf(ModifierType.class, Codecs.EnumStringFormat.UPPERCASE).optionalFieldOf("type", ModifierType.FLAT).forGetter(Entry::type),
				ParsedExpr.CODEC.fieldOf("value").forGetter(Entry::value))
				.apply(i, Entry::new));
		public static final Codec<Entry> CODEC = MAP_CODEC.codec();
	}

	public static record Config(List<Entry> buffs) implements PetComponent.Config<MythicLibBuffComponent> {
		public static final Codec<Config> CODEC = Codec.list(Entry.CODEC).xmap(Config::new, Config::buffs);

		@Override
		public Codec<MythicLibBuffComponent> stateCodec() {
			return Codec.unit(this::createDefaultState);
		}

		@Override
		public MythicLibBuffComponent createDefaultState() {
			return new MythicLibBuffComponent(this);
		}
	}
}
