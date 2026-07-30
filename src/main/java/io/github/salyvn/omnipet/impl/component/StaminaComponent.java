package io.github.salyvn.omnipet.impl.component;

import java.text.DecimalFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.salyvn.omnipet.OmniPetPlugin;
import io.github.salyvn.omnipet.api.LoreProvider;
import io.github.salyvn.omnipet.api.Pet;
import io.github.salyvn.omnipet.api.PetComponent;
import io.github.salyvn.omnipet.api.PetPlayer;
import io.github.salyvn.omnipet.language.LanguageConfig;
import io.github.salyvn.omnipet.utils.AdvtrUtils;
import io.github.salyvn.omnipet.utils.ParsedExpr;
import io.github.salyvn.omnipet.utils.expr.MethodValues;
import io.github.nahkd123.tinyexpr.Value;
import io.github.nahkd123.tinyexpr.impl.DoubleValue;
import io.github.nahkd123.tinyexpr.impl.MethodValue;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

public class StaminaComponent implements PetComponent, Value, LoreProvider {
	private static final DecimalFormat FORMATTER = new DecimalFormat("#,##0.##");
	private Config config;
	private LanguageConfig language;
	private double value;
	private boolean initialized;
	private Pet pet;
	private PetPlayer owner;
	private boolean triggeredEmpty = false;
	private Set<Consumer<StaminaComponent>> subscribers = new HashSet<>();

	private MethodValue set = MethodValues.ofVoid(this::set, double.class);
	private MethodValue add = MethodValues.ofVoid(this::add, double.class);
	private MethodValue take = MethodValues.ofVoid(this::take, double.class);
	private MethodValue tryTaking = MethodValues.of(this::tryTaking, double.class, boolean.class);

	public StaminaComponent(Config config, double value, boolean initialized) {
		this.config = config;
		this.language = JavaPlugin.getPlugin(OmniPetPlugin.class).getLanguageConfig();
		this.value = value;
		this.initialized = initialized;
	}

	@Override
	public void initialize(Pet pet, PetPlayer owner) {
		this.pet = pet;
		this.owner = owner;
	}

	public double getStamina() {
		if (!initialized) {
			value = getMax();
			initialized = true;
		}

		return value;
	}

	public double getMax() { return config.maxStamina.eval(pet.evalContext()).unwrapAs(double.class); }
	public void set(double value) { this.value = Math.clamp(value, 0d, getMax()); checkState(); }
	public void add(double amount) { value = Math.clamp(getStamina() + amount, 0d, getMax()); checkState(); }
	public void take(double amount) { value = Math.clamp(getStamina() - amount, 0d, getMax()); checkState(); }

	public boolean tryTaking(double amount) {
		checkState();
		if (value < amount) return false;
		take(amount);
		return true;
	}

	@Override
	public Value get(String name) {
		return switch (name) {
		case "value" -> new DoubleValue(getStamina());
		case "max" -> config.maxStamina.eval(pet.evalContext());
		case "set" -> set;
		case "add" -> add;
		case "take" -> take;
		case "tryTaking" -> tryTaking;
		default -> Value.super.get(name);
		};
	}

	private void checkState() {
		if (value > 0d) {
			if (triggeredEmpty) for (Consumer<StaminaComponent> sub : subscribers) sub.accept(this);
			triggeredEmpty = false;
			return;
		}

		if (owner.player() == null) return;
		Player player = owner.player();

		if (triggeredEmpty) return;
		for (Consumer<StaminaComponent> sub : subscribers) sub.accept(this);
		triggeredEmpty = true;
		language.messages().stamina().getOutOfStamina(pet).forEach(player::sendMessage);
	}

	public void subscribe(Consumer<StaminaComponent> subscriber) {
		subscribers.add(subscriber);
	}

	@Override
	public List<Component> provideLore() {
		TagResolver resolver = TagResolver.resolver(
				Placeholder.unparsed("stamina", FORMATTER.format(getStamina())),
				Placeholder.unparsed("max_stamina", FORMATTER.format(getMax())),
				TagResolver.resolver("stamina_progressbar", (args, ctx) -> {
					int count = args.popOr("expecting number of symbols").asInt().orElse(0);
					String symbol = args.popOr("expecting symbol").value();
					return Tag.inserting(AdvtrUtils.progressBar(count, symbol, getStamina() / getMax()));
				}));
		MiniMessage mm = MiniMessage.miniMessage();
		return language.components().stamina().stream().map(s -> mm.deserialize(s, resolver)).toList();
	}

	public record Config(ParsedExpr maxStamina) implements PetComponent.Config<StaminaComponent> {
		public static final MapCodec<Config> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
				ParsedExpr.CODEC.fieldOf("maxStamina").forGetter(Config::maxStamina))
				.apply(i, Config::new));
		public static final Codec<Config> CODEC = MAP_CODEC.codec();

		@Override
		public Codec<StaminaComponent> stateCodec() {
			return RecordCodecBuilder.create(i -> i.group(
					Codec.DOUBLE.optionalFieldOf("value", 0d).forGetter(c -> c.value),
					Codec.BOOL.optionalFieldOf("init", false).forGetter(c -> c.initialized))
					.apply(i, (value, init) -> new StaminaComponent(this, value, init)));
		}

		@Override
		public StaminaComponent createDefaultState() {
			return new StaminaComponent(this, 0d, false);
		}
	}
}
