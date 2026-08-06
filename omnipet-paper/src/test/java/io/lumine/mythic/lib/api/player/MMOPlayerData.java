package io.lumine.mythic.lib.api.player;

/**
 * Stand-in for MythicLib's {@code MMOPlayerData}, shaped to the real ABI of build 106.
 *
 * <p>Only what the buff adapter reflects on. The important detail is the pair of lookups: the real
 * {@code get(UUID)} <em>throws</em> for a player MythicLib has not set up yet, while {@code getOrNull}
 * answers null. The adapter treats a throw as an ABI mismatch and quarantines itself for every owner, so
 * which one it binds decides whether one early player disables pet stats server-wide.
 */
public final class MMOPlayerData {
    private static final java.util.Map<java.util.UUID, MMOPlayerData> DATA =
            new java.util.LinkedHashMap<>();

    private final io.lumine.mythic.lib.api.stat.StatMap statMap = new io.lumine.mythic.lib.api.stat.StatMap();

    private MMOPlayerData() {}

    /** Registers a player as loaded, mirroring what MythicLib's own join handler does. */
    public static MMOPlayerData load(java.util.UUID playerId) {
        return DATA.computeIfAbsent(playerId, ignored -> new MMOPlayerData());
    }

    public static void reset() {
        DATA.clear();
    }

    /** Throws for an unloaded player, exactly as the real one does. */
    public static MMOPlayerData get(java.util.UUID playerId) {
        return java.util.Objects.requireNonNull(DATA.get(playerId), "Player data not loaded");
    }

    /** Answers null for an unloaded player, which is what makes the adapter survive one. */
    public static MMOPlayerData getOrNull(java.util.UUID playerId) {
        return DATA.get(playerId);
    }

    public io.lumine.mythic.lib.api.stat.StatMap getStatMap() {
        return statMap;
    }
}
