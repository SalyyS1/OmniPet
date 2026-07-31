package io.github.salyvn.omnipet.paper.command;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.github.salyvn.omnipet.paper.studio.bukkit.PetStudioController;
import io.github.salyvn.omnipet.paper.player.PlayerPetController;

public final class OmniPetCommand implements BasicCommand {
    private final PetStudioController studio;
    private final PlayerPetController players;
    private final BooleanSupplier reloadRuntime;

    public OmniPetCommand(
            PetStudioController studio,
            PlayerPetController players,
            BooleanSupplier reloadRuntime) {
        this.studio = studio;
        this.players = players;
        this.reloadRuntime = reloadRuntime;
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        Player player = sender instanceof Player value ? value : null;
        List<String> arguments = Arrays.asList(args == null ? new String[0] : args);
        if (arguments.size() == 2 && arguments.get(0).equalsIgnoreCase("admin")
                && arguments.get(1).equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("omnipet.admin.reload")) {
                sender.sendMessage("OmniPet: you do not have permission to reload definitions.");
                return;
            }
            sender.sendMessage(reloadRuntime.getAsBoolean()
                    ? "OmniPet: config and definitions reloaded; online player reconciliation queued."
                    : "OmniPet: reload failed; the previous registry remains active.");
            return;
        }

        UUID playerId = player == null ? null : player.getUniqueId();
        AdminPetCommandParser.Result result = AdminPetCommandParser.parse(new AdminPetCommandParser.Request(
                "pet", arguments, playerId, sender.hasPermission(AdminPetCommandParser.GENERAL_PERMISSION),
                sender.hasPermission(AdminPetCommandParser.MANAGE_PET_PERMISSION)));
        if (result instanceof AdminPetCommandParser.OpenAdminBrowse) {
            players.release(playerId);
            studio.openBrowse(player);
        } else if (result instanceof AdminPetCommandParser.PlayerPage page) {
            players.openVault(player, page.page());
        } else if (result instanceof AdminPetCommandParser.Rejected rejected) {
            sender.sendMessage("OmniPet: command rejected - " + rejected.reason().name().toLowerCase().replace('_', ' '));
        }
    }

    @Override
    public boolean canUse(CommandSender sender) { return sender.hasPermission(AdminPetCommandParser.GENERAL_PERMISSION); }

    @Override
    public String permission() { return AdminPetCommandParser.GENERAL_PERMISSION; }
}
