package io.github.salyvn.omnipet.core.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class StableIdTest {
    @Test
    void acceptsAsciiSafeIdsAndFoldsCase() {
        assertEquals("my_pet-2", StableId.requireValid("my_pet-2"));
        assertEquals("mypet", StableId.folded("MyPet"));
    }

    @Test
    void rejectsTraversalReservedAndTrailingCharacters() {
        assertThrows(IllegalArgumentException.class, () -> StableId.requireValid("../pet"));
        assertThrows(IllegalArgumentException.class, () -> StableId.requireValid("CON"));
        assertThrows(IllegalArgumentException.class, () -> StableId.requireValid("pet."));
        assertThrows(IllegalArgumentException.class, () -> StableId.requireValid("pet "));
    }
}
