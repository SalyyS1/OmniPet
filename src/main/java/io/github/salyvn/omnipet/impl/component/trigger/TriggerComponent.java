package io.github.salyvn.omnipet.impl.component.trigger;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.codehaus.plexus.util.FastMap;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;

import io.github.salyvn.omnipet.OmniPetPlugin;
import io.github.salyvn.omnipet.api.LoreProvider;
import io.github.salyvn.omnipet.api.Pet;
import io.github.salyvn.omnipet.api.PetComponent;
import io.github.salyvn.omnipet.api.PetPlayer;
import io.github.salyvn.omnipet.api.ScopeSource;
import io.github.salyvn.omnipet.utils.AdvtrUtils;
import io.github.salyvn.omnipet.utils.ParseUtils;
import io.github.nahkd123.tinyexpr.Value;
import io.github.nahkd123.tinyexpr.impl.NullValue;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

public class TriggerComponent implements PetComponent, LoreProvider {
	private Pet pet;
	private Map<String, List<TriggerScript>> scripts;
	private PetPlayer owner;
	private Map<TriggerScript, Long> cooldowns = new HashMap<>();
	private Set<TriggerScript> reportedInvalidTriggers = new HashSet<>();
	private BukkitTask task = null;

	public TriggerComponent(Config config) {
		scripts = config.scripts.size() < 4096 ? new FastMap<>() : new HashMap<>();

		for (TriggerScript s : config.scripts) {
			// TODO: special scripts
			scripts.computeIfAbsent(s.type(), id -> new ArrayList<>()).add(s);
		}
	}

	@Override
	public void initialize(Pet pet, PetPlayer owner) {
		this.pet = pet;
		this.owner = owner;
	}

	@Override
	public void onSummon() {
		if (scripts.containsKey("interval")) {
			if (task != null) task.cancel();
			task = Bukkit.getScheduler().runTaskTimer(
					JavaPlugin.getPlugin(OmniPetPlugin.class),
					() -> trigger("interval", NullValue.NULL),
					0L, 1L);
		}
	}

	@Override
	public void onRecall() {
		if (task != null) {
			task.cancel();
			task = null;
		}
	}

	public void trigger(String type, Value vars) {
		List<TriggerScript> scripts = this.scripts.get(type);
		if (scripts == null) return;
		OmniPetPlugin plugin = JavaPlugin.getPlugin(OmniPetPlugin.class);
		Map<String, Value> map = plugin.forScope(new ScopeSource.Player(owner.player())).stream().collect(Collectors.toMap(Pair::getFirst, Pair::getSecond));
		long now = System.currentTimeMillis();

		Function<String, Value> petContext = pet.evalContext();
		Function<String, Value> context = name -> {
			return switch (name) {
			case "trigger" -> vars;
			default -> map.containsKey(name) ? map.get(name) : petContext.apply(name);
			};
		};

		for (TriggerScript s : scripts) {
			if (s.cooldown() != null) {
				long lastTimestamp = cooldowns.getOrDefault(s, 0L);
				long cooldownTicks;
				try {
					cooldownTicks = s.cooldown().eval(context).unwrapAs(long.class);
				} catch (RuntimeException exception) {
					reportInvalidTrigger(s, "cooldown expression failed: " + exception.getMessage());
					continue;
				}
				if (cooldownTicks <= 0) {
					reportInvalidTrigger(s, "cooldown must be positive");
					continue;
				}
				long cooldown = cooldownTicks > Long.MAX_VALUE / 50 ? Long.MAX_VALUE : cooldownTicks * 50;
				long elapsed = now - lastTimestamp;
				if (elapsed < cooldown) continue;
			} else if ("interval".equals(type)) {
				reportInvalidTrigger(s, "cooldown is required for interval triggers");
				continue;
			}

			if (s.precondition() != null && !s.precondition().eval(context).unwrapAs(boolean.class)) continue;
			if (s.cooldown() != null) cooldowns.put(s, now);

			s.script().trigger(context);
		}
	}

	private void reportInvalidTrigger(TriggerScript script, String reason) {
		if (!reportedInvalidTriggers.add(script)) return;
		JavaPlugin.getPlugin(OmniPetPlugin.class).getLogger()
				.warning("Skipping invalid trigger: %s".formatted(reason));
	}

	@Override
	public List<Component> provideLore() {
		long now = System.currentTimeMillis();
		Function<String, Value> petContext = pet.evalContext();
		MiniMessage mm = MiniMessage.miniMessage();

		return scripts
				.values()
				.stream()
				.flatMap(List::stream)
				.flatMap(s -> {
					long lastTimestamp = cooldowns.getOrDefault(s, 0L);
					long cooldownTicks = 0;
					if (s.cooldown() != null) {
						try {
							cooldownTicks = s.cooldown().eval(petContext).unwrapAs(long.class);
						} catch (RuntimeException ignored) {
							cooldownTicks = 0;
						}
					}
					long cooldown = cooldownTicks > Long.MAX_VALUE / 50 ? Long.MAX_VALUE : Math.max(cooldownTicks, 0) * 50;
					long elapsed = now - lastTimestamp;

					TagResolver[] resolvers = {
							TagResolver.resolver("trigger_progressbar", (args, ctx) -> {
								int count = args.popOr("expecting count").asInt().getAsInt();
								String symbol = args.popOr("expecting symbol").value();
								return Tag.inserting(AdvtrUtils.progressBar(count, symbol, cooldown > 0 ? elapsed / (double) cooldown : 1d));
							}),
							TagResolver.resolver("trigger_cooldown", (args, ctx) -> {
								if (s.cooldown() == null) return Tag.inserting(Component.text("0s"));
								Duration d = Duration.ofSeconds(Math.max(cooldown - elapsed, 0) / 1000);
								return Tag.inserting(Component.text(ParseUtils.toString(d)));
							})
					};

					return s.lore().stream().map(l -> mm.deserialize(l, resolvers));
				})
				.toList();
	}

	public static record Config(List<TriggerScript> scripts) implements PetComponent.Config<TriggerComponent> {
		public static final Codec<Config> CODEC = Codec.list(TriggerScript.CODEC).xmap(Config::new, Config::scripts);

		@Override
		public Codec<TriggerComponent> stateCodec() {
			return Codec.unit(this::createDefaultState);
		}

		@Override
		public TriggerComponent createDefaultState() {
			return new TriggerComponent(this);
		}
	}
}
