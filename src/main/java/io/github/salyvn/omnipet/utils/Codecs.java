package io.github.salyvn.omnipet.utils;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;

import com.google.gson.JsonParseException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import io.github.salyvn.omnipet.OmniPetPlugin;
import io.github.salyvn.omnipet.api.EggType;
import io.github.salyvn.omnipet.api.OmniPet;
import io.github.salyvn.omnipet.api.PetComponent;
import io.github.salyvn.omnipet.api.PetType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

public class Codecs {
	private Codecs() {
	}

	public static Codec<Component> minimessage(MiniMessage mm) {
		return Codec.STRING.comapFlatMap(
				text -> {
					try {
						return DataResult.success(mm.deserialize(text));
					} catch (JsonParseException | IllegalArgumentException e) {
						return DataResult.error(e::getLocalizedMessage);
					}
				},
				mm::serialize);
	}

	public static <T extends Enum<T>> Codec<T> enumOf(Class<T> type, EnumStringFormat fmt) {
		return Codec.STRING.comapFlatMap(
				text -> {
					try {
						return DataResult.success(Enum.valueOf(type, fmt.apply(text)));
					} catch (IllegalArgumentException e) {
						return DataResult.error(e::getLocalizedMessage);
					}
				},
				v -> fmt.apply(v.toString()));
	}

	public static enum EnumStringFormat {
		NORMAL { @Override public String apply(String s) { return s; } },
		UPPERCASE { @Override public String apply(String s) { return s.toUpperCase(); } },
		LOWERCASE { @Override public String apply(String s) { return s.toLowerCase(); } };

		public abstract String apply(String s);
	}

	public static final Codec<Component> MINIMESSAGE = minimessage(MiniMessage.miniMessage());

	public static final Codec<UUID> UUID_STRING = Codec.STRING.comapFlatMap(
			text -> {
				try {
					return DataResult.success(UUID.fromString(text));
				} catch (IllegalArgumentException e) {
					return DataResult.error(e::getLocalizedMessage);
				}
			},
			UUID::toString);

	public static final Codec<Material> MATERIAL = Codec.STRING.comapFlatMap(
			text -> {
				try {
					return DataResult.success(Material.valueOf(text));
				} catch (IllegalArgumentException e) {
					return DataResult.error(e::getLocalizedMessage);
				}
			},
			Material::toString);

	public static final Codec<Duration> DURATION = Codec.STRING.comapFlatMap(
			ParseUtils::tryParseDuration,
			ParseUtils::toString);

	public static final Codec<ZonedDateTime> ZONED_DATE_TIME = Codec.STRING.comapFlatMap(
			text -> {
				try {
					return DataResult.success(ZonedDateTime.parse(text));
				} catch (DateTimeParseException e) {
					return DataResult.error(e::getLocalizedMessage);
				}
			},
			ZonedDateTime::toString);

	public static final Codec<PetType> PET_ID = Codec.STRING.comapFlatMap(
			text -> {
				OmniPet api = JavaPlugin.getPlugin(OmniPetPlugin.class);
				PetType type = api.pets().get(text);
				return type != null ? DataResult.success(type) : DataResult.error(() -> "No such pet with ID %s".formatted(text));
			},
			type -> {
				OmniPet api = JavaPlugin.getPlugin(OmniPetPlugin.class);
				return api.pets().inverse().get(type);
			});

	public static final Codec<EggType> EGG_ID = Codec.STRING.comapFlatMap(
			text -> {
				OmniPet api = JavaPlugin.getPlugin(OmniPetPlugin.class);
				EggType type = api.eggs().get(text);
				return type != null ? DataResult.success(type) : DataResult.error(() -> "No such pet with ID %s".formatted(text));
			},
			type -> {
				OmniPet api = JavaPlugin.getPlugin(OmniPetPlugin.class);
				return api.eggs().inverse().get(type);
			});

	public static final Codec<Map<String, PetComponent.Config<?>>> PET_COMPONENTS = Codec.dispatchedMap(
			Codec.STRING,
			id -> {
				OmniPet api = JavaPlugin.getPlugin(OmniPetPlugin.class);
				Codec<? extends PetComponent.Config<?>> codec = api.components().get(id);
				return codec;
			});
}
