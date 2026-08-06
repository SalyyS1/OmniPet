package io.lumine.mythic.lib.api.stat;

/**
 * Stand-in for MythicLib's {@code StatMap}.
 *
 * <p>{@link #getInstance} is a {@code computeIfAbsent} in the real thing, and that is the whole reason pet
 * stats failed silently: it never returns null, so a stat ID MythicLib has never registered still yields a
 * live instance, a modifier installs onto it successfully, and nothing anywhere reads it. Reproduced here so
 * a test can prove the adapter no longer relies on a null.
 */
public final class StatMap {
    private final java.util.Map<String, StatInstance> instances = new java.util.LinkedHashMap<>();

    public StatInstance getInstance(String stat) {
        return instances.computeIfAbsent(stat, StatInstance::new);
    }

    /** Which stats have had an instance minted, so a test can see the orphans. */
    public java.util.Set<String> touched() {
        return java.util.Set.copyOf(instances.keySet());
    }
}
