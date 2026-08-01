package io.github.salyvn.omnipet.paper.incubation;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Stable tokens make recovery retries idempotent after a restart. */
final class IncubationActionTokens {
    private IncubationActionTokens() {}

    static UUID cancel(UUID transactionId) {
        if (transactionId == null) throw new IllegalArgumentException("transaction id is required");
        return UUID.nameUUIDFromBytes(("omnipet:escrow-cancel:" + transactionId)
                .getBytes(StandardCharsets.UTF_8));
    }
}
