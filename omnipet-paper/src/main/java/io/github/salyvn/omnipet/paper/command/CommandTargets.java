package io.github.salyvn.omnipet.paper.command;

import java.util.List;
import java.util.UUID;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * The seams the {@code /pet} routers dispatch through.
 *
 * <p>Each one is the narrowest view of a controller that a router needs, which is what lets the dispatch
 * tests drive every branch with a recording fake instead of a live server. They lived as loose
 * package-private types trailing {@code OmniPetCommand}; grouping them here keeps that file inside the
 * project's size limit and puts the contract in one readable place.
 *
 * <p>Not a public API. Anything outside this package talks to the controllers directly.
 */
final class CommandTargets {
    private CommandTargets() {}
}

@FunctionalInterface
interface PlayerHatchCommandTarget {
    void command(Player player, String action);
}

@FunctionalInterface
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

/** Items split by kind: a cultivation item additionally requires the cultivation permission. */
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
