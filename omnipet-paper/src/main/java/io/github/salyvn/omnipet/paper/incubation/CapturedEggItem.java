package io.github.salyvn.omnipet.paper.incubation;

import java.util.Objects;

import org.bukkit.inventory.ItemStack;

import io.github.salyvn.omnipet.core.incubation.EggItemIdentity;

public record CapturedEggItem(ItemStack stack, String eggId, EggItemIdentity identity) {
    public CapturedEggItem {
        stack = Objects.requireNonNull(stack, "captured egg stack");
        eggId = Objects.requireNonNull(eggId, "captured egg id");
        identity = Objects.requireNonNull(identity, "captured egg identity");
    }
}
