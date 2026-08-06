package io.lumine.mythic.lib.api.stat.modifier;

import io.lumine.mythic.lib.api.player.EquipmentSlot;
import io.lumine.mythic.lib.player.modifier.ModifierSource;
import io.lumine.mythic.lib.player.modifier.ModifierType;

/** Stand-in for MythicLib's {@code StatModifier}, with build 106's UUID-keyed constructor. */
public final class StatModifier {
    private final java.util.UUID key;
    private final String stat;
    private final double value;
    private final ModifierType type;

    public StatModifier(
            java.util.UUID key,
            String name,
            String stat,
            double value,
            ModifierType type,
            EquipmentSlot slot,
            ModifierSource source) {
        this.key = key;
        this.stat = stat;
        this.value = value;
        this.type = type;
    }

    public java.util.UUID key() { return key; }

    public String getStat() { return stat; }

    public double getValue() { return value; }

    public ModifierType getType() { return type; }
}
