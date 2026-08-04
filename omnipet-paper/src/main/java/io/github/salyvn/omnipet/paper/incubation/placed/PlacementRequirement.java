package io.github.salyvn.omnipet.paper.incubation.placed;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * What an egg needs around it before a placed egg will incubate.
 *
 * <p>Read from the egg definition's {@code extensions} under {@code placement}, so a fire-affinity egg —
 * a dragon, a phoenix — can demand lava while an ordinary egg is happy beside a campfire. An egg with no
 * {@code placement} node uses {@link #defaults()}, which accepts any of the ordinary heat sources.
 *
 * <p>Deliberately dependency-free: it names required blocks as strings and counts them, so the rule is
 * unit-testable without a server and the Paper layer only has to supply what it sees around the block.
 */
public record PlacementRequirement(Set<String> acceptedBlocks, int minimumCount, String describeKey) {
    /** Heat sources an ordinary egg accepts. Any one adjacent block is enough. */
    public static final Set<String> DEFAULT_HEAT = Set.of(
            "torch", "wall_torch", "soul_torch", "soul_wall_torch",
            "campfire", "soul_campfire", "lantern", "soul_lantern",
            "fire", "soul_fire", "lava", "magma_block", "furnace", "blast_furnace", "smoker");

    /**
     * How many lava blocks a fire-affinity egg wants around it.
     *
     * <p>Eight is the ring of blocks touching a single block on its own level, so "surrounded by lava"
     * is a full ring rather than one lucky neighbour.
     */
    public static final int SURROUNDED_COUNT = 8;

    public PlacementRequirement {
        acceptedBlocks = acceptedBlocks == null || acceptedBlocks.isEmpty()
                ? DEFAULT_HEAT
                : Set.copyOf(acceptedBlocks.stream().map(PlacementRequirement::normalize).toList());
        if (minimumCount < 1) throw new IllegalArgumentException("minimum count must be positive");
        describeKey = describeKey == null || describeKey.isBlank() ? "heat" : describeKey.trim();
    }

    public static PlacementRequirement defaults() {
        return new PlacementRequirement(DEFAULT_HEAT, 1, "heat");
    }

    /** Lava on every side, for a fire-affinity egg. */
    public static PlacementRequirement surroundedByLava() {
        return new PlacementRequirement(Set.of("lava"), SURROUNDED_COUNT, "lava");
    }

    /**
     * Reads the requirement an egg definition declares.
     *
     * <p>Lenient: an absent, malformed, or empty {@code placement} node yields the defaults, because an
     * egg that cannot be incubated at all is a worse outcome than an egg that incubates too easily.
     */
    public static PlacementRequirement from(Map<String, Object> eggExtensions) {
        if (eggExtensions == null) return defaults();
        if (!(eggExtensions.get("placement") instanceof Map<?, ?> node)) return defaults();
        Object preset = node.get("requires");
        if (preset instanceof String name && name.trim().equalsIgnoreCase("lava")) {
            return surroundedByLava();
        }
        Set<String> blocks = blocks(node.get("blocks"));
        int count = node.get("count") instanceof Number number && number.intValue() >= 1
                ? number.intValue()
                : 1;
        // An egg that declared nothing usable is the default egg, describe line included, so a broken
        // node and an absent node cannot produce two subtly different requirements.
        if (blocks.equals(DEFAULT_HEAT) && count == 1 && !(node.get("describe") instanceof String)) {
            return defaults();
        }
        String describe = node.get("describe") instanceof String text && !text.isBlank()
                ? text
                : String.join(" or ", blocks);
        return new PlacementRequirement(blocks, count, describe);
    }

    /**
     * Whether the blocks around a placed egg satisfy this requirement.
     *
     * @param surroundingBlocks the block names adjacent to the egg, as the caller observed them
     */
    public boolean satisfiedBy(java.util.Collection<String> surroundingBlocks) {
        if (surroundingBlocks == null) return false;
        long matching = surroundingBlocks.stream()
                .filter(Objects::nonNull)
                .map(PlacementRequirement::normalize)
                .filter(acceptedBlocks::contains)
                .count();
        return matching >= minimumCount;
    }

    /** What to tell a player whose placement was refused. */
    public String describe() {
        return minimumCount > 1 ? "surrounded by " + describeKey : "next to " + describeKey;
    }

    private static Set<String> blocks(Object value) {
        if (!(value instanceof java.util.List<?> list)) return DEFAULT_HEAT;
        Set<String> names = new java.util.LinkedHashSet<>();
        for (Object entry : list) {
            if (entry instanceof String text && !text.isBlank()) names.add(normalize(text));
        }
        return names.isEmpty() ? DEFAULT_HEAT : Set.copyOf(names);
    }

    /** Strips a namespace and lowercases, so {@code minecraft:LAVA} and {@code lava} are one thing. */
    private static String normalize(String raw) {
        String value = raw.trim().toLowerCase(Locale.ROOT);
        int colon = value.indexOf(':');
        return colon < 0 ? value : value.substring(colon + 1);
    }

    /** The requirement for an egg, or empty when the egg forbids placement entirely. */
    public static Optional<PlacementRequirement> forEgg(Map<String, Object> eggExtensions) {
        if (eggExtensions != null
                && eggExtensions.get("placement") instanceof Map<?, ?> node
                && Boolean.FALSE.equals(node.get("allowed"))) {
            return Optional.empty();
        }
        return Optional.of(from(eggExtensions));
    }
}
