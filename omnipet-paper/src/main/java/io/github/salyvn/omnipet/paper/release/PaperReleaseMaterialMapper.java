package io.github.salyvn.omnipet.paper.release;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

import org.bukkit.Material;

import io.github.salyvn.omnipet.core.release.ReleaseRewardBundle;

public final class PaperReleaseMaterialMapper {
    public List<PaperMaterialReward> map(List<ReleaseRewardBundle.InternalReward> rewards) {
        LinkedHashMap<Material, Long> totals = new LinkedHashMap<>();
        for (ReleaseRewardBundle.InternalReward reward : rewards) {
            if (!"material".equals(reward.rewardId())) {
                throw new IllegalArgumentException("unsupported internal release reward: " + reward.rewardId());
            }
            Object rawType = reward.payload().getOrDefault("type", reward.payload().get("material"));
            if (rawType == null) throw new IllegalArgumentException("material release reward has no type");
            Material material = Material.matchMaterial(String.valueOf(rawType).toUpperCase(Locale.ROOT));
            if (material == null || material == Material.AIR) {
                throw new IllegalArgumentException("unknown release reward material: " + rawType);
            }
            totals.merge(material, reward.amount(), Math::addExact);
        }
        ArrayList<PaperMaterialReward> mapped = new ArrayList<>(totals.size());
        totals.forEach((material, amount) -> mapped.add(new PaperMaterialReward(material, amount)));
        return List.copyOf(mapped);
    }
}
