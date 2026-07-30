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
import io.github.salyvn.omnipet.api.EggType;
import io.github.salyvn.omnipet.api.OmniPet;
import io.papermc.paper.command.brigadier.argument.CustomArgumentType;

public class EggTypeArgumentType implements CustomArgumentType<EggType, String> {
	private static final EggTypeArgumentType TYPE = new EggTypeArgumentType();
	private static final DynamicCommandExceptionType INVALID_ID = new DynamicCommandExceptionType(id -> new LiteralMessage("Invalid ID: %s".formatted(id)));

	private EggTypeArgumentType() {}

	public static EggTypeArgumentType eggType() {
		return TYPE;
	}

	public static <S> EggType getEggType(CommandContext<S> context, String name) {
		return context.getArgument(name, EggType.class);
	}

	@Override
	public EggType parse(StringReader reader) throws CommandSyntaxException {
		OmniPet api = JavaPlugin.getPlugin(OmniPetPlugin.class);
		String id = reader.readString();
		EggType type = api.eggs().get(id);
		if (type == null) throw INVALID_ID.create(id);
		return type;
	}

	@Override
	public ArgumentType<String> getNativeType() {
		return StringArgumentType.string();
	}

	@Override
	public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
		OmniPet api = JavaPlugin.getPlugin(OmniPetPlugin.class);
		api.eggs().keySet().forEach(builder::suggest);
		return builder.buildFuture();
	}
}
