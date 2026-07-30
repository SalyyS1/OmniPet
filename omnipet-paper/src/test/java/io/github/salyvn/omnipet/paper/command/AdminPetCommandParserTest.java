package io.github.salyvn.omnipet.paper.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.github.salyvn.omnipet.paper.command.AdminPetCommandParser.OpenAdminBrowse;
import io.github.salyvn.omnipet.paper.command.AdminPetCommandParser.PlayerPage;
import io.github.salyvn.omnipet.paper.command.AdminPetCommandParser.RejectReason;
import io.github.salyvn.omnipet.paper.command.AdminPetCommandParser.Rejected;
import io.github.salyvn.omnipet.paper.command.AdminPetCommandParser.Request;

class AdminPetCommandParserTest {
    private final UUID playerId = UUID.randomUUID();

    @Test
    void acceptsAdminBrowseForBothStableAliases() {
        assertInstanceOf(OpenAdminBrowse.class, parse("pet", List.of("admin", "browse"), playerId, true, true));
        assertInstanceOf(OpenAdminBrowse.class, parse("pets", List.of("ADMIN", "BROWSE"), playerId, true, true));
    }

    @Test
    void adminLiteralTakesPrecedenceOverPageParsing() {
        assertRejected(RejectReason.INVALID_ARGUMENTS,
                parse("pet", List.of("admin", "browse", "1"), playerId, true, true));
        assertRejected(RejectReason.INVALID_ARGUMENTS,
                parse("pet", List.of("admin"), playerId, true, true));
    }

    @Test
    void rejectsMissingPermissionsAndConsoleExecution() {
        assertRejected(RejectReason.GENERAL_PERMISSION_REQUIRED,
                parse("pet", List.of("admin", "browse"), playerId, false, true));
        assertRejected(RejectReason.MANAGE_PET_PERMISSION_REQUIRED,
                parse("pet", List.of("admin", "browse"), playerId, true, false));
        assertRejected(RejectReason.PLAYER_REQUIRED,
                parse("pet", List.of("admin", "browse"), null, true, true));
    }

    @Test
    void preservesOneBasedPlayerPageArguments() {
        PlayerPage defaultPage = assertInstanceOf(PlayerPage.class,
                parse("pet", List.of(), playerId, true, false));
        PlayerPage explicitPage = assertInstanceOf(PlayerPage.class,
                parse("pets", List.of("3"), playerId, true, false));
        assertEquals(1, defaultPage.page());
        assertEquals(3, explicitPage.page());
        assertRejected(RejectReason.INVALID_PAGE,
                parse("pet", List.of("0"), playerId, true, false));
    }

    private static AdminPetCommandParser.Result parse(
            String alias, List<String> args, UUID player, boolean general, boolean manage) {
        return AdminPetCommandParser.parse(new Request(alias, args, player, general, manage));
    }

    private static void assertRejected(RejectReason expected, AdminPetCommandParser.Result result) {
        assertEquals(expected, assertInstanceOf(Rejected.class, result).reason());
    }
}
