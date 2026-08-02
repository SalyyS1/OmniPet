package io.github.salyvn.omnipet.paper.incubation.action;

import java.util.Objects;

import org.bukkit.inventory.ItemStack;

import io.github.salyvn.omnipet.core.incubation.EggItemIdentity;
import io.github.salyvn.omnipet.core.incubation.IncubationItemActionType;

public record CapturedIncubationItemAction(
        ItemStack stack,
        IncubationItemActionType type,
        long effectMillis,
        EggItemIdentity identity) {
    public CapturedIncubationItemAction {
        stack = Objects.requireNonNull(stack, "incubation action item stack");
        type = Objects.requireNonNull(type, "incubation action item type");
        identity = Objects.requireNonNull(identity, "incubation action item identity");
        if (type == IncubationItemActionType.REDUCE && effectMillis <= 0) {
            throw new IllegalArgumentException("REDUCE action item effect must be positive");
        }
        if (type == IncubationItemActionType.COMPLETE && effectMillis != 0) {
            throw new IllegalArgumentException("COMPLETE action item effect must be zero");
        }
    }
}
