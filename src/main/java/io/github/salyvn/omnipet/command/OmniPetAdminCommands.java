package io.github.salyvn.omnipet.command;

import static io.papermc.paper.command.brigadier.Commands.argument;
import static io.papermc.paper.command.brigadier.Commands.literal;
import static net.kyori.adventure.text.Component.text;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Collection;
import java.util.List;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;

import io.github.salyvn.omnipet.OmniPetPlugin;
import io.github.salyvn.omnipet.api.Egg;
import io.github.salyvn.omnipet.api.EggType;
import io.github.salyvn.omnipet.api.OmniPet;
import io.github.salyvn.omnipet.api.Pet;
import io.github.salyvn.omnipet.api.PetPlayer;
import io.github.salyvn.omnipet.api.PetType;
import io.github.salyvn.omnipet.impl.PetPlayerImpl;
import io.github.salyvn.omnipet.impl.component.GeneralComponent;
import io.github.salyvn.omnipet.impl.item.EggItemImpl;
import io.github.salyvn.omnipet.impl.item.ItemIdentifierImpl;
import io.github.salyvn.omnipet.impl.item.PetEvolverItemImpl;
import io.github.salyvn.omnipet.impl.item.PetFoodItemImpl;
import io.github.salyvn.omnipet.impl.item.PetHatcherItemImpl;
import io.github.salyvn.omnipet.utils.ParseUtils;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.PlayerProfileListResolver;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class OmniPetAdminCommands {
	public static final SimpleCommandExceptionType PET_NOT_FOUND = new SimpleCommandExceptionType(new LiteralMessage("Pet not found!"));
	public static final DynamicCommandExceptionType PET_OUT_OF_BOUNDS = new DynamicCommandExceptionType(size -> new LiteralMessage("This player only have %s pets".formatted(size)));

	public static LiteralArgumentBuilder<CommandSourceStack> reload() {
		return literal("reload").requires(s -> OmniPetCommands.hasPermission(s.getSender(), "omnipet.admin.reload")).executes(ctx -> {
			OmniPetPlugin plugin = JavaPlugin.getPlugin(OmniPetPlugin.class);
			plugin.reloadData();
			ctx.getSource().getSender().sendMessage(text("Reloaded plugin data!").color(NamedTextColor.YELLOW));
			return 1;
		});
	}

	public static LiteralArgumentBuilder<CommandSourceStack> inspect() {
		return literal("inspect").requires(s -> OmniPetCommands.hasPermission(s.getSender(), "omnipet.admin.inspect"))
				.then(argument("players", ArgumentTypes.playerProfiles())
						.executes(ctx -> {
							OmniPet api = JavaPlugin.getPlugin(OmniPetPlugin.class);
							CommandSender sender = ctx.getSource().getSender();
							Collection<PlayerProfile> profiles = ctx
									.getArgument("players", PlayerProfileListResolver.class)
									.resolve(ctx.getSource());

							for (PlayerProfile profile : profiles) {
								PetPlayer data = api.player(profile.getId());
								if (data == null) data = api.loadData(profile.getId());
								Component name = data.player() != null
										? data.player().displayName()
										: profile.getName() != null ? text(profile.getName())
										: text(profile.getId().toString());

								sender.sendMessage(text("Inspecting player ").append(name).append(text(": ")));

								if (data.currentEgg() != null) {
									sender.sendMessage(text()
											.append(text("* ").color(NamedTextColor.GRAY))
											.append(text("Egg: "))
											.append(text("hatching").color(NamedTextColor.GREEN)));
									sender.sendMessage(text()
											.append(text("  - ").color(NamedTextColor.GRAY))
											.append(text("Will be hatched on "))
											.append(text(ZonedDateTime.now().plus(data.currentEgg().timeLeft()).format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT))).color(NamedTextColor.YELLOW)));
									sender.sendMessage(text()
											.append(text("  - ").color(NamedTextColor.GRAY))
											.append(text("Time left: "))
											.append(text(ParseUtils.toString(data.currentEgg().timeLeft())).color(NamedTextColor.YELLOW)));
								} else {
									sender.sendMessage(text()
											.append(text("* ").color(NamedTextColor.GRAY))
											.append(text("Egg: "))
											.append(text("not hatching").color(NamedTextColor.RED)));
								}

								sender.sendMessage(text()
										.append(text("* ").color(NamedTextColor.GRAY))
										.append(text("Pets "))
										.append(text("(").color(NamedTextColor.DARK_GRAY))
										.append(text("%d".formatted(data.pets().size())))
										.append(text(")").color(NamedTextColor.DARK_GRAY)));

								for (Pet pet : data.pets()) {
									String id = api.pets().inverse().get(pet.type());
									sender.sendMessage(text()
											.append(text("  - ").color(NamedTextColor.GRAY))
											.append(text(id).color(NamedTextColor.YELLOW)));
								}
							}

							return profiles.size();
						}));
	}

	public static LiteralArgumentBuilder<CommandSourceStack> explore() {
		return literal("explore").requires(s -> OmniPetCommands.hasPermission(s.getSender(), "omnipet.admin.explore"))
				.then(argument("player", ArgumentTypes.player())
						.then(argument("path", StringArgumentType.greedyString()).executes(ctx -> explore(ctx, true)))
						.executes(ctx -> explore(ctx, false)));
	}

	private static int explore(CommandContext<CommandSourceStack> ctx, boolean hasPath) throws CommandSyntaxException {
		OmniPet api = JavaPlugin.getPlugin(OmniPetPlugin.class);
		Player p = ctx.getArgument("player", PlayerSelectorArgumentResolver.class).resolve(ctx.getSource()).getFirst();
		String path = hasPath ? StringArgumentType.getString(ctx, "path") : "";
		PetPlayer data = api.player(p);
		if (!(data instanceof PetPlayerImpl impl)) return 0;
		CommandSender sender = ctx.getSource().getSender();
		CodecExplorer.explore(PetPlayerImpl.CODEC, impl, path, sender, "/pets explore %s".formatted(p == sender ? "@s" : p.getName()));
		return 1;
	}

	public static LiteralArgumentBuilder<CommandSourceStack> pet() {
		return literal("pet").requires(s -> OmniPetCommands.hasPermission(s.getSender(), "omnipet.admin.managepet"))
				.then(literal("give").then(argument("pet", PetTypeArgumentType.petType())
						.then(argument("players", ArgumentTypes.players()).executes(ctx -> {
							List<Player> players = ctx.getArgument("players", PlayerSelectorArgumentResolver.class).resolve(ctx.getSource());
							PetType type = PetTypeArgumentType.getPetType(ctx, "pet");
							return givePet(ctx, type, players);
						}))
						.executes(ctx -> {
							if (!(ctx.getSource().getExecutor() instanceof Player player)) throw OmniPetCommands.NOT_PLAYER.create();
							PetType type = PetTypeArgumentType.getPetType(ctx, "pet");
							return givePet(ctx, type, List.of(player));
						})))
				.then(literal("take").then(argument("slot", IntegerArgumentType.integer(0))
						.then(argument("player", ArgumentTypes.player()).executes(ctx -> {
							Player player = ctx.getArgument("player", PlayerSelectorArgumentResolver.class).resolve(ctx.getSource()).getFirst();
							return takePet(ctx, player);
						}))
						.executes(ctx -> {
							if (!(ctx.getSource().getExecutor() instanceof Player player)) throw OmniPetCommands.NOT_PLAYER.create();
							return takePet(ctx, player);
						})));
	}

	private static int givePet(CommandContext<CommandSourceStack> ctx, PetType type, List<Player> players) {
		OmniPet api = JavaPlugin.getPlugin(OmniPetPlugin.class);
		String id = api.pets().inverse().get(type);
		for (Player player : players) api.player(player).addPet(type.createDefault());
		ctx.getSource().getSender().sendMessage(text("Gave pet ")
				.append(text(id))
				.append(text(" to "))
				.append(players.size() != 1 ? text("%d players".formatted(players.size())) : players.get(0).displayName()));
		return players.size();
	}

	private static int takePet(CommandContext<CommandSourceStack> ctx, Player player) throws CommandSyntaxException {
		OmniPet api = JavaPlugin.getPlugin(OmniPetPlugin.class);
		PetPlayer data = api.player(player);
		int index = IntegerArgumentType.getInteger(ctx, "slot");
		if (index < 0 || index >= data.pets().size()) throw PET_OUT_OF_BOUNDS.create(data.pets().size());
		Pet pet = data.pets().get(index);
		data.removePet(pet);
		Component name = pet.component(GeneralComponent.class) != null
				? pet.component(GeneralComponent.class).name()
				: text(api.pets().inverse().get(pet.type()));
		ctx.getSource().getSender().sendMessage(text("Took pet ").append(name).append(text(" from ")).append(player.displayName()));
		return 1;
	}

	public static LiteralArgumentBuilder<CommandSourceStack> egg() {
		return literal("egg").requires(s -> OmniPetCommands.hasPermission(s.getSender(), "omnipet.admin.manageegg"))
				.then(literal("set").then(argument("egg", EggTypeArgumentType.eggType())
						.then(argument("players", ArgumentTypes.players())
								.then(argument("duration", DurationArgumentType.duration())
										.executes(ctx -> {
											List<Player> players = ctx.getArgument("players", PlayerSelectorArgumentResolver.class).resolve(ctx.getSource());
											EggType type = EggTypeArgumentType.getEggType(ctx, "egg");
											Duration duration = DurationArgumentType.getDuration(ctx, "duration");
											return setEgg(ctx, type, players, duration);
										}))
								.executes(ctx -> {
									List<Player> players = ctx.getArgument("players", PlayerSelectorArgumentResolver.class).resolve(ctx.getSource());
									EggType type = EggTypeArgumentType.getEggType(ctx, "egg");
									return setEgg(ctx, type, players, null);
								}))
						.executes(ctx -> {
							if (!(ctx.getSource().getExecutor() instanceof Player player)) throw OmniPetCommands.NOT_PLAYER.create();
							EggType type = EggTypeArgumentType.getEggType(ctx, "egg");
							return setEgg(ctx, type, List.of(player), null);
						})))
				.then(literal("clear")
						.then(argument("players", ArgumentTypes.players()).executes(ctx -> {
							List<Player> players = ctx.getArgument("players", PlayerSelectorArgumentResolver.class).resolve(ctx.getSource());
							return clearEgg(ctx, players);
						}))
						.executes(ctx -> {
							if (!(ctx.getSource().getExecutor() instanceof Player player)) throw OmniPetCommands.NOT_PLAYER.create();
							return clearEgg(ctx, List.of(player));
						}));
	}

	private static int setEgg(CommandContext<CommandSourceStack> ctx, EggType type, List<Player> players, Duration duration) {
		OmniPet api = JavaPlugin.getPlugin(OmniPetPlugin.class);
		if (type.pets().isEmpty()) {
			ctx.getSource().getSender().sendMessage(text("Cannot set an egg with an empty pet pool.").color(NamedTextColor.RED));
			return 0;
		}
		String id = api.eggs().inverse().get(type);
		for (Player player : players) api.player(player).setEgg(new Egg(type, duration == null ? type.hatchDuration() : duration));
		ctx.getSource().getSender().sendMessage(text("Set egg ")
				.append(text(id))
				.append(text(" to "))
				.append(players.size() != 1 ? text("%d players".formatted(players.size())) : players.get(0).displayName()));
		return players.size();
	}

	private static int clearEgg(CommandContext<CommandSourceStack> ctx, List<Player> players) {
		OmniPet api = JavaPlugin.getPlugin(OmniPetPlugin.class);
		for (Player player : players) api.player(player).setEgg(null);
		ctx.getSource().getSender().sendMessage(text("Cleared pet eggs from ")
				.append(players.size() != 1 ? text("%d players".formatted(players.size())) : players.get(0).displayName()));
		return players.size();
	}

	public static LiteralArgumentBuilder<CommandSourceStack> item() {
		return literal("item").requires(s -> OmniPetCommands.hasPermission(s.getSender(), "omnipet.admin.item"))
				.then(literal("food").then(argument("food", PetFoodItemArgumentType.foodItem())
						.then(argument("players", ArgumentTypes.players()).executes(ctx -> {
							List<Player> players = ctx.getArgument("players", PlayerSelectorArgumentResolver.class).resolve(ctx.getSource());
							return giveFood(ctx, players);
						}))
						.executes(ctx -> {
							if (!(ctx.getSource().getExecutor() instanceof Player player)) throw OmniPetCommands.NOT_PLAYER.create();
							return giveFood(ctx, List.of(player));
						})))
				.then(literal("hatcher").then(argument("hatcher", PetHatcherItemArgumentType.hatcherItem())
						.then(argument("players", ArgumentTypes.players()).executes(ctx -> {
							List<Player> players = ctx.getArgument("players", PlayerSelectorArgumentResolver.class).resolve(ctx.getSource());
							return giveHatcher(ctx, players);
						}))
						.executes(ctx -> {
							if (!(ctx.getSource().getExecutor() instanceof Player player)) throw OmniPetCommands.NOT_PLAYER.create();
							return giveHatcher(ctx, List.of(player));
						})))
				.then(literal("evolver")
						.then(argument("players", ArgumentTypes.players()).executes(ctx -> {
							List<Player> players = ctx.getArgument("players", PlayerSelectorArgumentResolver.class).resolve(ctx.getSource());
							return giveEvolver(ctx, players);
						}))
						.executes(ctx -> {
							if (!(ctx.getSource().getExecutor() instanceof Player player)) throw OmniPetCommands.NOT_PLAYER.create();
							return giveEvolver(ctx, List.of(player));
						}))
				.then(literal("egg").then(argument("egg", EggTypeArgumentType.eggType())
						.then(argument("players", ArgumentTypes.players()).executes(ctx -> {
							List<Player> players = ctx.getArgument("players", PlayerSelectorArgumentResolver.class).resolve(ctx.getSource());
							return giveEgg(ctx, players);
						}))
						.executes(ctx -> {
							if (!(ctx.getSource().getExecutor() instanceof Player player)) throw OmniPetCommands.NOT_PLAYER.create();
							return giveEgg(ctx, List.of(player));
						})));
	}

	private static int giveFood(CommandContext<CommandSourceStack> ctx, List<Player> players) {
		ItemIdentifierImpl impl = JavaPlugin.getPlugin(OmniPetPlugin.class).getDefaultItemIdentifier();
		PetFoodItemImpl item = PetFoodItemArgumentType.getFoodItem(ctx, "food");
		String id = impl.config().get().foods().inverse().get(item);

		for (Player p : players) {
			ItemStack stack = item.build(impl.keys(), id);
			p.getInventory().addItem(stack);
		}

		ctx.getSource().getSender().sendMessage(text("Gave item to ")
				.append(players.size() != 1 ? text("%d players".formatted(players.size())) : players.get(0).displayName()));
		return players.size();
	}

	private static int giveHatcher(CommandContext<CommandSourceStack> ctx, List<Player> players) {
		ItemIdentifierImpl impl = JavaPlugin.getPlugin(OmniPetPlugin.class).getDefaultItemIdentifier();
		PetHatcherItemImpl item = PetHatcherItemArgumentType.getHatcherItem(ctx, "hatcher");
		String id = impl.config().get().hatchers().inverse().get(item);

		for (Player p : players) {
			ItemStack stack = item.build(impl.keys(), id);
			p.getInventory().addItem(stack);
		}

		ctx.getSource().getSender().sendMessage(text("Gave item to ")
				.append(players.size() != 1 ? text("%d players".formatted(players.size())) : players.get(0).displayName()));
		return players.size();
	}

	private static int giveEvolver(CommandContext<CommandSourceStack> ctx, List<Player> players) {
		ItemIdentifierImpl impl = JavaPlugin.getPlugin(OmniPetPlugin.class).getDefaultItemIdentifier();
		PetEvolverItemImpl item = impl.config().get().evolver();

		for (Player p : players) {
			ItemStack stack = item.build(impl.keys());
			p.getInventory().addItem(stack);
		}

		ctx.getSource().getSender().sendMessage(text("Gave item to ")
				.append(players.size() != 1 ? text("%d players".formatted(players.size())) : players.get(0).displayName()));
		return players.size();
	}

	private static int giveEgg(CommandContext<CommandSourceStack> ctx, List<Player> players) {
		OmniPetPlugin plugin = JavaPlugin.getPlugin(OmniPetPlugin.class);
		ItemIdentifierImpl impl = plugin.getDefaultItemIdentifier();
		EggItemImpl item = impl.config().get().egg();
		EggType eggType = EggTypeArgumentType.getEggType(ctx, "egg");

		for (Player p : players) {
			ItemStack stack = item.build(impl.keys(), eggType, plugin);
			p.getInventory().addItem(stack);
		}

		ctx.getSource().getSender().sendMessage(text("Gave item to ")
				.append(players.size() != 1 ? text("%d players".formatted(players.size())) : players.get(0).displayName()));
		return players.size();
	}
}
