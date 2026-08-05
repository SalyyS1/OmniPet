package io.github.salyvn.omnipet.paper.player;

import java.util.Objects;
import java.util.function.BooleanSupplier;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import io.github.salyvn.omnipet.paper.text.MessageKey;
import io.github.salyvn.omnipet.paper.text.Messages;

/**
 * Tells a first-time player the plugin exists.
 *
 * <p>Nothing else did. Every other join handler recovers durable state, and discovery relied on a player
 * already suspecting there was something to type: {@code /pet} is tab-completable, but only once you know
 * to reach for it.
 *
 * <p>Two lines, sent once, and only to someone who has genuinely never played here. Repeating it on every
 * join would make the plugin the thing that spams the chat on arrival, which is a worse first impression
 * than saying nothing.
 */
public final class PlayerOnboardingListener implements Listener {
    private final BooleanSupplier enabled;

    public PlayerOnboardingListener(BooleanSupplier enabled) {
        this.enabled = Objects.requireNonNull(enabled, "greeting toggle");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // hasPlayedBefore is false only on a genuine first join, so this cannot repeat for a regular.
        if (player.hasPlayedBefore() || !enabled.getAsBoolean()) return;
        player.sendMessage(Messages.line(MessageKey.ONBOARDING_WELCOME));
        player.sendMessage(Messages.line(MessageKey.ONBOARDING_WELCOME_HINT));
    }
}
