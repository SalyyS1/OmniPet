package io.github.salyvn.omnipet.paper.command;

import org.bukkit.command.CommandSender;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;

/** Minimal command spine kept stable while the Studio command tree is built. */
public final class FoundationCommand implements BasicCommand {
    @Override
    public void execute(CommandSourceStack source, String[] args) {
        source.getSender().sendMessage("OmniPet foundation is enabled; pet features are loading in the next release phase.");
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
