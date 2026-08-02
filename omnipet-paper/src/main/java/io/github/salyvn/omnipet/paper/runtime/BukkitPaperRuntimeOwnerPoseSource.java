package io.github.salyvn.omnipet.paper.runtime;

import java.util.Optional;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import io.github.salyvn.omnipet.core.runtime.RuntimeVector;

public final class BukkitPaperRuntimeOwnerPoseSource implements PaperRuntimeOwnerPoseSource {
    @Override
    public Optional<PaperRuntimeOwnerPose> find(UUID ownerId) {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("runtime owner pose lookup must run on the Paper main thread");
        }
        Player player = Bukkit.getPlayer(ownerId);
        if (player == null || !player.isOnline() || !player.isValid()) return Optional.empty();
        Location location = player.getLocation();
        Vector direction = location.getDirection();
        return Optional.of(new PaperRuntimeOwnerPose(
                new RuntimeVector(location.getX(), location.getY(), location.getZ()),
                new RuntimeVector(direction.getX(), direction.getY(), direction.getZ()),
                location.getYaw(),
                location.getPitch(),
                !player.isDead()));
    }
}
