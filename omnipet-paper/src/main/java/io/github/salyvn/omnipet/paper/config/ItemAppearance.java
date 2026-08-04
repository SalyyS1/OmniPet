package io.github.salyvn.omnipet.paper.config;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Operator-configurable appearance for one OmniPet item.
 *
 * <p>Every field is optional and falls back to what the plugin already produced, so an absent section
 * reproduces today's item exactly. This is presentation only: it never carries the persistent identity
 * keys, the amount-one invariant, or anything else escrow matches on.
 *
 * <p><strong>Read at mint time only.</strong> An egg's escrow fingerprint is a hash of the serialized
 * stack taken when the item is captured, so appearance chosen before minting is simply part of that
 * hash. Changing the config later affects newly minted items; it must never rewrite an item already
 * held in escrow, whose recorded fingerprint would no longer match.
 */
public record ItemAppearance(
        Optional<String> material,
        Optional<Integer> customModelData,
        boolean glint,
        List<String> itemFlags,
        List<String> extraLore) {
    /**
     * Bounds configurable lore.
     *
     * <p>The durable egg snapshot rejects a serialized stack over 8 KiB, and lore is the one part of an
     * item an operator can grow without limit. Capping the count and the line length keeps a verbose
     * config from producing an egg that mints fine and then fails to be captured.
     */
    public static final int MAX_EXTRA_LORE_LINES = 12;
    public static final int MAX_LORE_LINE_LENGTH = 256;

    public ItemAppearance {
        material = material == null ? Optional.empty() : material.filter(value -> !value.isBlank());
        customModelData = customModelData == null ? Optional.empty() : customModelData;
        itemFlags = itemFlags == null ? List.of() : List.copyOf(itemFlags);
        extraLore = extraLore == null ? List.of() : List.copyOf(extraLore);
        if (extraLore.size() > MAX_EXTRA_LORE_LINES) {
            throw new IllegalArgumentException("extra lore cannot exceed " + MAX_EXTRA_LORE_LINES + " lines");
        }
        if (extraLore.stream().anyMatch(line -> line.length() > MAX_LORE_LINE_LENGTH)) {
            throw new IllegalArgumentException("a lore line cannot exceed " + MAX_LORE_LINE_LENGTH + " characters");
        }
    }

    /** The appearance of an item nobody has configured: exactly what the plugin built before. */
    public static ItemAppearance defaults() {
        return new ItemAppearance(Optional.empty(), Optional.empty(), false, List.of(), List.of());
    }

    /** The configured material, or {@code fallback} when the operator did not choose one. */
    public String materialOr(String fallback) {
        return material.orElse(Objects.requireNonNull(fallback, "fallback material"));
    }

    public boolean isDefault() {
        return material.isEmpty() && customModelData.isEmpty() && !glint
                && itemFlags.isEmpty() && extraLore.isEmpty();
    }
}
