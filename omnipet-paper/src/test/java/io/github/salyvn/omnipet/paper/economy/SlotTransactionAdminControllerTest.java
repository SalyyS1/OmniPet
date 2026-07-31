package io.github.salyvn.omnipet.paper.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import io.github.salyvn.omnipet.core.economy.PurchaseJournalIssue;
import io.github.salyvn.omnipet.core.economy.PurchaseJournalScanResult;

class SlotTransactionAdminControllerTest {
    @Test
    void listMessagesRemainBoundedAndExposeCursorAndIssueOmissions() {
        List<PurchaseJournalIssue> issues = IntStream.range(0, 25)
                .mapToObj(index -> new PurchaseJournalIssue("bad-" + index + ".yml", "invalid"))
                .toList();
        PurchaseJournalScanResult scan = new PurchaseJournalScanResult(
                List.of(),
                issues,
                true,
                7,
                true,
                "v1.a.start");

        List<String> messages = SlotTransactionAdminController.formatListMessages(scan, 20);

        assertEquals(24, messages.size());
        assertEquals(20, messages.stream().filter(message -> message.startsWith("- unreadable bad-")).count());
        assertTrue(messages.contains("- 12 additional unreadable journal issue(s) omitted."));
        assertTrue(messages.stream().anyMatch(message -> message.contains("additional issues may exist")));
        assertTrue(messages.getLast().contains("/pet admin transactions 20 v1.a.start"));
    }
}
