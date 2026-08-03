package io.github.salyvn.omnipet.paper.command;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.github.salyvn.omnipet.paper.studio.bukkit.PetStudioController;
import io.github.salyvn.omnipet.paper.economy.SlotTransactionAdminController;
import io.github.salyvn.omnipet.paper.incubation.HatchAdminController;
import io.github.salyvn.omnipet.paper.incubation.action.IncubationActionItemController;
import io.github.salyvn.omnipet.paper.management.PetCultivationItemController;
import io.github.salyvn.omnipet.paper.management.PaperCultivationAdminCommandTarget;
import io.github.salyvn.omnipet.paper.player.PlayerPetController;
import io.github.salyvn.omnipet.paper.player.PlayerHatchController;
import io.github.salyvn.omnipet.paper.player.PlayerSlotPurchaseController;
import io.github.salyvn.omnipet.paper.release.PaperReleaseAdminCommandTarget;
import io.github.salyvn.omnipet.paper.skill.PaperActiveSkillController;

public final class OmniPetCommand implements BasicCommand {
    private final PetStudioController studio;
    private final PlayerPetController players;
    private final PlayerHatchCommandTarget hatches;
    private final HatchAdminCommandTarget hatchAdmin;
    private final ItemCommandTarget actionItems;
    private final CultivationItemCommandTarget cultivationItems;
    private final PlayerSkillCommandTarget skills;
    private final SkillAdminCommandTarget skillAdmin;
    private final ReleaseAdminCommandTarget releaseAdmin;
    private final CultivationAdminCommandTarget cultivationAdmin;
    private final SlotTransactionAdminTarget transactions;
    private final PlayerSlotPurchaseController slotPurchases;
    private final BooleanSupplier reloadRuntime;
    /**
     * Set after construction so the ten existing constructor overloads stay unchanged.
     *
     * <p>When absent, {@code /pet} keeps its previous vault-first behavior, which is what the
     * narrower overloads used by tests rely on.
     */
    private volatile HubTarget hub;

    /** Wires the hub. Called once from {@code onEnable}. */
    public void bindHub(HubTarget target) {
        this.hub = target;
    }

    public OmniPetCommand(
            PetStudioController studio,
            PlayerPetController players,
            SlotTransactionAdminController transactions,
            PlayerSlotPurchaseController slotPurchases,
            BooleanSupplier reloadRuntime) {
        this(studio, players, null, null, null,
                (SlotTransactionAdminTarget) transactions, slotPurchases, reloadRuntime);
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
                null,
                null,
                (SlotTransactionAdminTarget) transactions,
                slotPurchases,
                reloadRuntime);
    }

    public OmniPetCommand(
            PetStudioController studio,
            PlayerPetController players,
            PlayerHatchController hatches,
            HatchAdminController hatchAdmin,
            IncubationActionItemController actionItems,
            PetCultivationItemController cultivationItems,
            PaperActiveSkillController skills,
            PaperReleaseAdminCommandTarget releaseAdmin,
            SlotTransactionAdminController transactions,
            PlayerSlotPurchaseController slotPurchases,
            BooleanSupplier reloadRuntime) {
        this(
                studio,
                players,
                hatches == null ? null : hatches::command,
                hatchAdmin,
                itemTarget(actionItems),
                cultivationTarget(cultivationItems),
                skills == null ? null : skills::cast,
                skills == null ? null : skills::adminCommand,
                releaseAdmin == null ? null : releaseAdmin::command,
                null,
                (SlotTransactionAdminTarget) transactions,
                slotPurchases,
                reloadRuntime);
    }

    public OmniPetCommand(
            PetStudioController studio,
            PlayerPetController players,
            PlayerHatchController hatches,
            HatchAdminController hatchAdmin,
            IncubationActionItemController actionItems,
            PetCultivationItemController cultivationItems,
            PaperActiveSkillController skills,
            PaperReleaseAdminCommandTarget releaseAdmin,
            PaperCultivationAdminCommandTarget cultivationAdmin,
            SlotTransactionAdminController transactions,
            PlayerSlotPurchaseController slotPurchases,
            BooleanSupplier reloadRuntime) {
        this(
                studio,
                players,
                hatches == null ? null : hatches::command,
                hatchAdmin,
                itemTarget(actionItems),
                cultivationTarget(cultivationItems),
                skills == null ? null : skills::cast,
                skills == null ? null : skills::adminCommand,
                releaseAdmin == null ? null : releaseAdmin::command,
                cultivationAdmin == null ? null : cultivationAdmin::command,
                (SlotTransactionAdminTarget) transactions,
                slotPurchases,
                reloadRuntime);
    }

    public OmniPetCommand(
            PetStudioController studio,
            PlayerPetController players,
            PlayerHatchController hatches,
            HatchAdminController hatchAdmin,
            SlotTransactionAdminController transactions,
            PlayerSlotPurchaseController slotPurchases,
            BooleanSupplier reloadRuntime) {
        this(
                studio,
                players,
                hatches == null ? null : hatches::command,
                hatchAdmin,
                null,
                (SlotTransactionAdminTarget) transactions,
                slotPurchases,
                reloadRuntime);
    }

    public OmniPetCommand(
            PetStudioController studio,
            PlayerPetController players,
            PlayerHatchController hatches,
            HatchAdminController hatchAdmin,
            IncubationActionItemController actionItems,
            SlotTransactionAdminController transactions,
            PlayerSlotPurchaseController slotPurchases,
            BooleanSupplier reloadRuntime) {
        this(
                studio,
                players,
                hatches == null ? null : hatches::command,
                hatchAdmin,
                actionItems,
                null,
                null,
                (SlotTransactionAdminTarget) transactions,
                slotPurchases,
                reloadRuntime);
    }

    public OmniPetCommand(
            PetStudioController studio,
            PlayerPetController players,
            PlayerHatchController hatches,
            HatchAdminController hatchAdmin,
            IncubationActionItemController actionItems,
            PaperActiveSkillController skills,
            SlotTransactionAdminController transactions,
            PlayerSlotPurchaseController slotPurchases,
            BooleanSupplier reloadRuntime) {
        this(
                studio,
                players,
                hatches == null ? null : hatches::command,
                hatchAdmin,
                actionItems,
                skills == null ? null : skills::cast,
                skills == null ? null : skills::adminCommand,
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
        this(studio, players, null, null, null, transactions, slotPurchases, reloadRuntime);
    }

    OmniPetCommand(
            PetStudioController studio,
            PlayerPetController players,
            PlayerHatchCommandTarget hatches,
            SlotTransactionAdminTarget transactions,
            PlayerSlotPurchaseController slotPurchases,
            BooleanSupplier reloadRuntime) {
        this(studio, players, hatches, null, null, transactions, slotPurchases, reloadRuntime);
    }

    OmniPetCommand(
            PetStudioController studio,
            PlayerPetController players,
            PlayerHatchCommandTarget hatches,
            HatchAdminCommandTarget hatchAdmin,
            SlotTransactionAdminTarget transactions,
            PlayerSlotPurchaseController slotPurchases,
            BooleanSupplier reloadRuntime) {
        this(studio, players, hatches, hatchAdmin, null, transactions, slotPurchases, reloadRuntime);
    }

    OmniPetCommand(
            PetStudioController studio,
            PlayerPetController players,
            PlayerHatchCommandTarget hatches,
            HatchAdminCommandTarget hatchAdmin,
            IncubationActionItemController actionItems,
            SlotTransactionAdminTarget transactions,
            PlayerSlotPurchaseController slotPurchases,
            BooleanSupplier reloadRuntime) {
        this(studio, players, hatches, hatchAdmin, actionItems, null, null,
                transactions, slotPurchases, reloadRuntime);
    }

    OmniPetCommand(
            PetStudioController studio,
            PlayerPetController players,
            PlayerHatchCommandTarget hatches,
            HatchAdminCommandTarget hatchAdmin,
            IncubationActionItemController actionItems,
            PlayerSkillCommandTarget skills,
            SkillAdminCommandTarget skillAdmin,
            SlotTransactionAdminTarget transactions,
            PlayerSlotPurchaseController slotPurchases,
            BooleanSupplier reloadRuntime) {
        this(studio, players, hatches, hatchAdmin, itemTarget(actionItems), null,
                skills, skillAdmin, null, transactions, slotPurchases, reloadRuntime);
    }

    OmniPetCommand(
            PetStudioController studio,
            PlayerPetController players,
            PlayerHatchCommandTarget hatches,
            HatchAdminCommandTarget hatchAdmin,
            ItemCommandTarget actionItems,
            CultivationItemCommandTarget cultivationItems,
            PlayerSkillCommandTarget skills,
            SkillAdminCommandTarget skillAdmin,
            ReleaseAdminCommandTarget releaseAdmin,
            SlotTransactionAdminTarget transactions,
            PlayerSlotPurchaseController slotPurchases,
            BooleanSupplier reloadRuntime) {
        this(studio, players, hatches, hatchAdmin, actionItems, cultivationItems,
                skills, skillAdmin, releaseAdmin, null, transactions, slotPurchases, reloadRuntime);
    }

    OmniPetCommand(
            PetStudioController studio,
            PlayerPetController players,
            PlayerHatchCommandTarget hatches,
            HatchAdminCommandTarget hatchAdmin,
            ItemCommandTarget actionItems,
            CultivationItemCommandTarget cultivationItems,
            PlayerSkillCommandTarget skills,
            SkillAdminCommandTarget skillAdmin,
            ReleaseAdminCommandTarget releaseAdmin,
            CultivationAdminCommandTarget cultivationAdmin,
            SlotTransactionAdminTarget transactions,
            PlayerSlotPurchaseController slotPurchases,
            BooleanSupplier reloadRuntime) {
        this.studio = studio;
        this.players = players;
        this.hatches = hatches;
        this.hatchAdmin = hatchAdmin;
        this.actionItems = actionItems;
        this.cultivationItems = cultivationItems;
        this.skills = skills;
        this.skillAdmin = skillAdmin;
        this.releaseAdmin = releaseAdmin;
        this.cultivationAdmin = cultivationAdmin;
        this.transactions = transactions;
        this.slotPurchases = slotPurchases;
        this.reloadRuntime = reloadRuntime;
    }

    private static ItemCommandTarget itemTarget(IncubationActionItemController controller) {
        return controller == null ? null : controller::command;
    }

    private static CultivationItemCommandTarget cultivationTarget(PetCultivationItemController controller) {
        if (controller == null) return null;
        return new CultivationItemCommandTarget() {
            @Override public boolean supports(List<String> arguments) { return controller.supports(arguments); }
            @Override public void command(CommandSender sender, List<String> arguments) {
                controller.command(sender, arguments);
            }
        };
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        Player player = sender instanceof Player value ? value : null;
        List<String> arguments = Arrays.asList(args == null ? new String[0] : args);
        // Matched before any numeric parsing so "help" can never be read as a vault page.
        if (!arguments.isEmpty() && arguments.getFirst().equalsIgnoreCase("help")) {
            CommandHelpRenderer.send(sender, arguments, player != null);
            return;
        }
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
                sender.sendMessage("OmniPet: use /pet hatch [main|off|claim|refresh|use-main|use-off].");
                return;
            }
            hatches.command(player, arguments.size() == 1 ? null : arguments.get(1));
            return;
        }
        if (!arguments.isEmpty() && arguments.getFirst().equalsIgnoreCase("skill")) {
            if (player == null) {
                sender.sendMessage("OmniPet: a player is required to cast a pet skill.");
                return;
            }
            if (skills == null) {
                sender.sendMessage("OmniPet: active pet skills are not available yet.");
                return;
            }
            if (arguments.size() != 3) {
                sender.sendMessage("OmniPet: use /pet skill <pet-uuid> <binding-id>.");
                return;
            }
            try {
                skills.cast(player, UUID.fromString(arguments.get(1)), arguments.get(2));
            } catch (IllegalArgumentException invalid) {
                sender.sendMessage("OmniPet: pet UUID is invalid.");
            }
            return;
        }
        if (arguments.size() >= 2 && arguments.get(0).equalsIgnoreCase("admin")
                && arguments.get(1).equalsIgnoreCase("item")) {
            List<String> itemArguments = arguments.subList(2, arguments.size());
            if (!sender.hasPermission("omnipet.admin.item")) {
                sender.sendMessage("OmniPet: you do not have permission to distribute OmniPet items.");
            } else if (cultivationItems != null && cultivationItems.supports(itemArguments)) {
                if (!sender.hasPermission("omnipet.admin.cultivation")) {
                    sender.sendMessage("OmniPet: you do not have permission to distribute cultivation items.");
                } else {
                    cultivationItems.command(sender, itemArguments);
                }
            } else if (actionItems == null) {
                sender.sendMessage("OmniPet: incubation action items are not available yet.");
            } else {
                actionItems.command(sender, itemArguments);
            }
            return;
        }
        if (arguments.size() >= 2 && arguments.get(0).equalsIgnoreCase("admin")
                && arguments.get(1).equalsIgnoreCase("skill")) {
            if (!sender.hasPermission("omnipet.admin.skill")) {
                sender.sendMessage("OmniPet: you do not have permission to reconcile pet skills.");
            } else if (skillAdmin == null) {
                sender.sendMessage("OmniPet: skill reconciliation is not available yet.");
            } else {
                skillAdmin.command(sender, arguments.subList(2, arguments.size()));
            }
            return;
        }
        if (arguments.size() >= 2 && arguments.get(0).equalsIgnoreCase("admin")
                && arguments.get(1).equalsIgnoreCase("cultivation")) {
            if (!sender.hasPermission("omnipet.admin.cultivation")) {
                sender.sendMessage("OmniPet: you do not have permission to recover cultivation actions.");
            } else if (cultivationAdmin == null) {
                sender.sendMessage("OmniPet: cultivation recovery is not available yet.");
            } else {
                cultivationAdmin.command(sender, arguments.subList(2, arguments.size()));
            }
            return;
        }
        if (arguments.size() >= 2 && arguments.get(0).equalsIgnoreCase("admin")
                && arguments.get(1).equalsIgnoreCase("release")) {
            if (!sender.hasPermission("omnipet.admin.release")) {
                sender.sendMessage("OmniPet: you do not have permission to reconcile pet releases.");
            } else if (releaseAdmin == null) {
                sender.sendMessage("OmniPet: release administration is not available yet.");
            } else {
                releaseAdmin.command(sender, arguments.subList(2, arguments.size()));
            }
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
        HatchAdminCommandParser.Result hatchAdminCommand = HatchAdminCommandParser.parse(arguments);
        if (!(hatchAdminCommand instanceof HatchAdminCommandParser.NotMatched)) {
            dispatchHatchAdmin(sender, hatchAdminCommand);
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
        } else if (result instanceof AdminPetCommandParser.OpenHub) {
            HubTarget target = hub;
            if (target == null) {
                // No hub wired: keep the previous vault-first behavior.
                players.openVault(player, 1);
            } else {
                players.release(playerId);
                target.open(player);
            }
        } else if (result instanceof AdminPetCommandParser.PlayerPage page) {
            players.openVault(player, page.page());
        } else if (result instanceof AdminPetCommandParser.Rejected rejected) {
            sender.sendMessage("OmniPet: command rejected - " + rejected.reason().name().toLowerCase().replace('_', ' '));
        }
    }

    private void dispatchHatchAdmin(CommandSender sender, HatchAdminCommandParser.Result result) {
        if (result instanceof HatchAdminCommandParser.Invalid) {
            sender.sendMessage("OmniPet: use /pet admin hatch inspect <player-uuid>, "
                    + "/pet admin hatch reduce|set <player-uuid> <incubation-uuid> <millis> <action-uuid>, "
                    + "or /pet admin hatch complete|cancel <player-uuid> <incubation-uuid> <action-uuid>.");
            return;
        }
        if (result instanceof HatchAdminCommandParser.Inspect inspect) {
            if (!sender.hasPermission(HatchAdminCommandParser.INSPECT_PERMISSION)) {
                sender.sendMessage("OmniPet: you do not have permission to inspect incubation state.");
                return;
            }
            if (hatchAdmin == null) {
                sender.sendMessage("OmniPet: hatch administration is not available yet.");
                return;
            }
            hatchAdmin.inspect(sender, inspect.playerId());
            return;
        }
        if (!sender.hasPermission(HatchAdminCommandParser.MANAGE_PERMISSION)) {
            sender.sendMessage("OmniPet: you do not have permission to manage incubation state.");
            return;
        }
        if (hatchAdmin == null) {
            sender.sendMessage("OmniPet: hatch administration is not available yet.");
            return;
        }
        if (result instanceof HatchAdminCommandParser.Reduce reduce) {
            hatchAdmin.reduce(sender, reduce.playerId(), reduce.incubationId(), reduce.millis(), reduce.actionId());
        } else if (result instanceof HatchAdminCommandParser.SetRemaining set) {
            hatchAdmin.setRemaining(sender, set.playerId(), set.incubationId(), set.millis(), set.actionId());
        } else if (result instanceof HatchAdminCommandParser.Complete complete) {
            hatchAdmin.complete(sender, complete.playerId(), complete.incubationId(), complete.actionId());
        } else if (result instanceof HatchAdminCommandParser.Cancel cancel) {
            hatchAdmin.cancel(sender, cancel.playerId(), cancel.incubationId(), cancel.actionId());
        }
    }

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        return CommandSuggestions.suggest(
                OmniPetCommandTree.root(), sender::hasPermission, sender instanceof Player, args);
    }

    @Override
    public boolean canUse(CommandSender sender) {
        return sender.hasPermission(AdminPetCommandParser.GENERAL_PERMISSION)
                || sender.hasPermission(HatchAdminCommandParser.INSPECT_PERMISSION)
                || sender.hasPermission(HatchAdminCommandParser.MANAGE_PERMISSION)
                || sender.hasPermission("omnipet.admin.item")
                || sender.hasPermission("omnipet.admin.cultivation")
                || sender.hasPermission("omnipet.admin.skill")
                || sender.hasPermission("omnipet.admin.release");
    }

    @Override
    public String permission() { return null; }

    /** The hub entry point, kept as a seam so the command stays testable without the hub. */
    @FunctionalInterface
    public interface HubTarget {
        void open(Player player);
    }
}

@FunctionalInterface
interface PlayerHatchCommandTarget {
    void command(Player player, String action);
}@FunctionalInterface
interface PlayerSkillCommandTarget {
    void cast(Player player, UUID petId, String bindingId);
}

@FunctionalInterface
interface SkillAdminCommandTarget {
    void command(CommandSender sender, List<String> arguments);
}

@FunctionalInterface
interface ItemCommandTarget {
    void command(CommandSender sender, List<String> arguments);
}

interface CultivationItemCommandTarget extends ItemCommandTarget {
    boolean supports(List<String> arguments);
}

@FunctionalInterface
interface ReleaseAdminCommandTarget {
    void command(CommandSender sender, List<String> arguments);
}

@FunctionalInterface
interface CultivationAdminCommandTarget {
    void command(CommandSender sender, List<String> arguments);
}
