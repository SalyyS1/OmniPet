package io.github.salyvn.omnipet.paper.command;

import java.util.List;

import org.bukkit.command.CommandSender;

/**
 * {@code admin transactions} and {@code admin reconcile}: bounded paging over the slot-purchase journal,
 * and applying explicit operator evidence to one transaction.
 *
 * <p>Split out of the monolithic dispatcher unchanged. Both literals share one permission and one parser,
 * so they belong together rather than as two {@link AdminArea} entries.
 *
 * <p>The continuation cursor stays an opaque token the operator re-pastes verbatim. That is deliberate: it
 * is what lets a scan be resumed from a log line or a ticket, which no menu can replace.
 */
public final class SlotTransactionAdminRouter {
    private final SlotTransactionAdminTarget target;

    public SlotTransactionAdminRouter(SlotTransactionAdminTarget target) {
        this.target = target;
    }

    /** @return whether the tokens were transaction administration, handled or refused */
    public boolean route(CommandSender sender, List<String> arguments) {
        SlotTransactionAdminCommandParser.Result result =
                SlotTransactionAdminCommandParser.parse(arguments);
        if (result instanceof SlotTransactionAdminCommandParser.NotMatched) return false;
        if (!sender.hasPermission(SlotTransactionAdminCommandParser.PERMISSION)) {
            sender.sendMessage("OmniPet: you do not have permission to reconcile slot transactions.");
            return true;
        }
        if (target == null) {
            sender.sendMessage("OmniPet: slot transaction administration is not available yet.");
            return true;
        }
        if (result instanceof SlotTransactionAdminCommandParser.ListPending pending) {
            target.list(sender, pending.limit(), pending.cursor());
        } else if (result instanceof SlotTransactionAdminCommandParser.Reconcile reconcile) {
            target.reconcile(sender, reconcile.transactionId(), reconcile.decision());
        } else {
            sender.sendMessage("OmniPet: use /pet admin transactions [limit] [cursor] or "
                    + "/pet admin reconcile <uuid> <charge|no-charge|refund|sync>.");
        }
        return true;
    }
}
