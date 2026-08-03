package io.github.salyvn.omnipet.paper.gui.player;

import java.util.Comparator;

import io.github.salyvn.omnipet.core.domain.PetInstance;

/**
 * How the vault orders pets. Cycles on a click, so the order is a display choice, not a setting.
 *
 * <p>Every comparator is <strong>total and stable</strong>: ties break on the pet's UUID. This is a
 * correctness requirement, not tidiness. An inconsistent comparator lets the same pet land on two
 * pages or on none, so a player would see a duplicate or lose track of a pet entirely.
 *
 * <p>Absent data sorts last rather than as zero. A legacy pet whose level cannot be read is unknown,
 * not level 1, and ranking it below every known pet is honest where ranking it as the weakest is not.
 */
public enum VaultSortOrder {
    /** Favorites first, then by level. A player who favorited a pet has said what they want on top. */
    FAVORITES_FIRST {
        @Override
        Comparator<PetInstance> comparator() {
            return Comparator
                    .comparing((PetInstance pet) -> !VaultPetSummary.of(pet).favorite())
                    .thenComparing(byLevelDescending());
        }
    },
    LEVEL_DESC {
        @Override
        Comparator<PetInstance> comparator() {
            return byLevelDescending();
        }
    },
    RARITY_DESC {
        @Override
        Comparator<PetInstance> comparator() {
            // Rarity IDs are free-form text with no shipped ordering, so reverse-alphabetical is the
            // honest choice: it is deterministic and does not invent a hierarchy the data lacks.
            return Comparator
                    .comparing((PetInstance pet) -> VaultPetSummary.of(pet).rarity().orElse(null),
                            Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(byId());
        }
    },
    NAME_ASC {
        @Override
        Comparator<PetInstance> comparator() {
            return Comparator
                    .comparing((PetInstance pet) -> pet.definitionId().toLowerCase(java.util.Locale.ROOT))
                    .thenComparing(byId());
        }
    },
    /** Storage order, kept as an explicit choice so a player can get back to the original listing. */
    RECENT {
        @Override
        Comparator<PetInstance> comparator() {
            return null;
        }
    };

    abstract Comparator<PetInstance> comparator();

    /** The next order in the cycle, wrapping at the end. */
    public VaultSortOrder next() {
        VaultSortOrder[] all = values();
        return all[(ordinal() + 1) % all.length];
    }

    /**
     * Applies this order. {@link #RECENT} returns the list unchanged rather than sorting by a
     * synthetic key, since storage order is what it means.
     */
    public java.util.List<PetInstance> sort(java.util.List<PetInstance> pets) {
        Comparator<PetInstance> comparator = comparator();
        if (comparator == null) return java.util.List.copyOf(pets);
        return pets.stream().sorted(comparator).toList();
    }

    private static Comparator<PetInstance> byLevelDescending() {
        return Comparator
                .comparing((PetInstance pet) -> VaultPetSummary.of(pet).level().orElse(null),
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(byId());
    }

    /** The final tiebreak that makes every comparator total, so no pet can drift between pages. */
    private static Comparator<PetInstance> byId() {
        return Comparator.comparing(pet -> pet.id().toString());
    }
}
