package io.github.salyvn.omnipet.paper.command;

import java.util.UUID;

import org.bukkit.command.CommandSender;

/** Command-facing boundary for asynchronous offline incubation administration. */
public interface HatchAdminCommandTarget {
    void inspect(CommandSender sender, UUID playerId);

    void reduce(CommandSender sender, UUID playerId, UUID incubationId, long millis, UUID actionId);

    void setRemaining(CommandSender sender, UUID playerId, UUID incubationId, long millis, UUID actionId);

    void complete(CommandSender sender, UUID playerId, UUID incubationId, UUID actionId);

    void cancel(CommandSender sender, UUID playerId, UUID incubationId, UUID actionId);
}
