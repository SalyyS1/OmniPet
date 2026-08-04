package io.github.salyvn.omnipet.paper.command;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * The player-facing branches of {@code /pet}: help, hatch, skill, and slot purchase.
 *
 * <p>Split out of the monolithic dispatcher unchanged. Each branch keeps the order it had, because
 * {@code help} in particular must be matched before any numeric parsing — otherwise {@code /pet help}
 * could be read as a request for a vault page.
 *
 * <p>The vault and hub branches stay in the command itself: they are what a bare {@code /pet} and
 * {@code /pet <page>} fall through to, so they belong with the fall-through rather than here.
 */
public final class PlayerCommandRouter {
    private final PlayerHatchCommandTarget hatches;
    private final PlayerSkillCommandTarget skills;
    private final SlotPurchaseTarget slotPurchases;

    public PlayerCommandRouter(
            PlayerHatchCommandTarget hatches,
            PlayerSkillCommandTarget skills,
            SlotPurchaseTarget slotPurchases) {
        this.hatches = hatches;
        this.skills = skills;
        this.slotPurchases = slotPurchases;
    }

    /** @return whether these tokens were a player branch, handled or refused */
    public boolean route(CommandSender sender, Player player, List<String> arguments) {
        Objects.requireNonNull(sender, "sender");
        List<String> tokens = arguments == null ? List.of() : arguments;
        if (tokens.isEmpty()) return false;
        return switch (tokens.getFirst().toLowerCase(java.util.Locale.ROOT)) {
            case "help" -> help(sender, player, tokens);
            case "hatch" -> hatch(sender, player, tokens);
            case "skill" -> skill(sender, player, tokens);
            case "slot" -> slot(sender, player, tokens);
            default -> false;
        };
    }

    /** Matched before any numeric parsing, so {@code help} can never be read as a vault page. */
    private static boolean help(CommandSender sender, Player player, List<String> tokens) {
        CommandHelpRenderer.send(sender, tokens, player != null);
        return true;
    }

    private boolean hatch(CommandSender sender, Player player, List<String> tokens) {
        if (player == null) {
            sender.sendMessage("OmniPet: a player is required to hatch an egg.");
            return true;
        }
        if (hatches == null) {
            sender.sendMessage("OmniPet: hatch flow is not available yet.");
            return true;
        }
        if (tokens.size() > 2) {
            sender.sendMessage("OmniPet: use /pet hatch [main|off|claim|refresh|use-main|use-off].");
            return true;
        }
        hatches.command(player, tokens.size() == 1 ? null : tokens.get(1));
        return true;
    }

    private boolean skill(CommandSender sender, Player player, List<String> tokens) {
        if (player == null) {
            sender.sendMessage("OmniPet: a player is required to cast a pet skill.");
            return true;
        }
        if (skills == null) {
            sender.sendMessage("OmniPet: active pet skills are not available yet.");
            return true;
        }
        if (tokens.size() != 3) {
            sender.sendMessage("OmniPet: use /pet skill <pet-uuid> <binding-id>.");
            return true;
        }
        try {
            skills.cast(player, UUID.fromString(tokens.get(1)), tokens.get(2));
        } catch (IllegalArgumentException invalid) {
            sender.sendMessage("OmniPet: pet UUID is invalid.");
        }
        return true;
    }

    private boolean slot(CommandSender sender, Player player, List<String> tokens) {
        if (player == null) {
            sender.sendMessage("OmniPet: a player is required to buy active slots.");
            return true;
        }
        int returnPage = 1;
        if (tokens.size() == 2) {
            try {
                returnPage = Integer.parseInt(tokens.get(1));
            } catch (NumberFormatException ignored) {
                sender.sendMessage("OmniPet: slot return page must be a positive integer.");
                return true;
            }
        } else if (tokens.size() != 1) {
            sender.sendMessage("OmniPet: use /pet slot.");
            return true;
        }
        if (returnPage < 1) {
            sender.sendMessage("OmniPet: slot return page must be a positive integer.");
            return true;
        }
        slotPurchases.open(player, returnPage);
        return true;
    }

    /** The slot purchase entry point, kept as a seam so the router stays testable without it. */
    @FunctionalInterface
    public interface SlotPurchaseTarget {
        void open(Player player, int returnPage);
    }
}
