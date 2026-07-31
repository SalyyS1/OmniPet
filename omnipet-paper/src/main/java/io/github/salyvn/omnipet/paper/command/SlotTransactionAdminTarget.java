package io.github.salyvn.omnipet.paper.command;

import java.util.UUID;

import org.bukkit.command.CommandSender;

import io.github.salyvn.omnipet.core.economy.SlotReconciliationDecision;

/** Command-facing boundary for asynchronous slot transaction administration. */
public interface SlotTransactionAdminTarget {
    void list(CommandSender sender, int limit, String cursor);

    void reconcile(CommandSender sender, UUID transactionId, SlotReconciliationDecision decision);
}
