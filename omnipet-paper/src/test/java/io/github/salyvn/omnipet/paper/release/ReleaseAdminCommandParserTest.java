package io.github.salyvn.omnipet.paper.release;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class ReleaseAdminCommandParserTest {
    private final ReleaseAdminCommandParser parser = new ReleaseAdminCommandParser();

    @Test
    void parsesBoundedListAndExactIdentityRecovery() {
        UUID playerId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();

        ReleaseAdminParseResult list = parser.parse(List.of("list", "50"));
        ReleaseAdminParseResult recover = parser.parse(List.of(
                "recover", playerId.toString(), transactionId.toString(), "external"));

        assertEquals(ReleaseAdminParseResult.Status.ACCEPTED, list.status());
        assertEquals(50, assertInstanceOf(ReleaseAdminCommand.ListPending.class, list.command()).limit());
        ReleaseAdminCommand.Recover target = assertInstanceOf(ReleaseAdminCommand.Recover.class, recover.command());
        assertEquals(playerId, target.playerId());
        assertEquals(transactionId, target.transactionId());
        assertEquals(ReleaseAdminCommand.Channel.EXTERNAL, target.channel());
    }

    @Test
    void rejectsUnsafeLimitsAndMalformedIdentities() {
        assertEquals(ReleaseAdminParseResult.Status.INVALID, parser.parse(List.of("list", "51")).status());
        assertEquals(ReleaseAdminParseResult.Status.INVALID,
                parser.parse(List.of("recover", "not-uuid", UUID.randomUUID().toString(), "internal")).status());
    }

    @Test
    void parsesExplicitReconciliationDecisions() {
        ReleaseAdminParseResult result = parser.parse(List.of(
                "reconcile", UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                "external-delivered"));

        ReleaseAdminCommand.Reconcile command = assertInstanceOf(
                ReleaseAdminCommand.Reconcile.class, result.command());
        assertEquals(ReleaseAdminCommand.Decision.EXTERNAL_DELIVERED, command.decision());
    }
}
