package io.github.salyvn.omnipet.paper.feedback;

import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;

/**
 * Where feedback actually goes. The narrow seam that makes {@link FeedbackService} testable.
 *
 * <p>{@code Player} declares a dozen {@code playSound} overloads, so counting calls through a dynamic
 * proxy would be fragile and would rot on the next API change. The service therefore writes to this
 * interface: production binds {@link BukkitFeedbackOutput}, tests bind a recording fake and assert
 * against counts. Without this seam, "disabled config produces no sound" could only be checked by
 * grepping the source for a {@code playSound} call, which asserts nothing about behavior.
 */
public interface FeedbackOutput {
    /** Plays to the acting player alone. Never to a location, and never to nearby players. */
    void sound(Player player, ResolvedSound sound);

    void actionBar(Player player, Component text);
}
