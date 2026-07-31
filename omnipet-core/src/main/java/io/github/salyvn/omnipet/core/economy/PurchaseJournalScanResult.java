package io.github.salyvn.omnipet.core.economy;

import java.util.List;

public record PurchaseJournalScanResult(
        List<SlotPurchaseTransaction> transactions,
        List<PurchaseJournalIssue> issues,
        boolean truncated,
        int omittedIssueCount,
        boolean issuesTruncated,
        String nextCursor) {
    public PurchaseJournalScanResult {
        transactions = List.copyOf(transactions == null ? List.of() : transactions);
        issues = List.copyOf(issues == null ? List.of() : issues);
        if (omittedIssueCount < 0) throw new IllegalArgumentException("omitted issue count cannot be negative");
        if (nextCursor != null && (nextCursor.isBlank() || nextCursor.length() > 128)) {
            throw new IllegalArgumentException("journal scan cursor is invalid");
        }
    }

    public PurchaseJournalScanResult(
            List<SlotPurchaseTransaction> transactions,
            List<PurchaseJournalIssue> issues,
            boolean truncated) {
        this(transactions, issues, truncated, 0, false, null);
    }
}
