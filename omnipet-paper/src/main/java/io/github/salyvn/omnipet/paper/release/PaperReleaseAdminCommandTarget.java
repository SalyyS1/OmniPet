package io.github.salyvn.omnipet.paper.release;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;

import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public final class PaperReleaseAdminCommandTarget {
    private final JavaPlugin plugin;
    private final ReleaseAdminCommandParser parser = new ReleaseAdminCommandParser(name -> {
        if (org.bukkit.Bukkit.getServer() == null) return java.util.Optional.empty();
        org.bukkit.entity.Player online = org.bukkit.Bukkit.getPlayerExact(name);
        return online == null ? java.util.Optional.empty() : java.util.Optional.of(online.getUniqueId());
    });
    private final ReleaseAdminController controller;
    private final Executor executor;

    public PaperReleaseAdminCommandTarget(
            JavaPlugin plugin,
            ReleaseAdminController controller,
            Executor executor) {
        this.plugin = Objects.requireNonNull(plugin, "release admin plugin");
        this.controller = Objects.requireNonNull(controller, "release admin controller");
        this.executor = Objects.requireNonNull(executor, "release admin executor");
    }

    public void command(CommandSender sender, List<String> arguments) {
        ReleaseAdminParseResult parsed = parser.parse(arguments);
        if (parsed.status() != ReleaseAdminParseResult.Status.ACCEPTED) {
            sender.sendMessage("OmniPet: " + parsed.detail());
            return;
        }
        try {
            executor.execute(() -> {
                ReleaseAdminResult result = controller.execute(parsed.command());
                if (!plugin.isEnabled()) return;
                plugin.getServer().getScheduler().runTask(plugin,
                        () -> result.messages().forEach(sender::sendMessage));
            });
        } catch (RuntimeException failure) {
            sender.sendMessage("OmniPet: release administration could not be scheduled.");
        }
    }
}
