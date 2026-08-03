package io.github.salyvn.omnipet.paper.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * Guards the Phase 2 boundary: player-facing chat resolves through the catalog, operator and Studio
 * output does not, and no migrated controller keeps a hardcoded chat literal.
 */
class MigratedControllerTextContractTest {
    private static final Path MAIN = Path.of("src/main/java/io/github/salyvn/omnipet/paper");

    private static final List<String> MIGRATED = List.of(
            "player/PlayerPetController.java",
            "player/PlayerHatchController.java",
            "player/PlayerSlotPurchaseController.java",
            "command/FoundationCommand.java");

    private static final List<String> OPERATOR_OWNED = List.of(
            "release/ReleaseAdminController.java",
            "economy/SlotTransactionAdminController.java",
            "incubation/HatchAdminController.java",
            "management/PaperCultivationAdminCommandTarget.java",
            "management/PetCultivationItemController.java");

    @Test
    void migratedControllersSendNoHardcodedPlayerText() throws IOException {
        Pattern literal = Pattern.compile("sendMessage\\(\\s*(?:Component\\.text\\()?\"");

        for (String relative : MIGRATED) {
            Matcher matcher = literal.matcher(read(relative));
            assertFalse(matcher.find(), () -> relative + " still sends a hardcoded string at index "
                    + matcher.start());
        }
    }

    @Test
    void migratedControllersRouteThroughTheCatalog() throws IOException {
        for (String relative : MIGRATED) {
            assertTrue(read(relative).contains("Messages.line("),
                    () -> relative + " does not resolve any text through the catalog");
        }
    }

    @Test
    void operatorOutputStaysInJavaSoAnEditedFileCannotDistortAnAuditLine() throws IOException {
        for (String relative : OPERATOR_OWNED) {
            String source = read(relative);
            assertFalse(source.contains("io.github.salyvn.omnipet.paper.text.Messages"),
                    () -> relative + " is operator output and must not read the message catalog");
        }
    }

    @Test
    void theSkillControllerMigratesPlayerFeedbackButKeepsItsAdminAuditRows() throws IOException {
        String source = read("skill/PaperActiveSkillController.java");

        assertTrue(source.contains("Messages.line(MessageKey.SKILL_SUCCEEDED)"));
        // The CommandSender overload is the /pet admin skill audit path and stays hardcoded.
        assertTrue(source.contains("private static void message(CommandSender sender"));
        assertFalse(source.contains("private static void message(Player player"));
    }

    @Test
    void theIncubationItemControllerMigratesRedemptionButKeepsDeliveryReceipts() throws IOException {
        String source = read("incubation/action/IncubationActionItemController.java");

        assertTrue(source.contains("Messages.line(MessageKey.HATCH_ITEM_NO_INCUBATION)"));
        assertTrue(source.contains("sender.sendMessage(\"OmniPet: delivered \""));
    }

    @Test
    void studioTextIsNotYetMigratedAndIsNotClaimedByAnyKey() throws IOException {
        assertFalse(read("studio/bukkit/PetStudioController.java").contains(
                        "io.github.salyvn.omnipet.paper.text.Messages"),
                "Studio text migrates in Phase 4, together with the prompt rewrite");
        for (MessageKey key : MessageKey.values()) {
            assertFalse(key.path().startsWith("studio."),
                    () -> key.path() + " exists before its Phase 4 owner does");
        }
    }

    private static String read(String relative) throws IOException {
        return Files.readString(MAIN.resolve(relative));
    }
}
