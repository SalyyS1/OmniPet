package io.github.salyvn.omnipet.paper.incubation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class PaperThreadGuardTest {
    @Test
    void rejectsInventoryAccessAwayFromThePrimaryThread() {
        assertThrows(IllegalStateException.class, () -> new PaperThreadGuard(() -> false).check());
        assertDoesNotThrow(() -> new PaperThreadGuard(() -> true).check());
    }
}
