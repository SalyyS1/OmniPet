package io.github.salyvn.omnipet.core.economy;

public record PurchaseJournalIssue(String entryName, String detail) {
    public PurchaseJournalIssue {
        if (entryName == null || entryName.isBlank() || entryName.length() > 128) {
            throw new IllegalArgumentException("journal issue entry name is invalid");
        }
        detail = detail == null ? "" : detail;
        if (detail.length() > 512) throw new IllegalArgumentException("journal issue detail is too long");
    }
}
