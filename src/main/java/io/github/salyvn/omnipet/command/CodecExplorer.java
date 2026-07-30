package io.github.salyvn.omnipet.command;

import static net.kyori.adventure.text.Component.text;

import java.util.List;
import java.util.Map;

import org.bukkit.command.CommandSender;

import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JavaOps;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * <p>Explore object dumped from {@link Codec}.</p>
 */
public class CodecExplorer {
	private static final Dynamic2CommandExceptionType EXPLORE_FAILED = new Dynamic2CommandExceptionType((path, msg) -> new LiteralMessage("Failed to explore path %s: %s".formatted(path, msg)));

	public static <T> void explore(Codec<T> codec, T object, String path, CommandSender sender, String commandPrefix) throws CommandSyntaxException {
		DataResult<Object> result = codec.encodeStart(JavaOps.INSTANCE, object).flatMap(o -> walk(o, path));
		if (!result.hasResultOrPartial()) throw EXPLORE_FAILED.create(path, result.error().get().message());
		Object value = result.getPartialOrThrow();

		switch (value) {
		case Map<?, ?> map:
			sender.sendMessage(text("Map ")
					.append(text("(")
							.append(text(map.size()).color(NamedTextColor.YELLOW))
							.append(text(" elements").color(NamedTextColor.GRAY))
							.append(text(")"))
							.color(NamedTextColor.DARK_GRAY)));

			for (Map.Entry<?, ?> e : map.entrySet()) {
				String nextPath = path.isBlank() ? String.valueOf(e.getKey()) : "%s.%s".formatted(path, e.getKey());
				sender.sendMessage(text()
						.append(text("- ").color(NamedTextColor.DARK_GRAY))
						.append(displayOf(e.getKey()))
						.append(text(": ").color(NamedTextColor.GRAY))
						.append(displayOf(e.getValue()))
						.append(text(" ")).append(text("   "))
						.append(button(text("Nav").color(NamedTextColor.YELLOW), "%s %s".formatted(commandPrefix, nextPath))));
			}

			break;
		case List<?> list:
			sender.sendMessage(text("List ")
					.append(text("(")
							.append(text(list.size()).color(NamedTextColor.YELLOW))
							.append(text(" elements").color(NamedTextColor.GRAY))
							.append(text(")"))
							.color(NamedTextColor.DARK_GRAY)));

			for (int i = 0; i < list.size(); i++) {
				String nextPath = path.isBlank() ? Integer.toString(i) : "%s.%d".formatted(path, i);
				sender.sendMessage(text()
						.append(text("- [")
								.append(text(Integer.toString(i)).color(NamedTextColor.GREEN))
								.append(text("]"))
								.color(NamedTextColor.DARK_GRAY))
						.append(text(": ").color(NamedTextColor.GRAY))
						.append(displayOf(list.get(i)))
						.append(text("   "))
						.append(button(text("Nav").color(NamedTextColor.YELLOW), "%s %s".formatted(commandPrefix, nextPath))));
			}

			break;
		default:
			sender.sendMessage(displayOf(value));
			break;
		}
	}

	public static Component displayOf(Object value) {
		return switch (value) {
		case String s -> text('\"').append(text(s)).append(text('\"')).color(NamedTextColor.AQUA);
		case Number n -> text(String.valueOf(n)).color(NamedTextColor.GREEN);
		case Map<?, ?> m -> text("Map ")
				.append(text("(")
				.append(text(m.size()).color(NamedTextColor.YELLOW))
				.append(text(" elements").color(NamedTextColor.GRAY))
				.append(text(")"))
				.color(NamedTextColor.DARK_GRAY));
		case List<?> l -> text("List ")
				.append(text("(")
				.append(text(l.size()).color(NamedTextColor.YELLOW))
				.append(text(" elements").color(NamedTextColor.GRAY))
				.append(text(")"))
				.color(NamedTextColor.DARK_GRAY));
		default -> text("Value: ").append(text(String.valueOf(value)).color(NamedTextColor.GOLD));
		};
	}

	private static Component button(Component text, String command) {
		return text("[").append(text().append(text).resetStyle()).append(text("]"))
				.color(NamedTextColor.DARK_GRAY)
				.clickEvent(ClickEvent.runCommand(command));
	}

	public static DataResult<Object> walk(Object tree, String path) {
		String[] segments = path.split("\\.");
		String parent = "";

		for (int i = 0; i < segments.length; i++) {
			if (segments[i].isBlank()) continue;
			String segment = segments[i];

			switch (tree) {
			case Number n: { Object t = tree; return DataResult.error(() -> "Trying to navigate in primitive %s".formatted(t)); }
			case String s: { Object t = tree; return DataResult.error(() -> "Trying to navigate in primitive '%s'".formatted(t)); }
			case Map<?, ?> m:
				tree = m.get(segment);

				if (tree == null) {
					String p = parent;
					return DataResult.error(() -> "%s not found in %s".formatted(segment, p));
				}

				break;
			case List<?> l:
				try {
					int index = Integer.parseInt(segment);

					if (index < 0 || index >= l.size()) {
						String p = parent;
						return DataResult.error(() -> "Accessing array at %s: Index %d out of bounds (length = %d)".formatted(p, index, l.size()));
					}

					tree = l.get(index);
					break;
				} catch (NumberFormatException e) {
					String p = parent;
					return DataResult.error(() -> "Accessing array at %s: %s".formatted(p, e.getLocalizedMessage()));
				}
			default: { Object t = tree; return DataResult.error(() -> "Cannot navigate in '%s'".formatted(t)); }
			}

			if (!parent.isBlank()) parent += '.';
			parent += segment;
		}

		return DataResult.success(tree);
	}
}
