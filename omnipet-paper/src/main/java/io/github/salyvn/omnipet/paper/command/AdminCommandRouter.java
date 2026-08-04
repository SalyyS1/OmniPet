package io.github.salyvn.omnipet.paper.command;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;

import org.bukkit.command.CommandSender;

/**
 * Everything under {@code admin}, reachable identically from {@code /pet admin ...} and {@code /petadmin
 * ...}.
 *
 * <p>Extracted from a 653-line {@code execute} method where each area was an inline {@code if} repeating
 * the same match-permission-availability shape. Two entry points routing into one router is what keeps the
 * two spellings from drifting: there is one implementation, not two parallel chains.
 *
 * <p>Areas whose arguments need real parsing — hatch administration, slot transactions — keep their own
 * parser and are matched after the simple areas, exactly as before, so a literal cannot be captured by the
 * wrong branch.
 */
public final class AdminCommandRouter {
    private final List<AdminArea> areas;
    private final HatchAdminRouter hatchAdmin;
    private final SlotTransactionAdminRouter transactions;
    private final BooleanSupplier reloadRuntime;
    /**
     * Opens the transaction menu, when a menu is available and the sender is a player.
     *
     * <p>Set separately from the routers because the menu needs a live inventory: console keeps the chat
     * form, which is also the only form that can resume a scan from a pasted cursor.
     */
    private volatile java.util.function.Consumer<org.bukkit.entity.Player> transactionMenu;

    /** Wires the transaction menu. Called once from {@code onEnable}. */
    public void bindTransactionMenu(java.util.function.Consumer<org.bukkit.entity.Player> target) {
        this.transactionMenu = target;
    }

    public AdminCommandRouter(
            List<AdminArea> areas,
            HatchAdminRouter hatchAdmin,
            SlotTransactionAdminRouter transactions,
            BooleanSupplier reloadRuntime) {
        this.areas = List.copyOf(Objects.requireNonNull(areas, "admin areas"));
        this.hatchAdmin = Objects.requireNonNull(hatchAdmin, "hatch admin router");
        this.transactions = Objects.requireNonNull(transactions, "transaction router");
        this.reloadRuntime = Objects.requireNonNull(reloadRuntime, "reload runtime");
    }

    /**
     * Routes tokens that already start with {@code admin}.
     *
     * @param arguments the full token list, {@code admin} included, so the existing parsers see the shape
     *     they were written against
     * @return whether this router handled the input
     */
    public boolean route(CommandSender sender, List<String> arguments) {
        Objects.requireNonNull(sender, "sender");
        List<String> tokens = arguments == null ? List.of() : arguments;

        if (menu(sender, tokens)) return true;
        Optional<AdminArea> area = area(tokens);
        if (area.isPresent()) {
            area.get().dispatch(sender, tokens.subList(2, tokens.size()));
            return true;
        }
        if (hatchAdmin.route(sender, tokens)) return true;
        if (transactions.route(sender, tokens)) return true;
        return reload(sender, tokens);
    }

    /** The simple area a token list names, if any. */
    private Optional<AdminArea> area(List<String> tokens) {
        if (tokens.size() < 2 || !tokens.get(0).equalsIgnoreCase("admin")) return Optional.empty();
        return areas.stream()
                .filter(candidate -> candidate.literal().equalsIgnoreCase(tokens.get(1)))
                .findFirst();
    }

    /**
     * {@code admin transactions menu}: the same pending list, as a clickable inventory.
     *
     * <p>Matched before the transaction parser, which would read {@code menu} as an invalid limit. A
     * console sender is told to use the chat form rather than being silently ignored.
     */
    private boolean menu(CommandSender sender, List<String> tokens) {
        if (tokens.size() != 3
                || !tokens.get(0).equalsIgnoreCase("admin")
                || !tokens.get(1).equalsIgnoreCase("transactions")
                || !tokens.get(2).equalsIgnoreCase("menu")) {
            return false;
        }
        if (!sender.hasPermission(SlotTransactionAdminCommandParser.PERMISSION)) {
            sender.sendMessage("OmniPet: you do not have permission to reconcile slot transactions.");
            return true;
        }
        var target = transactionMenu;
        if (target == null) {
            sender.sendMessage("OmniPet: the transaction menu is not available yet.");
            return true;
        }
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            sender.sendMessage("OmniPet: the transaction menu needs a player; "
                    + "use /pet admin transactions [limit] [cursor] from console.");
            return true;
        }
        target.accept(player);
        return true;
    }

    private boolean reload(CommandSender sender, List<String> tokens) {
        if (tokens.size() != 2
                || !tokens.get(0).equalsIgnoreCase("admin")
                || !tokens.get(1).equalsIgnoreCase("reload")) {
            return false;
        }
        if (!sender.hasPermission("omnipet.admin.reload")) {
            sender.sendMessage("OmniPet: you do not have permission to reload definitions.");
            return true;
        }
        sender.sendMessage(reloadRuntime.getAsBoolean()
                ? "OmniPet: config and definitions reloaded; online player reconciliation queued."
                : "OmniPet: reload failed; the previous registry remains active.");
        return true;
    }
}
