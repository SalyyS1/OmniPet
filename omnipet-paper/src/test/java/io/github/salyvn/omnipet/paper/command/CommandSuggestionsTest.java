package io.github.salyvn.omnipet.paper.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;

class CommandSuggestionsTest {
    private static final Predicate<String> PLAIN_PLAYER = Set.of("omnipet.general")::contains;
    private static final Predicate<String> EVERYTHING = permission -> true;

    @Test
    void aPlainPlayerSeesTheirOwnBranchesAndNoAdminNode() {
        List<String> suggestions = suggest(PLAIN_PLAYER, true, "");

        assertEquals(List.of("vault", "hatch", "slot", "skill", "help"), suggestions);
        assertFalse(suggestions.contains("admin"));
    }

    @Test
    void anAdminSeesTheAdminGroupingLiteral() {
        assertTrue(suggest(EVERYTHING, true, "").contains("admin"));
    }

    @Test
    void aSingleAdminPermissionExposesThatBranchAndNothingElse() {
        Predicate<String> releaseOnly = Set.of("omnipet.general", "omnipet.admin.release")::contains;

        assertEquals(List.of("vault", "hatch", "slot", "skill", "help", "admin"), suggest(releaseOnly, true, ""));
        assertEquals(List.of("release"), suggest(releaseOnly, true, "admin", ""));
        assertEquals(List.of("list", "recover", "reconcile"), suggest(releaseOnly, true, "admin", "release", ""));
    }

    @Test
    void aPartialTokenFiltersByCaseInsensitivePrefix() {
        assertEquals(List.of("admin"), suggest(EVERYTHING, true, "ad"));
        assertEquals(List.of("admin"), suggest(EVERYTHING, true, "AD"));
        assertEquals(List.of("hatch", "help"), suggest(EVERYTHING, true, "h"));
    }

    @Test
    void adminHatchOffersEveryVerbAndInspectIsGatedSeparately() {
        assertEquals(
                List.of("inspect", "reduce", "set", "complete", "cancel"),
                suggest(EVERYTHING, true, "admin", "hatch", ""));

        Predicate<String> inspectOnly = Set.of("omnipet.general", "omnipet.admin.inspect")::contains;
        assertEquals(List.of("inspect"), suggest(inspectOnly, true, "admin", "hatch", ""));

        Predicate<String> manageOnly = Set.of("omnipet.general", "omnipet.admin.manageegg")::contains;
        assertEquals(
                List.of("reduce", "set", "complete", "cancel"),
                suggest(manageOnly, true, "admin", "hatch", ""));
    }

    @Test
    void cultivationItemTypesRequireBothItemAndCultivationPermissions() {
        Predicate<String> itemOnly = Set.of("omnipet.general", "omnipet.admin.item")::contains;

        assertEquals(List.of("reducer", "instant"), suggest(itemOnly, true, "admin", "item", ""));
        assertEquals(
                List.of("reducer", "instant", "candy", "breakthrough"),
                suggest(EVERYTHING, true, "admin", "item", ""));
    }

    @Test
    void consoleSeesOnlyAdminBranchesBecauseTheDispatcherRequiresAPlayerElsewhere() {
        List<String> suggestions = suggest(EVERYTHING, false, "");

        assertEquals(List.of("help", "admin"), suggestions);
        assertFalse(suggestions.contains("hatch"));
        assertFalse(suggestions.contains("slot"));
        assertFalse(suggestions.contains("skill"));
    }

    @Test
    void anIdArgumentSuggestsNothingRatherThanGuessing() {
        assertEquals(List.of(), suggest(EVERYTHING, true, "skill", ""));
        // Slot reconciliation takes a transaction UUID first, so it has nothing safe to enumerate.
        assertEquals(List.of(), suggest(EVERYTHING, true, "admin", "reconcile", ""));
    }

    @Test
    void aRealSubcommandIsStillSuggestedWhereOneExists() {
        // `transactions` takes [limit], which cannot be guessed, but it does own a `menu` subcommand -
        // and a literal that exists must be offered rather than suppressed along with the arguments.
        assertEquals(List.of("menu"), suggest(EVERYTHING, true, "admin", "transactions", ""));
    }

    @Test
    void anUnmatchedCompletedTokenStopsTheWalkInsteadOfRestartingAtTheRoot() {
        assertEquals(List.of(), suggest(EVERYTHING, true, "not-a-branch", ""));
        assertEquals(List.of(), suggest(EVERYTHING, true, "2", ""));
    }

    @Test
    void aSenderWithoutTheGeneralPermissionGetsNothing() {
        assertEquals(List.of(), suggest(permission -> false, true, ""));
    }

    @Test
    void vaultIsSuggestedNowThatItsParserBranchExists() {
        assertTrue(suggest(EVERYTHING, true, "").contains("vault"));
        assertEquals(List.of("vault"), suggest(EVERYTHING, true, "va"));
    }

    private static List<String> suggest(Predicate<String> hasPermission, boolean isPlayer, String... args) {
        return CommandSuggestions.suggest(OmniPetCommandTree.root(), hasPermission, isPlayer, args);
    }
    @Test
    void requiredAndOptionalPlayerArgumentsSuggestOnlineNames() {
        // Reading the online roster is an in-memory lookup, unlike resolving an offline profile or UUID.
        java.util.function.Supplier<List<String>> roster = () -> List.of("Steve", "Alex", "Stephanie");

        assertEquals(List.of("Steve", "Alex", "Stephanie"),
                suggestWithRoster(roster, "admin", "hatch", "inspect", ""));
        assertEquals(List.of("Steve", "Stephanie"),
                suggestWithRoster(roster, "admin", "egg", "give", "Ste"));
        assertEquals(List.of("Steve", "Alex", "Stephanie"),
                suggestWithRoster(roster, "admin", "stats", ""));
        assertEquals(List.of("Steve", "Alex", "Stephanie"),
                suggestWithRoster(roster, "admin", "release", "reconcile", ""));
    }

    @Test
    void aLaterIdArgumentSuggestsNothingRatherThanGuessing() {
        java.util.function.Supplier<List<String>> roster = () -> List.of("Steve");

        // Position 2 of `hatch reduce` is an incubation UUID: there is nothing safe to enumerate.
        assertEquals(List.of(), suggestWithRoster(roster, "admin", "hatch", "reduce", "Steve", ""));
        // A node whose first argument is not a player gets no value suggestions either.
        assertEquals(List.of(), suggestWithRoster(roster, "admin", "reconcile", ""));
    }

    private static List<String> suggestWithRoster(
            java.util.function.Supplier<List<String>> roster, String... args) {
        return CommandSuggestions.suggest(OmniPetCommandTree.root(), EVERYTHING, true, args, roster);
    }
}
