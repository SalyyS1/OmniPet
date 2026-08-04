package io.github.salyvn.omnipet.paper.incubation.placed;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * One egg placed in the world, and the incubation it owns.
 *
 * <p>The durable owner of a placed egg. The escrow saga is deliberately not involved: it exists to prove
 * a <em>held</em> item was paid for, and a placed egg is not a payment — it is an egg the player still
 * owns, sitting in a block instead of a slot. Keeping the two separate means placing an egg cannot reach
 * the code that holds players' paid items and money.
 *
 * <p>The record is the single source of truth. The hologram above the egg is rebuilt from it whenever the
 * chunk loads, so a crash or an unload leaves nothing to leak — there is no state in the entity that the
 * record does not already hold.
 *
 * @param eggId the catalog entry this egg hatches
 * @param itemNonce the egg item's own nonce, carried through so breaking the block returns that exact
 *     egg rather than a fresh one, and so one egg can never be placed twice
 * @param ownerId who placed it, and who the hatched pet belongs to
 * @param world the world name, kept as text so a record survives a world that is not loaded yet
 * @param remainingMillis incubation time left, decremented only while the requirement holds
 * @param itemSnapshot the serialized egg item, so break returns the item exactly as it was placed
 */
public record PlacedEggRecord(
        String eggId,
        UUID itemNonce,
        UUID ownerId,
        String world,
        int blockX,
        int blockY,
        int blockZ,
        long remainingMillis,
        long totalMillis,
        String itemSnapshot,
        Map<String, Object> extensions) {
    public PlacedEggRecord {
        eggId = required(eggId, "egg id");
        itemNonce = Objects.requireNonNull(itemNonce, "egg item nonce");
        ownerId = Objects.requireNonNull(ownerId, "owner id");
        world = required(world, "world name");
        if (remainingMillis < 0) throw new IllegalArgumentException("remaining time cannot be negative");
        if (totalMillis <= 0) throw new IllegalArgumentException("total time must be positive");
        if (remainingMillis > totalMillis) {
            throw new IllegalArgumentException("remaining time cannot exceed the total");
        }
        itemSnapshot = required(itemSnapshot, "egg item snapshot");
        extensions = extensions == null ? Map.of() : Map.copyOf(extensions);
    }

    /** The key a record is stored and looked up by: one egg per block. */
    public String key() {
        return key(world, blockX, blockY, blockZ);
    }

    /** Stable, filename-safe, and unique per block. */
    public static String key(String world, int x, int y, int z) {
        return required(world, "world name") + "_" + x + "_" + y + "_" + z;
    }

    public boolean ready() {
        return remainingMillis == 0;
    }

    /** The same record with time credited, floored at zero. */
    public PlacedEggRecord withRemaining(long millis) {
        long bounded = Math.max(0, Math.min(totalMillis, millis));
        return new PlacedEggRecord(eggId, itemNonce, ownerId, world, blockX, blockY, blockZ,
                bounded, totalMillis, itemSnapshot, extensions);
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required");
        return value.trim();
    }
}
