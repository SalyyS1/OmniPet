package io.github.salyvn.omnipet.paper.command;

import java.util.List;
import java.util.Objects;

import org.bukkit.command.CommandSender;

/**
 * {@code admin hatch ...}: inspect and adjust persisted incubation, including for offline players.
 *
 * <p>Split out of the monolithic dispatcher unchanged. Its arguments need a parser rather than a literal
 * match, so it is tried after the simple areas — a bare {@code admin hatch} must not be captured by
 * something looser.
 *
 * <p>The two permissions are checked separately on purpose: reading someone's incubation state is a
 * weaker capability than changing it.
 */
public final class HatchAdminRouter {
    private final HatchAdminCommandTarget target;
    private final PlayerArgumentResolver players;

    public HatchAdminRouter(HatchAdminCommandTarget target, PlayerArgumentResolver players) {
        this.target = target;
        this.players = Objects.requireNonNull(players, "player argument resolver");
    }

    /** @return whether the tokens were hatch administration, handled or refused */
    public boolean route(CommandSender sender, List<String> arguments) {
        HatchAdminCommandParser.Result result = HatchAdminCommandParser.parse(arguments, players);
        if (result instanceof HatchAdminCommandParser.NotMatched) return false;
        dispatch(sender, result);
        return true;
    }

    private void dispatch(CommandSender sender, HatchAdminCommandParser.Result result) {
        if (result instanceof HatchAdminCommandParser.Invalid) {
            sender.sendMessage("OmniPet: use /pet admin hatch inspect <player_name>, "
                    + "/pet admin hatch reduce|set <player_name> <incubation-uuid> <millis> [action-uuid], "
                    + "or /pet admin hatch complete|cancel <player_name> <incubation-uuid> [action-uuid].");
            return;
        }
        if (result instanceof HatchAdminCommandParser.Inspect inspect) {
            if (!sender.hasPermission(HatchAdminCommandParser.INSPECT_PERMISSION)) {
                sender.sendMessage("OmniPet: you do not have permission to inspect incubation state.");
                return;
            }
            if (target == null) {
                sender.sendMessage("OmniPet: hatch administration is not available yet.");
                return;
            }
            target.inspect(sender, inspect.playerId());
            return;
        }
        if (!sender.hasPermission(HatchAdminCommandParser.MANAGE_PERMISSION)) {
            sender.sendMessage("OmniPet: you do not have permission to manage incubation state.");
            return;
        }
        if (target == null) {
            sender.sendMessage("OmniPet: hatch administration is not available yet.");
            return;
        }
        if (result instanceof HatchAdminCommandParser.Reduce reduce) {
            target.reduce(sender, reduce.playerId(), reduce.incubationId(), reduce.millis(), reduce.actionId());
        } else if (result instanceof HatchAdminCommandParser.SetRemaining set) {
            target.setRemaining(sender, set.playerId(), set.incubationId(), set.millis(), set.actionId());
        } else if (result instanceof HatchAdminCommandParser.Complete complete) {
            target.complete(sender, complete.playerId(), complete.incubationId(), complete.actionId());
        } else if (result instanceof HatchAdminCommandParser.Cancel cancel) {
            target.cancel(sender, cancel.playerId(), cancel.incubationId(), cancel.actionId());
        }
    }
}
