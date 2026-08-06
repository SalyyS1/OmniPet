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
import io.github.salyvn.omnipet.paper.incubation.EggAdminController;
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
    /**
     * Resolves an admin command's player argument from an online name, falling back to a UUID.
     *
     * <p>Static because it only reads the live roster. Only online names resolve: an offline lookup
     * is a blocking profile fetch and must not run while a command is dispatched on the main thread.
     *
     * <p>Returns empty when no server is running, so the UUID form still parses in a unit test rather
     * than the roster read throwing before the argument is even examined.
     */
    private static final PlayerArgumentResolver ONLINE_PLAYERS = new PlayerArgumentResolver(name -> {
        if (org.bukkit.Bukkit.getServer() == null) return java.util.Optional.empty();
        Player online = org.bukkit.Bukkit.getPlayerExact(name);
        return online == null
                ? java.util.Optional.empty()
                : java.util.Optional.of(online.getUniqueId());
    });

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

    /**
     * Egg and direct-pet distribution, set after construction for the same reason as the hub.
     *
     * <p>When absent both branches report the feature as unavailable rather than throwing, so the
     * narrower constructors used by tests need no change.
     */
    private volatile EggAdminController eggAdmin;

    /** Wires egg and pet granting. Called once from {@code onEnable}. */
    public void bindEggAdmin(EggAdminController target) {
        this.eggAdmin = target;
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

        // Order is load-bearing and unchanged by the split: the player branches include `help`, which
        // must be matched before anything parses a leading token as a vault page number.
        if (playerRouter().route(sender, player, arguments)) return;
        if (adminRouter().route(sender, arguments)) return;
        openVaultOrHub(sender, player, arguments);
    }

    /**
     * What a bare {@code /pet}, {@code /pet <page>}, and {@code /pet admin browse} fall through to.
     *
     * <p>Kept here rather than in a router because it is the fall-through itself: every branch above
     * returns, and anything left is a vault page, the hub, or the Studio.
     */
    private void openVaultOrHub(CommandSender sender, Player player, List<String> arguments) {
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

    /**
     * The player branches, built from the fields this command was constructed with.
     *
     * <p>Built on demand rather than in the constructor because there are ten constructor overloads, each
     * wiring a different subset; deriving the routers from the fields means the split needed no change to
     * any of them.
     */
    private PlayerCommandRouter playerRouter() {
        return new PlayerCommandRouter(hatches, skills,
                slotPurchases == null ? null : slotPurchases::open);
    }

    /** Everything under {@code admin}, shared verbatim with the standalone {@code /petadmin}. */
    AdminCommandRouter adminRouter() {
        AdminCommandRouter router = new AdminCommandRouter(
                adminAreas(),
                new HatchAdminRouter(hatchAdmin, ONLINE_PLAYERS),
                new SlotTransactionAdminRouter(transactions),
                reloadRuntime);
        // Re-bound each time because the router is built per dispatch; the target itself is the
        // long-lived object, set once from onEnable.
        router.bindTransactionMenu(transactionMenu);
        return router;
    }

    /**
     * Opens the transaction menu, set after construction like the hub and egg admin.
     *
     * <p>Absent in the narrower constructors the tests use, where the router reports the menu as
     * unavailable rather than throwing.
     */
    private volatile java.util.function.Consumer<Player> transactionMenu;

    /** Wires the transaction menu. Called once from {@code onEnable}. */
    public void bindTransactionMenu(java.util.function.Consumer<Player> target) {
        this.transactionMenu = target;
    }

    /**
     * Explains why an owner's pet stats are or are not reaching them.
     *
     * <p>Set after construction like the transaction menu, and absent in the narrower constructors the
     * tests use, where the area reports itself unavailable rather than throwing.
     */
    private volatile StatDiagnosticTarget statDiagnostics;

    /** Wires the stat diagnostic. Called once from {@code onEnable}. */
    public void bindStatDiagnostics(StatDiagnosticTarget target) {
        this.statDiagnostics = target;
    }

    /** Produces the diagnostic lines for one owner. */
    @FunctionalInterface
    public interface StatDiagnosticTarget {
        java.util.List<String> describe(java.util.UUID ownerId, boolean verbose);
    }

    /**
     * The simple {@code admin <area>} branches.
     *
     * <p>Items are one area with an internal fork: a cultivation item additionally requires the
     * cultivation permission, which is why that branch is a handler rather than two areas.
     */
    private List<AdminArea> adminAreas() {
        return List.of(
                new AdminArea("item", "omnipet.admin.item",
                        "OmniPet: you do not have permission to distribute OmniPet items.",
                        "OmniPet: incubation action items are not available yet.",
                        this::dispatchItems),
                new AdminArea("egg", "omnipet.admin.egg",
                        "OmniPet: you do not have permission to administer eggs.",
                        "OmniPet: egg administration is not available yet.",
                        eggAdmin == null ? null : eggAdmin::eggCommand),
                new AdminArea("pet", "omnipet.admin.petgive",
                        "OmniPet: you do not have permission to grant pets directly.",
                        "OmniPet: pet granting is not available yet.",
                        eggAdmin == null ? null : eggAdmin::petCommand),
                new AdminArea("skill", "omnipet.admin.skill",
                        "OmniPet: you do not have permission to reconcile pet skills.",
                        "OmniPet: skill reconciliation is not available yet.",
                        skillAdmin == null ? null : skillAdmin::command),
                new AdminArea("cultivation", "omnipet.admin.cultivation",
                        "OmniPet: you do not have permission to recover cultivation actions.",
                        "OmniPet: cultivation recovery is not available yet.",
                        cultivationAdmin == null ? null : cultivationAdmin::command),
                new AdminArea("release", "omnipet.admin.release",
                        "OmniPet: you do not have permission to reconcile pet releases.",
                        "OmniPet: release administration is not available yet.",
                        releaseAdmin == null ? null : releaseAdmin::command),
                new AdminArea("stats", "omnipet.admin.reload",
                        "OmniPet: you do not have permission to inspect pet stats.",
                        "OmniPet: stat diagnostics are not available yet.",
                        statDiagnostics == null ? null : this::dispatchStatDiagnostics));
    }

    /**
     * Reports whether a player's pet stats are reaching them, and why not when they are not.
     *
     * <p>Shares the reload permission rather than minting a node for one read-only command: an operator who
     * can reload definitions is exactly the person who needs this, and a new node would be one more thing to
     * grant before the diagnostic could be used — at the moment it is most needed.
     *
     * <p>{@code all} appends every stat. The default is a verdict and little else: a healthy server has
     * nothing to read in a per-stat list that the character sheet does not already show, and burying the
     * verdict under it was the complaint.
     */
    private void dispatchStatDiagnostics(CommandSender sender, List<String> arguments) {
        StatDiagnosticTarget target = statDiagnostics;
        if (target == null) {
            sender.sendMessage("OmniPet: stat diagnostics are not available yet.");
            return;
        }
        List<String> named = new java.util.ArrayList<>(arguments);
        // Accepted in either position, because "stats all" for yourself reads as naturally as
        // "stats <player> all" and refusing one of the two would be a rule to remember for no reason.
        boolean verbose = named.removeIf(argument -> argument.equalsIgnoreCase("all"));
        Player subject = named.isEmpty()
                ? (sender instanceof Player self ? self : null)
                : org.bukkit.Bukkit.getPlayerExact(named.getFirst());
        if (subject == null) {
            sender.sendMessage(named.isEmpty()
                    ? "OmniPet: name a player — /pet admin stats <online-player> [all]"
                    : "OmniPet: no online player named " + named.getFirst() + ".");
            return;
        }
        sender.sendMessage("OmniPet stat check for " + subject.getName() + ":");
        target.describe(subject.getUniqueId(), verbose).forEach(sender::sendMessage);
    }

    /**
     * Item distribution, where a cultivation item needs a second permission.
     *
     * <p>The availability check is deliberately on the action-item target rather than on both: the
     * cultivation branch reports its own missing permission first, matching the previous behaviour.
     */
    private void dispatchItems(CommandSender sender, List<String> arguments) {
        if (cultivationItems != null && cultivationItems.supports(arguments)) {
            if (!sender.hasPermission("omnipet.admin.cultivation")) {
                sender.sendMessage("OmniPet: you do not have permission to distribute cultivation items.");
                return;
            }
            cultivationItems.command(sender, arguments);
            return;
        }
        if (actionItems == null) {
            sender.sendMessage("OmniPet: incubation action items are not available yet.");
            return;
        }
        actionItems.command(sender, arguments);
    }

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        return CommandSuggestions.suggest(
                OmniPetCommandTree.root(), sender::hasPermission, sender instanceof Player, args,
                // In-memory roster read, so it is safe per keystroke on the main thread. Suggesting a
                // UUID would not be, which is why only names are offered.
                () -> org.bukkit.Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .toList());
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
