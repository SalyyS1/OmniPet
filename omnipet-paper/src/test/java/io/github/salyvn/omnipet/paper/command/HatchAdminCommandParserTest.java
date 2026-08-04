package io.github.salyvn.omnipet.paper.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class HatchAdminCommandParserTest {
    private final UUID playerId = UUID.randomUUID();
    private final UUID incubationId = UUID.randomUUID();
    private final UUID actionId = UUID.randomUUID();

    @Test
    void parsesEveryOfflineHatchAdministrationForm() {
        assertEquals(
                new HatchAdminCommandParser.Inspect(playerId),
                parse("inspect", playerId));
        assertEquals(
                new HatchAdminCommandParser.Reduce(playerId, incubationId, 0, actionId),
                parse("reduce", playerId, incubationId, "0", actionId));
        assertEquals(
                new HatchAdminCommandParser.Reduce(playerId, incubationId, Long.MAX_VALUE, actionId),
                parse("reduce", playerId, incubationId, Long.toString(Long.MAX_VALUE), actionId));
        assertEquals(
                new HatchAdminCommandParser.SetRemaining(playerId, incubationId, 2500, actionId),
                parse("set", playerId, incubationId, "2500", actionId));
        assertEquals(
                new HatchAdminCommandParser.Complete(playerId, incubationId, actionId),
                parse("complete", playerId, incubationId, actionId));
        assertEquals(
                new HatchAdminCommandParser.Cancel(playerId, incubationId, actionId),
                parse("cancel", playerId, incubationId, actionId));
    }

    @Test
    void rejectsMalformedMissingExtraNegativeAndOverflowingArguments() {
        List<List<String>> invalid = List.of(
                List.of("admin", "hatch"),
                List.of("admin", "hatch", "inspect"),
                List.of("admin", "hatch", "inspect", "not-a-uuid"),
                List.of("admin", "hatch", "inspect", playerId.toString(), "extra"),
                List.of("admin", "hatch", "reduce", playerId.toString(), incubationId.toString(), "-1",
                        actionId.toString()),
                List.of("admin", "hatch", "reduce", playerId.toString(), incubationId.toString(),
                        "9223372036854775808", actionId.toString()),
                List.of("admin", "hatch", "set", playerId.toString(), "bad", "0", actionId.toString()),
                List.of("admin", "hatch", "complete", playerId.toString(), incubationId.toString(), "bad"),
                List.of("admin", "hatch", "cancel", playerId.toString(), incubationId.toString(),
                        actionId.toString(), "extra"));

        invalid.forEach(arguments -> assertInstanceOf(
                HatchAdminCommandParser.Invalid.class,
                HatchAdminCommandParser.parse(arguments)));
        assertInstanceOf(
                HatchAdminCommandParser.NotMatched.class,
                HatchAdminCommandParser.parse(List.of("admin", "browse")));
    }

    private static HatchAdminCommandParser.Result parse(String action, Object... values) {
        java.util.ArrayList<String> arguments = new java.util.ArrayList<>(List.of("admin", "hatch", action));
        for (Object value : values) arguments.add(value.toString());
        return HatchAdminCommandParser.parse(arguments);
    }
    @Test
    void anOnlineNameResolvesWhereAUuidWasPreviouslyRequired() {
        // An operator acting on someone standing in front of them had to open a YAML file and copy a
        // UUID out of it. A name resolves first; the UUID form is untouched.
        UUID steve = UUID.randomUUID();
        PlayerArgumentResolver players = new PlayerArgumentResolver(
                name -> name.equals("Steve") ? java.util.Optional.of(steve) : java.util.Optional.empty());

        assertEquals(
                new HatchAdminCommandParser.Inspect(steve),
                HatchAdminCommandParser.parse(List.of("admin", "hatch", "inspect", "Steve"), players));
        assertEquals(
                new HatchAdminCommandParser.Inspect(playerId),
                HatchAdminCommandParser.parse(
                        List.of("admin", "hatch", "inspect", playerId.toString()), players));
        assertInstanceOf(
                HatchAdminCommandParser.Invalid.class,
                HatchAdminCommandParser.parse(List.of("admin", "hatch", "inspect", "Offline"), players));
    }

    @Test
    void theActionIdIsOptionalAndGeneratedWhenOmitted() {
        // The action ID only makes a retry idempotent. Requiring the operator to invent one added a
        // chance to mistype it and nothing else.
        UUID generated = UUID.randomUUID();
        PlayerArgumentResolver players = new PlayerArgumentResolver(name -> java.util.Optional.empty());

        assertEquals(
                new HatchAdminCommandParser.Cancel(playerId, incubationId, generated),
                HatchAdminCommandParser.parse(
                        List.of("admin", "hatch", "cancel", playerId.toString(), incubationId.toString()),
                        players, () -> generated));
        assertEquals(
                new HatchAdminCommandParser.Reduce(playerId, incubationId, 5_000L, generated),
                HatchAdminCommandParser.parse(
                        List.of("admin", "hatch", "reduce", playerId.toString(), incubationId.toString(),
                                "5000"),
                        players, () -> generated));
        // An explicitly supplied action ID still wins, so a scripted retry stays idempotent.
        assertEquals(
                new HatchAdminCommandParser.Cancel(playerId, incubationId, actionId),
                HatchAdminCommandParser.parse(
                        List.of("admin", "hatch", "cancel", playerId.toString(), incubationId.toString(),
                                actionId.toString()),
                        players, () -> generated));
    }
}
