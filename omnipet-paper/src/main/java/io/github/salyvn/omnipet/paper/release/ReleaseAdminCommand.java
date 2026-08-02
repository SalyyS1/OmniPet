package io.github.salyvn.omnipet.paper.release;

import java.util.UUID;

public sealed interface ReleaseAdminCommand {
    record ListPending(int limit) implements ReleaseAdminCommand {}

    record Recover(UUID playerId, UUID transactionId, Channel channel) implements ReleaseAdminCommand {}

    record Reconcile(UUID playerId, UUID transactionId, Decision decision) implements ReleaseAdminCommand {}

    enum Channel { INTERNAL, EXTERNAL }

    enum Decision {
        EXTERNAL_DELIVERED,
        EXTERNAL_NOT_DELIVERED,
        MAILBOX_PENDING,
        INVENTORY_DELIVERED
    }
}
