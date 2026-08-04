package io.github.salyvn.omnipet.paper.command;

import java.util.Collection;
import java.util.Arrays;
import java.util.List;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;

/**
 * {@code /petadmin ...}, the same administration as {@code /pet admin ...} without the second word.
 *
 * <p>Routes into the {@link AdminCommandRouter} that {@code /pet} already builds, so the two spellings
 * cannot drift: there is one implementation and one permission check per area, not two parallel ones.
 * {@code /pet admin ...} keeps working, so existing scripts, macros, and documentation are unaffected.
 *
 * <p>Tokens are prefixed with {@code admin} before routing, because every parser under administration was
 * written against the {@code /pet admin ...} shape and expects to see that literal. Translating here rather
 * than teaching each parser a second shape keeps one contract.
 */
public final class OmniPetAdminCommand implements BasicCommand {
    public static final String NAME = "petadmin";
    public static final List<String> ALIASES = List.of("opadmin");

    private final OmniPetCommand delegate;

    public OmniPetAdminCommand(OmniPetCommand delegate) {
        this.delegate = java.util.Objects.requireNonNull(delegate, "command delegate");
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        List<String> tokens = new java.util.ArrayList<>();
        tokens.add("admin");
        tokens.addAll(Arrays.asList(args == null ? new String[0] : args));
        if (delegate.adminRouter().route(sender, tokens)) return;
        sender.sendMessage("OmniPet: unknown admin command. Run /pet help for the list you can use.");
    }

    /**
     * Suggests from the same tree {@code /pet} uses, walked one level in.
     *
     * <p>Reusing the tree is what keeps {@code /petadmin} tab-complete, {@code /pet admin} tab-complete,
     * and {@code /pet help} from disagreeing about which commands exist.
     */
    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        String[] tokens = new String[(args == null ? 0 : args.length) + 1];
        tokens[0] = "admin";
        if (args != null) System.arraycopy(args, 0, tokens, 1, args.length);
        return CommandSuggestions.suggest(
                OmniPetCommandTree.root(), sender::hasPermission, sender instanceof Player, tokens,
                () -> org.bukkit.Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
    }

    /** Visible to anyone holding any administration permission; each area still checks its own. */
    @Override
    public boolean canUse(CommandSender sender) {
        return sender.hasPermission(AdminPetCommandParser.MANAGE_PET_PERMISSION)
                || sender.hasPermission(HatchAdminCommandParser.INSPECT_PERMISSION)
                || sender.hasPermission(HatchAdminCommandParser.MANAGE_PERMISSION)
                || sender.hasPermission("omnipet.admin.reload")
                || sender.hasPermission("omnipet.admin.item")
                || sender.hasPermission("omnipet.admin.egg")
                || sender.hasPermission("omnipet.admin.petgive")
                || sender.hasPermission("omnipet.admin.cultivation")
                || sender.hasPermission("omnipet.admin.skill")
                || sender.hasPermission("omnipet.admin.release")
                || sender.hasPermission(SlotTransactionAdminCommandParser.PERMISSION);
    }

    @Override
    public String permission() { return null; }
}
