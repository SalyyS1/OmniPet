package io.github.salyvn.omnipet.paper.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;

class CommandHelpTest {
    private static final Predicate<String> PLAIN_PLAYER = Set.of("omnipet.general")::contains;
    private static final Predicate<String> EVERYTHING = permission -> true;

    @Test
    void aPlainPlayerSeesOnlyTheirOwnCommands() {
        List<String> usages = usages(PLAIN_PLAYER, true);

        assertEquals(
                List.of(
                        "/pet",
                        "/pet vault [page]",
                        "/pet hatch",
                        "/pet hatch main",
                        "/pet hatch off",
                        "/pet hatch claim",
                        "/pet hatch refresh",
                        "/pet hatch use-main",
                        "/pet hatch use-off",
                        "/pet slot",
                        "/pet skill <pet-uuid> <binding-id>",
                        "/pet help [page]"),
                usages);
    }

    @Test
    void noAdminUsageLeaksToAnUnprivilegedPlayer() {
        assertTrue(usages(PLAIN_PLAYER, true).stream().noneMatch(usage -> usage.contains("admin")));
    }

    @Test
    void everyLineCarriesANonBlankDescription() {
        for (CommandHelp.Line line : lines(EVERYTHING, true)) {
            assertFalse(line.description().isBlank(), () -> line.usage() + " has no description");
        }
    }

    @Test
    void aGroupingLiteralIsNotListedAsARunnableCommand() {
        List<String> usages = usages(EVERYTHING, true);

        assertFalse(usages.contains("/pet admin"), "admin only groups children");
        assertFalse(usages.contains("/pet admin item"), "item only groups children");
        assertTrue(usages.contains("/pet admin item candy <online-player> [amount]"));
    }

    @Test
    void consoleSeesAdminCommandsButNoPlayerOnlyBranch() {
        List<String> usages = usages(EVERYTHING, false);

        assertTrue(usages.contains("/pet admin reload"));
        assertFalse(usages.contains("/pet"));
        assertFalse(usages.contains("/pet vault [page]"));
        assertFalse(usages.contains("/pet admin browse"));
        assertTrue(usages.stream().noneMatch(usage -> usage.startsWith("/pet hatch ")
                && !usage.startsWith("/pet admin")));
    }

    @Test
    void pagingClampsBelowOneAndPastTheLastPage() {
        List<CommandHelp.Line> lines = lines(EVERYTHING, true);
        int expectedPages = (lines.size() + CommandHelp.LINES_PER_PAGE - 1) / CommandHelp.LINES_PER_PAGE;

        assertEquals(1, CommandHelp.page(lines, -5).page());
        assertEquals(1, CommandHelp.page(lines, 0).page());
        assertEquals(expectedPages, CommandHelp.page(lines, expectedPages + 99).page());
        assertEquals(expectedPages, CommandHelp.page(lines, 1).pages());
    }

    @Test
    void everyLineAppearsOnExactlyOnePage() {
        List<CommandHelp.Line> lines = lines(EVERYTHING, true);
        int pages = CommandHelp.page(lines, 1).pages();

        List<CommandHelp.Line> paged = new java.util.ArrayList<>();
        for (int page = 1; page <= pages; page++) paged.addAll(CommandHelp.page(lines, page).lines());

        assertEquals(lines, paged);
    }

    @Test
    void aPageHoldsAtMostTheConfiguredLineCount() {
        List<CommandHelp.Line> lines = lines(EVERYTHING, true);

        assertTrue(CommandHelp.page(lines, 1).lines().size() <= CommandHelp.LINES_PER_PAGE);
    }

    @Test
    void anEmptyTreeYieldsOnePageWithNoLines() {
        CommandHelp.Page page = CommandHelp.page(List.of(), 3);

        assertEquals(1, page.page());
        assertEquals(1, page.pages());
        assertTrue(page.lines().isEmpty());
    }

    @Test
    void aSenderWithoutTheGeneralPermissionGetsNoLines() {
        assertTrue(lines(permission -> false, true).isEmpty());
    }

    private static List<CommandHelp.Line> lines(Predicate<String> hasPermission, boolean isPlayer) {
        return CommandHelp.lines(OmniPetCommandTree.root(), hasPermission, isPlayer);
    }

    private static List<String> usages(Predicate<String> hasPermission, boolean isPlayer) {
        return lines(hasPermission, isPlayer).stream().map(CommandHelp.Line::usage).toList();
    }
}
