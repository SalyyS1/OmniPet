package io.github.salyvn.omnipet.paper.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.kyori.adventure.text.Component;

import io.github.salyvn.omnipet.paper.text.MessageCatalog;
import io.github.salyvn.omnipet.paper.text.Messages;

/**
 * The first-join greeting.
 *
 * <p>The two cases that matter are opposites: a brand-new player has no other way to learn the plugin
 * exists, and a returning player must never be greeted again — a plugin that re-introduces itself on
 * every login is worse than one that says nothing.
 */
class PlayerOnboardingListenerTest {
    private final List<Component> sent = new ArrayList<>();

    @BeforeAll
    static void bindMessageCatalog() {
        Messages.bind(MessageCatalog.defaults());
    }

    @AfterAll
    static void unbindMessageCatalog() {
        Messages.unbind();
    }

    @Test
    void aFirstTimePlayerIsToldTheCommandExists() {
        new PlayerOnboardingListener(() -> true).onJoin(join(player(false)));

        assertEquals(2, sent.size(), "a name for the feature and the command to reach it");
        assertTrue(Messages.plain(sent.get(1)).contains("/pet"),
                "the greeting is only useful if it names the command: " + Messages.plain(sent.get(1)));
    }

    @Test
    void aReturningPlayerIsNotGreetedAgain() {
        new PlayerOnboardingListener(() -> true).onJoin(join(player(true)));

        assertEquals(List.of(), sent);
    }

    @Test
    void theOperatorSwitchSilencesItEvenOnAFirstJoin() {
        // For a server that runs its own introduction and does not want a second one.
        new PlayerOnboardingListener(() -> false).onJoin(join(player(false)));

        assertEquals(List.of(), sent);
    }

    private static PlayerJoinEvent join(Player player) {
        return new PlayerJoinEvent(player, Component.empty());
    }

    /** A player stub: only {@code hasPlayedBefore} and {@code sendMessage} are exercised. */
    private Player player(boolean playedBefore) {
        return (Player) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[] {Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "hasPlayedBefore" -> playedBefore;
                    case "sendMessage" -> {
                        if (args != null && args.length == 1 && args[0] instanceof Component line) {
                            sent.add(line);
                        }
                        yield null;
                    }
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" -> "player";
                    default -> defaultValue(method.getReturnType());
                });
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == void.class) return null;
        return 0;
    }
}
