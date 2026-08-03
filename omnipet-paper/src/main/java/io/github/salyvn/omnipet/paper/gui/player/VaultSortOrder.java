package io.github.salyvn.omnipet.paper.gui.player;

import java.util.Comparator;
import java.util.List;

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
 *
 * <p>{@link #sort} derives each pet's {@link VaultPetSummary} <strong>once</strong> and sorts the
 * derived values. Reading it inside the comparator instead would re-parse the pet's component and
 * extension maps on every one of the {@code n log n} comparisons: measured at the 100,000-pet vault
 * cap that is a 1.4-second main-thread stall — roughly 28 ticks — on a single click. Derive-once
 * turns it into {@code n} derivations plus cheap comparisons.
 */
public enum VaultSortOrder {
    /** Favorites first, then by level. A player who favorited a pet has said what they want on top. */
    FAVORITES_FIRST {
        @Override
        Comparator<Ranked> comparator() {
            return Comparator.comparing((Ranked ranked) -> !ranked.summary().favorite())
                    .thenComparing(byLevelDescending());
        }
    },
    LEVEL_DESC {
        @Override
        Comparator<Ranked> comparator() {
            return byLevelDescending();
        }
    },
    RARITY_DESC {
        @Override
        Comparator<Ranked> comparator() {
            // Rarity IDs are free-form text with no shipped ordering, so reverse-alphabetical is the
            // honest choice: it is deterministic and does not invent a hierarchy the data lacks.
            return Comparator
                    .comparing((Ranked ranked) -> ranked.summary().rarity().orElse(null),
                            Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(byId());
        }
    },
    NAME_ASC {
        @Override
        Comparator<Ranked> comparator() {
            return Comparator.comparing(Ranked::name).thenComparing(byId());
        }
    },
    /** Storage order, kept as an explicit choice so a player can get back to the original listing. */
    RECENT {
        @Override
        Comparator<Ranked> comparator() {
            return null;
        }
    };

    abstract Comparator<Ranked> comparator();

    /** The next order in the cycle, wrapping at the end. */
    public VaultSortOrder next() {
        VaultSortOrder[] all = values();
        return all[(ordinal() + 1) % all.length];
    }

    /**
     * Applies this order. {@link #RECENT} returns the list unchanged rather than sorting by a
     * synthetic key, since storage order is what it means.
     */
    public List<PetInstance> sort(List<PetInstance> pets) {
        Comparator<Ranked> comparator = comparator();
        if (comparator == null) return List.copyOf(pets);
        return pets.stream().map(Ranked::of).sorted(comparator).map(Ranked::pet).toList();
    }

    private static Comparator<Ranked> byLevelDescending() {
        return Comparator
                .comparing((Ranked ranked) -> ranked.summary().level().orElse(null),
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(byId());
    }

    /** The final tiebreak that makes every comparator total, so no pet can drift between pages. */
    private static Comparator<Ranked> byId() {
        return Comparator.comparing(Ranked::id);
    }

    /** A pet with its sort keys derived once, so comparisons never re-parse its maps. */
    record Ranked(PetInstance pet, VaultPetSummary summary, String name, String id) {
        static Ranked of(PetInstance pet) {
            return new Ranked(
                    pet,
                    VaultPetSummary.of(pet),
                    pet.definitionId().toLowerCase(java.util.Locale.ROOT),
                    pet.id().toString());
        }
    }
}
