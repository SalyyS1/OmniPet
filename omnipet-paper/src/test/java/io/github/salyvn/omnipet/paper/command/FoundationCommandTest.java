package io.github.salyvn.omnipet.paper.command;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class FoundationCommandTest {
    @Test
    void exposesStableAliasesAndPermission() {
        assertEquals("pet", FoundationCommandContract.NAME);
        assertEquals(java.util.List.of("pets"), FoundationCommandContract.ALIASES);
        assertEquals("omnipet.general", FoundationCommandContract.PERMISSION);
    }
}
