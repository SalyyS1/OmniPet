package io.github.salyvn.omnipet.paper.release;

public record MailboxClaimResult(Status status, ReleaseMailboxEntry entry, String detail) {
    public MailboxClaimResult {
        if (status == null) throw new IllegalArgumentException("mailbox claim status is required");
        detail = detail == null ? "" : detail;
    }

    public enum Status {
        INVENTORY_DELIVERED,
        MAILBOX_PENDING,
        UNKNOWN_REQUIRES_RECONCILIATION,
        NOT_FOUND,
        IDENTITY_MISMATCH,
        FAILED
    }
}
