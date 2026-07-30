package io.github.salyvn.omnipet.command;

import java.util.concurrent.CompletableFuture;

import org.bukkit.plugin.java.JavaPlugin;

import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import io.github.salyvn.omnipet.OmniPetPlugin;
import io.github.salyvn.omnipet.impl.item.PetFoodItemImpl;
import io.github.salyvn.omnipet.impl.item.PetItemConfig;
import io.papermc.paper.command.brigadier.argument.CustomArgumentType;

public class PetFoodItemArgumentType implements CustomArgumentType<PetFoodItemImpl, String> {
	private static final PetFoodItemArgumentType TYPE = new PetFoodItemArgumentType();
	private static final DynamicCommandExceptionType INVALID_ID = new DynamicCommandExceptionType(id -> new LiteralMessage("Invalid ID: %s".formatted(id)));

	private PetFoodItemArgumentType() {}

	public static PetFoodItemArgumentType foodItem() {
		return TYPE;
	}

	public static <S> PetFoodItemImpl getFoodItem(CommandContext<S> context, String name) {
		return context.getArgument(name, PetFoodItemImpl.class);
	}

	@Override
	public PetFoodItemImpl parse(StringReader reader) throws CommandSyntaxException {
		OmniPetPlugin plugin = JavaPlugin.getPlugin(OmniPetPlugin.class);
		PetItemConfig config = plugin.getItemConfig();
		String id = reader.readString();
		PetFoodItemImpl item = config.foods().get(id);
		if (item == null) throw INVALID_ID.create(id);
		return item;
	}

	@Override
	public ArgumentType<String> getNativeType() {
		return StringArgumentType.string();
	}

	@Override
	public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
		OmniPetPlugin plugin = JavaPlugin.getPlugin(OmniPetPlugin.class);
		PetItemConfig config = plugin.getItemConfig();
		config.foods().keySet().forEach(builder::suggest);
		return builder.buildFuture();
	}
}
