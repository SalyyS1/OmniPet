package io.github.salyvn.omnipet.paper.command;

import java.util.List;

import org.bukkit.command.CommandSender;

import io.github.salyvn.omnipet.paper.text.MessageKey;
import io.github.salyvn.omnipet.paper.text.Messages;

/**
 * Renders {@code /pet help} from {@link CommandHelp} through the message catalog.
 *
 * <p>Separate from {@link CommandHelp} so the paging and filtering logic stays free of Bukkit and
 * unit-testable, and separate from {@link OmniPetCommand} so that class does not grow.
 */
final class CommandHelpRenderer {
    private CommandHelpRenderer() {}

    /**
     * @param arguments the full argument list, whose first token is {@code help}
     * @param isPlayer whether the sender is a player, which hides player-only branches from console
     */
    static void send(CommandSender sender, List<String> arguments, boolean isPlayer) {
        Integer requested = page(sender, arguments);
        if (requested == null) return;

        List<CommandHelp.Line> lines = CommandHelp.lines(
                OmniPetCommandTree.root(), sender::hasPermission, isPlayer);
        if (lines.isEmpty()) {
            sender.sendMessage(Messages.line(MessageKey.HELP_EMPTY));
            return;
        }
        CommandHelp.Page page = CommandHelp.page(lines, requested);
        sender.sendMessage(Messages.line(MessageKey.HELP_HEADER,
                Messages.of("page", page.page()), Messages.of("pages", page.pages())));
        for (CommandHelp.Line line : page.lines()) {
            sender.sendMessage(Messages.line(MessageKey.HELP_LINE,
                    Messages.of("usage", line.usage()), Messages.of("description", line.description())));
        }
        if (page.page() < page.pages()) {
            sender.sendMessage(Messages.line(MessageKey.HELP_FOOTER, Messages.of("page", page.page() + 1)));
        }
    }

    /** The requested page, or {@code null} after reporting an invalid argument. */
    private static Integer page(CommandSender sender, List<String> arguments) {
        if (arguments.size() == 1) return 1;
        if (arguments.size() == 2) {
            try {
                int parsed = Integer.parseInt(arguments.get(1));
                if (parsed >= 1) return parsed;
            } catch (NumberFormatException ignored) {
                // Falls through to the shared rejection below.
            }
        }
        sender.sendMessage(Messages.line(MessageKey.HELP_PAGE_INVALID));
        return null;
    }
}
