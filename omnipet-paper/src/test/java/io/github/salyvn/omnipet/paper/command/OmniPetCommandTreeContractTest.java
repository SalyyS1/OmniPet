package io.github.salyvn.omnipet.paper.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * Ties the declarative tree back to the code and descriptor it claims to describe.
 *
 * <p>Without these, a typo'd permission would hide a branch from every sender, and a literal that
 * the dispatcher does not accept would be advertised by both help and tab-complete.
 */
class OmniPetCommandTreeContractTest {
    private static final Path DESCRIPTOR = Path.of("src/main/resources/paper-plugin.yml");
    private static final Path DISPATCHER =
            Path.of("src/main/java/io/github/salyvn/omnipet/paper/command/OmniPetCommand.java");

    @Test
    void everyPermissionInTheTreeIsDeclaredInTheDescriptor() throws IOException {
        String descriptor = Files.readString(DESCRIPTOR);

        for (String permission : Set.copyOf(OmniPetCommandTree.root().allPermissions())) {
            assertTrue(descriptor.contains(permission + ":"),
                    () -> permission + " is used by the command tree but not declared in paper-plugin.yml");
        }
    }

    @Test
    void everyTopLevelLiteralIsMatchedByTheDispatcherOrItsParser() throws IOException {
        // vault is matched by AdminPetCommandParser rather than inline in the dispatcher.
        String routing = Files.readString(DISPATCHER) + Files.readString(sibling("AdminPetCommandParser.java"));

        for (CommandSpec child : OmniPetCommandTree.root().children()) {
            assertTrue(routing.contains("equalsIgnoreCase(\"" + child.literal() + "\")"),
                    () -> "the tree advertises /pet " + child.literal() + " but nothing matches it");
        }
    }

    @Test
    void everyAdminBranchLiteralIsMatchedByTheDispatcherOrOneOfItsParsers() throws IOException {
        // The dispatcher matches some admin literals inline and delegates the rest to a parser.
        // Either is fine; what must not happen is a literal nothing matches.
        String routing = Files.readString(DISPATCHER)
                + Files.readString(sibling("AdminPetCommandParser.java"))
                + Files.readString(sibling("HatchAdminCommandParser.java"))
                + Files.readString(sibling("SlotTransactionAdminCommandParser.java"));

        for (CommandSpec branch : OmniPetCommandTree.root().child("admin").children()) {
            assertTrue(routing.contains("equalsIgnoreCase(\"" + branch.literal() + "\")"),
                    () -> "the tree advertises /pet admin " + branch.literal()
                            + " but no dispatcher or parser matches it");
        }
    }

    @Test
    void everyAdminVerbIsMatchedByItsOwningParser() throws IOException {
        assertVerbs(sibling("HatchAdminCommandParser.java"), "hatch");
        assertVerbs(Path.of("src/main/java/io/github/salyvn/omnipet/paper/release/ReleaseAdminCommandParser.java"),
                "release");
        assertVerbs(Path.of("src/main/java/io/github/salyvn/omnipet/paper/skill/PaperActiveSkillController.java"),
                "skill");
        assertVerbs(Path.of("src/main/java/io/github/salyvn/omnipet/paper/management/"
                + "PaperCultivationAdminCommandTarget.java"), "cultivation");
        assertVerbs(Path.of("src/main/java/io/github/salyvn/omnipet/paper/management/"
                + "PetCultivationItemController.java"), "item", List.of("candy", "breakthrough"));
    }

    private static void assertVerbs(Path source, String branch) throws IOException {
        assertVerbs(source, branch, null);
    }

    /** Asserts each advertised verb literal appears in the source that parses it. */
    private static void assertVerbs(Path source, String branch, List<String> only) throws IOException {
        String parser = Files.readString(source);
        for (CommandSpec verb : OmniPetCommandTree.root().child("admin").child(branch).children()) {
            if (only != null && !only.contains(verb.literal())) continue;
            assertTrue(parser.contains("\"" + verb.literal() + "\""),
                    () -> "the tree advertises /pet admin " + branch + " " + verb.literal()
                            + " but " + source.getFileName() + " never matches it");
        }
    }

    private static Path sibling(String fileName) {
        return DISPATCHER.resolveSibling(fileName);
    }

    @Test
    void noLiteralIsDuplicatedAmongSiblings() {
        Deque<CommandSpec> pending = new ArrayDeque<>(List.of(OmniPetCommandTree.root()));
        while (!pending.isEmpty()) {
            CommandSpec node = pending.pop();
            Set<String> seen = new HashSet<>();
            for (CommandSpec child : node.children()) {
                assertTrue(seen.add(child.literal()),
                        () -> "duplicate sibling literal " + child.literal() + " under " + node.literal());
                pending.push(child);
            }
        }
    }

    @Test
    void aPlayerOnlyBranchNeverSitsUnderTheAdminGroupWhichConsoleMustReach() {
        CommandSpec admin = OmniPetCommandTree.root().child("admin");

        // browse is the one admin command the dispatcher requires a player for (OmniPetCommand:465).
        List<String> playerOnly = new ArrayList<>();
        for (CommandSpec branch : admin.children()) {
            if (branch.playerOnly()) playerOnly.add(branch.literal());
        }
        assertEquals(List.of("browse"), playerOnly);
    }

    @Test
    void groupingLiteralsCarryNoPermissionOfTheirOwn() {
        Deque<CommandSpec> pending = new ArrayDeque<>(List.of(OmniPetCommandTree.root()));
        while (!pending.isEmpty()) {
            CommandSpec node = pending.pop();
            if (!node.runnable()) {
                assertTrue(node.permissions().isEmpty(),
                        () -> node.literal() + " groups children, so its visibility must come from them");
            }
            node.children().forEach(pending::push);
        }
    }

    @Test
    void theVaultLiteralIsAdvertisedNowThatItsParserBranchExists() throws IOException {
        // The Phase 3 form of this test asserted the opposite: help must never advertise a command
        // the dispatcher cannot route, so the literal and its branch had to land together.
        assertTrue(Files.readString(sibling("AdminPetCommandParser.java"))
                .contains("equalsIgnoreCase(\"vault\")"));
        assertTrue(OmniPetCommandTree.root().children().stream()
                .anyMatch(child -> child.literal().equals("vault")));
    }

    @Test
    void theHubResultIsDispatchedRatherThanFallingThroughToTheVault() throws IOException {
        String dispatcher = Files.readString(DISPATCHER);

        assertTrue(dispatcher.contains("AdminPetCommandParser.OpenHub"));
        assertTrue(dispatcher.contains("target.open(player)"));
    }
}
