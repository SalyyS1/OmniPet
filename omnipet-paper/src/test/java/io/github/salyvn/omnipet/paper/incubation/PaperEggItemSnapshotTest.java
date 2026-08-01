package io.github.salyvn.omnipet.paper.incubation;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.github.salyvn.omnipet.core.incubation.EggInventoryHand;
import io.github.salyvn.omnipet.core.incubation.EggItemIdentity;

class PaperEggItemSnapshotTest {
    @Test
    void fingerprintsAndRoundTripsTheDurableAmountOnePayload() {
        byte[] payload = "amount-one-paper-item".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String fingerprint = PaperEggItemSnapshot.fingerprint(payload);
        EggItemIdentity identity = identity(fingerprint, PaperEggItemSnapshot.extensions(payload));

        assertEquals(64, fingerprint.length());
        assertEquals(fingerprint, PaperEggItemSnapshot.fingerprint(payload));
        assertArrayEquals(payload, PaperEggItemSnapshot.payload(identity));
    }

    @Test
    void rejectsMissingInvalidAndOversizedPayloads() {
        String fingerprint = PaperEggItemSnapshot.fingerprint(new byte[] {1});

        assertThrows(IllegalArgumentException.class,
                () -> PaperEggItemSnapshot.payload(identity(fingerprint, Map.of())));
        assertThrows(IllegalArgumentException.class,
                () -> PaperEggItemSnapshot.payload(identity(fingerprint, Map.of(
                        PaperEggItemSnapshot.SCHEMA_KEY, 1,
                        PaperEggItemSnapshot.ITEM_KEY, "not-base64"))));
        assertThrows(IllegalArgumentException.class,
                () -> PaperEggItemSnapshot.extensions(new byte[PaperEggItemSnapshot.MAX_ITEM_BYTES + 1]));
    }

    private static EggItemIdentity identity(String fingerprint, Map<String, Object> extensions) {
        return new EggItemIdentity(
                4,
                EggInventoryHand.MAIN_HAND,
                "minecraft:player_head",
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                fingerprint,
                3,
                extensions);
    }
}
