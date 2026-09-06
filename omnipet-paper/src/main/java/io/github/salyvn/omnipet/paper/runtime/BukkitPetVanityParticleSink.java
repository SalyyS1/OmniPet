package io.github.salyvn.omnipet.paper.runtime;

import java.util.Objects;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import io.github.salyvn.omnipet.core.runtime.RuntimeVector;

/**
 * Draws a playing pet's vanity particles into the world so nearby players see them too.
 *
 * <p>{@code World.spawnParticle} rather than the player-scoped overload the click feedback uses: a pet
 * playing for its owner alone would be invisible to everyone standing next to it, which is the opposite of
 * a companion. The griefing risk the click path guards against does not apply here — the count is a
 * handful and the cadence is throttled per pet, so this cannot be turned into a sprayer.
 *
 * <p>Best-effort: an owner who logged off, or whose world unloaded between the tick reading their position
 * and this call, simply produces no particle rather than an error that would disturb the runtime task.
 */
public final class BukkitPetVanityParticleSink implements PetVanityParticleSink {
    private final IdlePlaySettings settings;

    public BukkitPetVanityParticleSink(IdlePlaySettings settings) {
        this.settings = Objects.requireNonNull(settings, "idle-play settings");
    }

    @Override
    public void emit(UUID ownerId, RuntimeVector position) {
        if (ownerId == null || position == null) return;
        Player owner = Bukkit.getPlayer(ownerId);
        if (owner == null || !owner.isOnline() || !owner.isValid()) return;
        owner.getWorld().spawnParticle(
                settings.particle(),
                position.x(), position.y(), position.z(),
                settings.particleCount(),
                settings.particleSpread(), settings.particleSpread(), settings.particleSpread());
    }
}
