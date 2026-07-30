package io.github.salyvn.omnipet.impl.component;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.salyvn.omnipet.api.EggType;
import io.github.salyvn.omnipet.api.PetComponent;
import io.github.nahkd123.tinyexpr.Value;
import io.github.nahkd123.tinyexpr.impl.DoubleValue;

public class HatchingComponent implements PetComponent, Value {
	private double rarity;
	private long seed;

	public HatchingComponent(double rarity, long seed) {
		this.rarity = rarity;
		this.seed = seed;
	}

	public void roll(EggType type) {
		rarity = type.rarity();
		seed = ThreadLocalRandom.current().nextLong();
	}

	public double rarity() { return rarity; }
	public long seed() { return seed; }
	public RandomGenerator randomizer(String name) { return new Random(seed ^ name.hashCode()); }

	@Override
	public Value get(String name) {
		return switch (name) {
		case "rarity" -> new DoubleValue(rarity);
		default -> Value.super.get(name);
		};
	}

	public static record Config(double defaultRarity) implements PetComponent.Config<HatchingComponent> {
		public static final MapCodec<Config> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
				Codec.DOUBLE.optionalFieldOf("defaultRarity", 0d).forGetter(Config::defaultRarity))
				.apply(i, Config::new));
		public static final Codec<Config> CODEC = MAP_CODEC.codec();

		@Override
		public Codec<HatchingComponent> stateCodec() {
			return RecordCodecBuilder.create(i -> i.group(
					Codec.DOUBLE.optionalFieldOf("rarity", defaultRarity).forGetter(HatchingComponent::rarity),
					Codec.LONG.optionalFieldOf("seed", 0L).forGetter(HatchingComponent::seed))
					.apply(i, HatchingComponent::new));
		}

		@Override
		public HatchingComponent createDefaultState() {
			return new HatchingComponent(defaultRarity, 0L);
		}
	}
}
