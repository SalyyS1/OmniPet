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
        // Accepted contract change: no arguments now opens the hub, not vault page 1.
        PlayerPage explicitPage = assertInstanceOf(PlayerPage.class,
                parse("pets", List.of("3"), playerId, true, false));
        assertEquals(3, explicitPage.page());
        assertRejected(RejectReason.INVALID_PAGE,
                parse("pet", List.of("0"), playerId, true, false));
    }

    @Test
    void noArgumentsOpensTheHub() {
        AdminPetCommandParser.OpenHub hub = assertInstanceOf(AdminPetCommandParser.OpenHub.class,
                parse("pet", List.of(), playerId, true, false));

        assertEquals(playerId, hub.viewerId());
        assertInstanceOf(AdminPetCommandParser.OpenHub.class,
                parse("pets", List.of(), playerId, true, false));
    }

    @Test
    void theVaultLiteralKeepsDirectPagedAccess() {
        assertEquals(1, assertInstanceOf(PlayerPage.class,
                parse("pet", List.of("vault"), playerId, true, false)).page());
        assertEquals(3, assertInstanceOf(PlayerPage.class,
                parse("pet", List.of("vault", "3"), playerId, true, false)).page());
        assertEquals(3, assertInstanceOf(PlayerPage.class,
                parse("pet", List.of("3"), playerId, true, false)).page());
    }

    @Test
    void theVaultLiteralIsCaseInsensitiveAndValidatesItsPage() {
        assertInstanceOf(PlayerPage.class, parse("pet", List.of("VAULT"), playerId, true, false));
        assertRejected(RejectReason.INVALID_PAGE,
                parse("pet", List.of("vault", "0"), playerId, true, false));
        assertRejected(RejectReason.INVALID_ARGUMENTS,
                parse("pet", List.of("vault", "abc"), playerId, true, false));
        assertRejected(RejectReason.INVALID_ARGUMENTS,
                parse("pet", List.of("vault", "1", "2"), playerId, true, false));
    }

    @Test
    void consoleStillCannotOpenAPlayerView() {
        assertRejected(RejectReason.PLAYER_REQUIRED, parse("pet", List.of(), null, true, false));
        assertRejected(RejectReason.PLAYER_REQUIRED, parse("pet", List.of("vault"), null, true, false));
    }

    private static AdminPetCommandParser.Result parse(
            String alias, List<String> args, UUID player, boolean general, boolean manage) {
        return AdminPetCommandParser.parse(new Request(alias, args, player, general, manage));
    }

    private static void assertRejected(RejectReason expected, AdminPetCommandParser.Result result) {
        assertEquals(expected, assertInstanceOf(Rejected.class, result).reason());
    }
}
