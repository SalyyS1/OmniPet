package io.github.salyvn.omnipet.impl.component;

import java.text.DecimalFormat;
import java.util.List;

import org.bukkit.Sound;
import org.bukkit.SoundCategory;
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
import io.github.nahkd123.tinyexpr.impl.LongValue;
import io.github.nahkd123.tinyexpr.impl.MethodValue;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

public class LevelingComponent implements PetComponent, Value, LoreProvider {
	private static final DecimalFormat FORMATTER = new DecimalFormat("#,##0.##");
	private Config config;
	private LanguageConfig language;
	private Pet pet;
	private PetPlayer owner;
	private double exp = 0d;
	private int level = 0;
	private int evolution = 0;

	private MethodValue setLevel = MethodValues.ofVoid(this::setLevel, int.class);
	private MethodValue setExp = MethodValues.ofVoid(this::setExp, double.class);
	private MethodValue setEvolution = MethodValues.ofVoid(this::setEvolution, int.class);
	private MethodValue addExp = MethodValues.ofVoid(this::addExp, double.class);
	private MethodValue evolve = MethodValues.ofVoid(this::evolve);

	public LevelingComponent(Config config, double exp, int level, int evolution) {
		this.config = config;
		this.language = JavaPlugin.getPlugin(OmniPetPlugin.class).getLanguageConfig();
		this.exp = exp;
		this.level = level;
		this.evolution = evolution;
	}

	@Override
	public void initialize(Pet pet, PetPlayer owner) {
		this.pet = pet;
		this.owner = owner;
	}

	public double getMaxExp() {
		double value = config.maxExp.eval(pet.evalContext()).unwrapAs(double.class);
		if (!Double.isFinite(value) || value <= 0d) throw new IllegalStateException("maxExp must be finite and positive");
		return value;
	}

	public int getMaxLevel() {
		int value = config.maxLevel.eval(pet.evalContext()).unwrapAs(int.class);
		if (value < 0) throw new IllegalStateException("maxLevel must not be negative");
		return value;
	}

	public int getMaxEvolution() {
		int value = config.maxEvolution.eval(pet.evalContext()).unwrapAs(int.class);
		if (value < 0) throw new IllegalStateException("maxEvolution must not be negative");
		return value;
	}
	public double getExp() { return exp; }
	public int getLevel() { return level; }
	public int getEvolution() { return evolution; }
	public void setExp(double exp) { this.exp = Math.clamp(exp, 0d, getMaxExp()); }
	public void setLevel(int level) { this.level = Math.clamp(level, 0, getMaxLevel()); }
	public void setEvolution(int evolution) { this.evolution = Math.clamp(evolution, 0, getMaxEvolution()); }

	public void addExp(double amount) {
		if (level >= getMaxLevel()) return;
		exp += amount;

		while (true) {
			double maxExp = getMaxExp();
			if (exp < maxExp) break;
			exp -= maxExp;
			level++;

			if (owner != null && owner.player() != null) {
				Player p = owner.player();
				language.messages().leveling().getLevelUp(pet, level - 1, level).forEach(p::sendMessage);
				p.playSound(p.getEyeLocation(), Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 1f, 1f, 0L);
			}

			if (level == getMaxLevel()) return;
		}
	}

	public void evolve() {
		if (evolution >= getMaxEvolution()) return;
		evolution++;

		if (owner != null && owner.player() != null) {
			Player p = owner.player();
			language.messages().leveling().getEvolve(pet, evolution - 1, evolution).forEach(p::sendMessage);
			p.playSound(p.getEyeLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 1f, 1f, 0L);
		}
	}

	@Override
	public Value get(String name) {
		return switch (name) {
		case "exp" -> new DoubleValue(getExp());
		case "level" -> new LongValue(getLevel());
		case "evolution" -> new LongValue(getEvolution());
		case "maxExp" -> config.maxExp.eval(pet.evalContext());
		case "maxLevel" -> config.maxLevel.eval(pet.evalContext());
		case "maxEvolution" -> config.maxEvolution.eval(pet.evalContext());
		case "setExp" -> setExp;
		case "addExp" -> addExp;
		case "setLevel" -> setLevel;
		case "setEvolution" -> setEvolution;
		case "evolve" -> evolve;
		default -> Value.super.get(name);
		};
	}

	@Override
	public List<Component> provideLore() {
		TagResolver resolver = TagResolver.resolver(
				Placeholder.unparsed("exp", FORMATTER.format(getExp())),
				Placeholder.unparsed("max_exp", FORMATTER.format(getMaxExp())),
				Placeholder.unparsed("level", FORMATTER.format(getLevel())),
				Placeholder.unparsed("max_level", FORMATTER.format(getMaxLevel())),
				Placeholder.unparsed("evolution", FORMATTER.format(getEvolution())),
				Placeholder.unparsed("max_evolution", FORMATTER.format(getMaxEvolution())),
				TagResolver.resolver("level_progressbar", (args, ctx) -> {
					int count = args.popOr("expecting number of symbols").asInt().orElse(0);
					String symbol = args.popOr("expecting symbol").value();
					return Tag.inserting(AdvtrUtils.progressBar(count, symbol, getExp() / getMaxExp()));
				}));
		MiniMessage mm = MiniMessage.miniMessage();
		return language.components().leveling().stream().map(s -> mm.deserialize(s, resolver)).toList();
	}

	public static record Config(ParsedExpr maxLevel, ParsedExpr maxExp, ParsedExpr maxEvolution) implements PetComponent.Config<LevelingComponent> {
		public static final MapCodec<Config> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
				ParsedExpr.CODEC.fieldOf("maxLevel").forGetter(Config::maxLevel),
				ParsedExpr.CODEC.fieldOf("maxExp").forGetter(Config::maxExp),
				ParsedExpr.CODEC.fieldOf("maxEvolution").forGetter(Config::maxEvolution))
				.apply(i, Config::new));
		public static final Codec<Config> CODEC = MAP_CODEC.codec();

		@Override
		public Codec<LevelingComponent> stateCodec() {
			return RecordCodecBuilder.create(i -> i.group(
					Codec.DOUBLE.optionalFieldOf("exp", 0d).forGetter(LevelingComponent::getExp),
					Codec.INT.optionalFieldOf("level", 0).forGetter(LevelingComponent::getLevel),
					Codec.INT.optionalFieldOf("evolution", 0).forGetter(LevelingComponent::getEvolution))
					.apply(i, (exp, level, evolution) -> new LevelingComponent(this, exp, level, evolution)));
		}

		@Override
		public LevelingComponent createDefaultState() {
			return new LevelingComponent(this, 0, 0, 0);
		}
	}
}
