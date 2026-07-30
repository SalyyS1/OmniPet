package io.github.salyvn.omnipet.command;

import java.time.Duration;
import java.util.Collection;
import java.util.List;

import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.serialization.DataResult;

import io.github.salyvn.omnipet.utils.ParseUtils;
import io.papermc.paper.command.brigadier.argument.CustomArgumentType;

public class DurationArgumentType implements CustomArgumentType<Duration, String> {
	private static final DynamicCommandExceptionType INVALID_INPUT = new DynamicCommandExceptionType(id -> new LiteralMessage("Invalid duration input: %s".formatted(id)));
	private static final DurationArgumentType TYPE = new DurationArgumentType();

	private DurationArgumentType() {}

	public static DurationArgumentType duration() {
		return TYPE;
	}

	public static <S> Duration getDuration(CommandContext<S> context, String name) {
		return context.getArgument(name, Duration.class);
	}

	@Override
	public Duration parse(StringReader reader) throws CommandSyntaxException {
		String input = reader.readString();
		DataResult<Duration> result = ParseUtils.tryParseDuration(input);
		if (result.isError()) throw INVALID_INPUT.create(input);
		return result.getPartialOrThrow();
	}

	@Override
	public Collection<String> getExamples() {
		return List.of("\"1d 2h\"", "1d2h");
	}

	@Override
	public ArgumentType<String> getNativeType() {
		return StringArgumentType.string();
	}
}
