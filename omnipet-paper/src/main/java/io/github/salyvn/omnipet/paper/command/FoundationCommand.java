package io.github.salyvn.omnipet.paper.command;

import org.bukkit.command.CommandSender;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;

import io.github.salyvn.omnipet.paper.text.MessageKey;
import io.github.salyvn.omnipet.paper.text.Messages;

/** Minimal command spine kept stable while the Studio command tree is built. */
public final class FoundationCommand implements BasicCommand {
    @Override
    public void execute(CommandSourceStack source, String[] args) {
        source.getSender().sendMessage(Messages.line(MessageKey.COMMAND_FOUNDATION_READY));
    }

    @Override
    public boolean canUse(CommandSender sender) {
        return sender.hasPermission(FoundationCommandContract.PERMISSION);
    }

    @Override
    public String permission() {
        return FoundationCommandContract.PERMISSION;
    }
}
