package io.github.salyvn.omnipet.paper.management;

import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class PaperCultivationAdminCommandTarget {
    private final JavaPlugin plugin;
    private final PaperCultivationRecoveryController recovery;

    public PaperCultivationAdminCommandTarget(
            JavaPlugin plugin,
            PaperCultivationRecoveryController recovery) {
        this.plugin = java.util.Objects.requireNonNull(plugin, "cultivation admin plugin");
        this.recovery = java.util.Objects.requireNonNull(recovery, "cultivation recovery controller");
    }

    public void command(CommandSender sender, List<String> arguments) {
        if (arguments == null || arguments.size() < 2) {
            usage(sender);
            return;
        }
        UUID playerId;
        // An online name first, then a UUID: the operator is usually acting on someone in front of
        // them, and copying a UUID out of a YAML file to do it was the whole friction.
        org.bukkit.entity.Player online = org.bukkit.Bukkit.getServer() == null
                ? null
                : org.bukkit.Bukkit.getPlayerExact(arguments.get(1));
        if (online != null) {
            playerId = online.getUniqueId();
        } else {
            try {
                playerId = UUID.fromString(arguments.get(1));
            } catch (IllegalArgumentException invalid) {
                sender.sendMessage("OmniPet: cultivation player must be an online name or a valid UUID.");
                return;
            }
        }
        switch (arguments.getFirst().toLowerCase(java.util.Locale.ROOT)) {
            case "pending", "review" -> list(sender, arguments, playerId);
            case "recover" -> recover(sender, arguments, playerId);
            default -> usage(sender);
        }
    }

    private void list(CommandSender sender, List<String> arguments, UUID playerId) {
        if (arguments.size() > 3) {
            usage(sender);
            return;
        }
        int limit;
        try {
            limit = arguments.size() == 3 ? Integer.parseInt(arguments.get(2)) : 20;
        } catch (NumberFormatException invalid) {
            sender.sendMessage("OmniPet: cultivation list limit must be an integer.");
            return;
        }
        if (limit < 1 || limit > 100) {
            sender.sendMessage("OmniPet: cultivation list limit must be 1..100.");
            return;
        }
        var stage = arguments.getFirst().equalsIgnoreCase("review")
                ? recovery.operatorReview(playerId, limit)
                : recovery.pending(playerId, limit);
        stage.whenComplete((transactions, failure) -> {
            if (failure != null) {
                reply(sender, "OmniPet: cultivation list failed.");
                return;
            }
            reply(sender, "OmniPet: " + transactions.size() + " cultivation action(s).");
            transactions.forEach(action -> reply(sender, "- " + action.actionToken()
                    + " pet=" + action.petId() + " kind=" + action.kind() + " stage=" + action.stage()));
        });
    }

    private void recover(CommandSender sender, List<String> arguments, UUID playerId) {
        if (arguments.size() != 3) {
            usage(sender);
            return;
        }
        UUID actionToken;
        try {
            actionToken = UUID.fromString(arguments.get(2));
        } catch (IllegalArgumentException invalid) {
            sender.sendMessage("OmniPet: cultivation action UUID is invalid.");
            return;
        }
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            sender.sendMessage("OmniPet: cultivation recovery requires the player online.");
            return;
        }
        recovery.recover(player, actionToken).whenComplete((outcome, failure) -> reply(sender,
                failure == null && outcome != null
                        ? "OmniPet: cultivation recovery " + outcome.status() + " - " + outcome.detail()
                        : "OmniPet: cultivation recovery failed."));
    }

    private void reply(CommandSender sender, String message) {
        if (!plugin.isEnabled()) return;
        plugin.getServer().getScheduler().runTask(plugin, () -> sender.sendMessage(message));
    }

    private static void usage(CommandSender sender) {
        sender.sendMessage("OmniPet: use /pet admin cultivation pending|review <player_name> [limit] "
                + "or /pet admin cultivation recover <player_name> <action-uuid>.");
    }
}
