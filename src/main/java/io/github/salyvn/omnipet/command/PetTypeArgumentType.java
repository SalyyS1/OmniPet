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
import io.github.salyvn.omnipet.api.OmniPet;
import io.github.salyvn.omnipet.api.PetType;
import io.papermc.paper.command.brigadier.argument.CustomArgumentType;

public class PetTypeArgumentType implements CustomArgumentType<PetType, String> {
	private static final PetTypeArgumentType TYPE = new PetTypeArgumentType();
	private static final DynamicCommandExceptionType INVALID_ID = new DynamicCommandExceptionType(id -> new LiteralMessage("Invalid ID: %s".formatted(id)));

	private PetTypeArgumentType() {}

	public static PetTypeArgumentType petType() {
		return TYPE;
	}

	public static <S> PetType getPetType(CommandContext<S> context, String name) {
		return context.getArgument(name, PetType.class);
	}

	@Override
	public PetType parse(StringReader reader) throws CommandSyntaxException {
		OmniPet api = JavaPlugin.getPlugin(OmniPetPlugin.class);
		String id = reader.readString();
		PetType type = api.pets().get(id);
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
		api.pets().keySet().forEach(builder::suggest);
		return builder.buildFuture();
	}
}
