package io.github.salyvn.omnipet.paper.release;

import org.bukkit.Material;

public record PaperMaterialReward(Material material, long amount) {
    public PaperMaterialReward {
        if (material == null || material == Material.AIR) {
            throw new IllegalArgumentException("release reward material must be a deliverable item");
        }
        if (amount < 1 || amount > 1_000_000_000L) {
            throw new IllegalArgumentException("release material amount is outside the supported range");
        }
    }
}
