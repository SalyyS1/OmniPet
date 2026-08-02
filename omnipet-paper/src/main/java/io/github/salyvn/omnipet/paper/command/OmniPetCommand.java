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
import io.github.salyvn.omnipet.paper.economy.SlotTransactionAdminController;
import io.github.salyvn.omnipet.paper.player.PlayerPetController;
import io.github.salyvn.omnipet.paper.player.PlayerHatchController;
import io.github.salyvn.omnipet.paper.player.PlayerSlotPurchaseController;

public final class OmniPetCommand implements BasicCommand {
    private final PetStudioController studio;
    private final PlayerPetController players;
    private final PlayerHatchCommandTarget hatches;
    private final SlotTransactionAdminTarget transactions;
    private final PlayerSlotPurchaseController slotPurchases;
    private final BooleanSupplier reloadRuntime;

    public OmniPetCommand(
            PetStudioController studio,
            PlayerPetController players,
            SlotTransactionAdminController transactions,
            PlayerSlotPurchaseController slotPurchases,
            BooleanSupplier reloadRuntime) {
        this(studio, players, null, (SlotTransactionAdminTarget) transactions, slotPurchases, reloadRuntime);
    }

    public OmniPetCommand(
            PetStudioController studio,
            PlayerPetController players,
            PlayerHatchController hatches,
            SlotTransactionAdminController transactions,
            PlayerSlotPurchaseController slotPurchases,
            BooleanSupplier reloadRuntime) {
        this(
                studio,
                players,
                hatches == null ? null : hatches::command,
                (SlotTransactionAdminTarget) transactions,
                slotPurchases,
                reloadRuntime);
    }

    OmniPetCommand(
            PetStudioController studio,
            PlayerPetController players,
            SlotTransactionAdminTarget transactions,
            PlayerSlotPurchaseController slotPurchases,
            BooleanSupplier reloadRuntime) {
        this(studio, players, null, transactions, slotPurchases, reloadRuntime);
    }

    OmniPetCommand(
            PetStudioController studio,
            PlayerPetController players,
            PlayerHatchCommandTarget hatches,
            SlotTransactionAdminTarget transactions,
            PlayerSlotPurchaseController slotPurchases,
            BooleanSupplier reloadRuntime) {
        this.studio = studio;
        this.players = players;
        this.hatches = hatches;
        this.transactions = transactions;
        this.slotPurchases = slotPurchases;
        this.reloadRuntime = reloadRuntime;
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        Player player = sender instanceof Player value ? value : null;
        List<String> arguments = Arrays.asList(args == null ? new String[0] : args);
        if (!arguments.isEmpty() && arguments.getFirst().equalsIgnoreCase("hatch")) {
            if (player == null) {
                sender.sendMessage("OmniPet: a player is required to hatch an egg.");
                return;
            }
            if (hatches == null) {
                sender.sendMessage("OmniPet: hatch flow is not available yet.");
                return;
            }
            if (arguments.size() > 2) {
                sender.sendMessage("OmniPet: use /pet hatch [main|off|claim|refresh].");
                return;
            }
            hatches.command(player, arguments.size() == 1 ? null : arguments.get(1));
            return;
        }
        if (!arguments.isEmpty() && arguments.getFirst().equalsIgnoreCase("slot")) {
            if (player == null) {
                sender.sendMessage("OmniPet: a player is required to buy active slots.");
                return;
            }
            int returnPage = 1;
            if (arguments.size() == 2) {
                try {
                    returnPage = Integer.parseInt(arguments.get(1));
                } catch (NumberFormatException ignored) {
                    sender.sendMessage("OmniPet: slot return page must be a positive integer.");
                    return;
                }
            } else if (arguments.size() != 1) {
                sender.sendMessage("OmniPet: use /pet slot.");
                return;
            }
            if (returnPage < 1) {
                sender.sendMessage("OmniPet: slot return page must be a positive integer.");
                return;
            }
            slotPurchases.open(player, returnPage);
            return;
        }
        SlotTransactionAdminCommandParser.Result transactionCommand =
                SlotTransactionAdminCommandParser.parse(arguments);
        if (!(transactionCommand instanceof SlotTransactionAdminCommandParser.NotMatched)) {
            if (!sender.hasPermission(SlotTransactionAdminCommandParser.PERMISSION)) {
                sender.sendMessage("OmniPet: you do not have permission to reconcile slot transactions.");
                return;
            }
            if (transactionCommand instanceof SlotTransactionAdminCommandParser.ListPending pending) {
                transactions.list(sender, pending.limit(), pending.cursor());
            } else if (transactionCommand instanceof SlotTransactionAdminCommandParser.Reconcile reconcile) {
                transactions.reconcile(sender, reconcile.transactionId(), reconcile.decision());
            } else {
                sender.sendMessage("OmniPet: use /pet admin transactions [limit] [cursor] or "
                        + "/pet admin reconcile <uuid> <charge|no-charge|refund|sync>.");
            }
            return;
        }
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

@FunctionalInterface
interface PlayerHatchCommandTarget {
    void command(Player player, String action);
}
