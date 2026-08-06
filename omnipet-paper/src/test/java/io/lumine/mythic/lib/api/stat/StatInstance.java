package io.lumine.mythic.lib.api.stat;

import io.lumine.mythic.lib.api.stat.modifier.StatModifier;

/** Stand-in for MythicLib's {@code StatInstance}, keyed by UUID exactly as build 106 is. */
public final class StatInstance {
    private final String stat;
    private final java.util.Map<java.util.UUID, StatModifier> modifiers = new java.util.LinkedHashMap<>();

    StatInstance(String stat) {
        this.stat = stat;
    }

    public String getStat() {
        return stat;
    }

    public StatModifier getModifier(java.util.UUID key) {
        return modifiers.get(key);
    }

    public void removeModifier(java.util.UUID key) {
        modifiers.remove(key);
    }

    public void registerModifier(StatModifier modifier) {
        modifiers.put(modifier.key(), modifier);
    }

    /** The modifiers currently installed, so a test can assert what actually landed. */
    public java.util.Collection<StatModifier> installed() {
        return java.util.List.copyOf(modifiers.values());
    }
}
