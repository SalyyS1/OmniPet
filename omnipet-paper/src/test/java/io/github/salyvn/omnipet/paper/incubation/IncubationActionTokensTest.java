package io.github.salyvn.omnipet.paper.incubation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class IncubationActionTokensTest {
    private static final UUID TRANSACTION =
            UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    void cancellationTokenIsStableAndTransactionScoped() {
        assertEquals(IncubationActionTokens.cancel(TRANSACTION),
                IncubationActionTokens.cancel(TRANSACTION));
        assertNotEquals(
                IncubationActionTokens.cancel(TRANSACTION),
                IncubationActionTokens.cancel(UUID.fromString("22222222-2222-2222-2222-222222222222")));
    }
}
