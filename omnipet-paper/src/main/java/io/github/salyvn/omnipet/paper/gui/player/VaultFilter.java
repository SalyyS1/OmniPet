package io.github.salyvn.omnipet.paper.gui.player;

import java.util.Set;
import java.util.UUID;

import io.github.salyvn.omnipet.core.domain.PetInstance;

/**
 * Which pets the vault shows. Cycles on a click, same idiom as {@link VaultSortOrder}.
 *
 * <p>Every mode is answerable from the loaded snapshot plus metadata already in hand, so filtering
 * costs no repository read.
 */
public enum VaultFilter {
    ALL {
        @Override
        boolean matches(PetInstance pet, Set<UUID> activePetIds) {
            return true;
        }
    },
    FAVORITES {
        @Override
        boolean matches(PetInstance pet, Set<UUID> activePetIds) {
            return VaultPetSummary.of(pet).favorite();
        }
    },
    ACTIVE {
        @Override
        boolean matches(PetInstance pet, Set<UUID> activePetIds) {
            return activePetIds.contains(pet.id());
        }
    },
    /** Owned but not currently out: the complement of {@link #ACTIVE}. */
    STORED {
        @Override
        boolean matches(PetInstance pet, Set<UUID> activePetIds) {
            return !activePetIds.contains(pet.id());
        }
    };

    abstract boolean matches(PetInstance pet, Set<UUID> activePetIds);

    /** The next filter in the cycle, wrapping at the end. */
    public VaultFilter next() {
        VaultFilter[] all = values();
        return all[(ordinal() + 1) % all.length];
    }

    public java.util.List<PetInstance> apply(java.util.List<PetInstance> pets, Set<UUID> activePetIds) {
        if (this == ALL) return java.util.List.copyOf(pets);
        Set<UUID> active = activePetIds == null ? Set.of() : activePetIds;
        return pets.stream().filter(pet -> matches(pet, active)).toList();
    }
}
