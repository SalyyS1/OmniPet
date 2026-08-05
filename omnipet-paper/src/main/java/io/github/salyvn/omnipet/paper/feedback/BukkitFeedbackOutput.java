package io.github.salyvn.omnipet.paper.feedback;

import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;

/**
 * Plays feedback through the Paper API.
 *
 * <p>Both calls are player-scoped on purpose. {@code Player.playSound(Location, ...)} and
 * {@code World.playSound} would broadcast to everyone nearby, which on a spammable menu click is a
 * griefing vector rather than a feature. The sound is heard only by the player who caused it.
 */
public final class BukkitFeedbackOutput implements FeedbackOutput {
    @Override
    public void sound(Player player, ResolvedSound sound) {
        // The Player-anchored overload: audible to this player only, positioned at them.
        player.playSound(player, sound.sound(), sound.volume(), sound.pitch());
    }

    @Override
    public void actionBar(Player player, Component text) {
        player.sendActionBar(text);
    }

    @Override
    public void particle(Player player, ResolvedParticle particle) {
        // The Player-scoped overload, deliberately: World.spawnParticle would show this to everyone
        // nearby, turning a repeatable click into a way to spray effects at other players.
        player.spawnParticle(
                particle.particle(),
                player.getLocation().add(0, 1, 0),
                particle.count(),
                particle.spread(),
                particle.spread(),
                particle.spread());
    }
}
