package io.github.salyvn.omnipet.paper.studio.bukkit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;

import org.junit.jupiter.api.Test;

class StudioErrorMessagesTest {
    @Test
    void hidesStorageFailuresAndAbsolutePaths() {
        String fallback = "storage transaction failed; check the server log";

        assertEquals(fallback, StudioErrorMessages.forAdmin(new IOException("failed at /srv/omnipet/players/user.yml")));
        assertEquals(fallback, StudioErrorMessages.forAdmin(new IllegalStateException("failed at C:\\servers\\omnipet\\pets\\fox.yml")));
        assertEquals(fallback, StudioErrorMessages.forAdmin(new IllegalStateException("failed at /srv/omnipet/pets/fox.yml")));
        assertEquals(fallback, StudioErrorMessages.forAdmin(new IllegalStateException("wrapped", new IOException("disk full"))));
    }

    @Test
    void keepsBoundedValidationMessages() {
        assertEquals("stats.health: min must not exceed max",
                StudioErrorMessages.forAdmin(new IllegalArgumentException("stats.health: min must not exceed max")));
        assertEquals(180, StudioErrorMessages.forAdmin(new IllegalArgumentException("x".repeat(200))).length());
    }
}
