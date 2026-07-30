package io.github.salyvn.omnipet.command;

import static io.papermc.paper.command.brigadier.Commands.argument;
import static io.papermc.paper.command.brigadier.Commands.literal;

import org.bukkit.entity.Player;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;

import io.github.salyvn.omnipet.OmniPetPlugin;
import io.github.salyvn.omnipet.api.PetPlayer;
import io.github.salyvn.omnipet.gui.PetMenu;
import io.papermc.paper.command.brigadier.CommandSourceStack;

public class OmniPetCommands {
	public static final SimpleCommandExceptionType NOT_PLAYER = new SimpleCommandExceptionType(new LiteralMessage("Executor is not a player"));

	/** Accept legacy grants during the migration window without changing new permission names. */
	public static boolean hasPermission(CommandSender sender, String permission) {
		return sender.hasPermission(permission)
				|| sender.hasPermission(permission.replace("omnipet", "passivepet"));
	}

	public static LiteralArgumentBuilder<CommandSourceStack> root(String literal) {
		return literal(literal).requires(s -> hasPermission(s.getSender(), "omnipet.general"))
				.then(OmniPetAdminCommands.reload())
				.then(OmniPetAdminCommands.pet())
				.then(OmniPetAdminCommands.egg())
				.then(OmniPetAdminCommands.inspect())
				.then(OmniPetAdminCommands.explore())
				.then(OmniPetAdminCommands.item())
				.then(argument("page", IntegerArgumentType.integer(1))
						.executes(ctx -> openMenu(ctx, IntegerArgumentType.getInteger(ctx, "page"))))
				.executes(ctx -> openMenu(ctx, 1));
	}

	private static int openMenu(CommandContext<CommandSourceStack> ctx, int page) throws CommandSyntaxException {
		if (!(ctx.getSource().getExecutor() instanceof Player player)) throw NOT_PLAYER.create();
		OmniPetPlugin plugin = JavaPlugin.getPlugin(OmniPetPlugin.class);
		PetPlayer data = plugin.player(player);
		PetMenu menu = new PetMenu(plugin, data, plugin.getGuiConfig(), plugin.getGlobalConfig(), plugin.getLanguageConfig(), page - 1);
		player.openInventory(menu.getInventory());
		return 0;
	}
}
