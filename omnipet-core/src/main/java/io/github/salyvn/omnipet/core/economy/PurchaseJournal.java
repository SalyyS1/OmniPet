package io.github.salyvn.omnipet.core.economy;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Durable, atomically replacing journal required by the purchase saga. */
public interface PurchaseJournal {
    Optional<SlotPurchaseTransaction> find(UUID transactionId) throws IOException;

    /** Atomically inserts and returns the existing record when the ID is already present. */
    SlotPurchaseTransaction create(SlotPurchaseTransaction transaction) throws IOException;

    void save(SlotPurchaseTransaction transaction) throws IOException;

    PurchaseJournalScanResult scan(Set<SlotPurchaseSagaState> states, int limit) throws IOException;

    default PurchaseJournalScanResult scan(
            Set<SlotPurchaseSagaState> states,
            int limit,
            String cursor) throws IOException {
        if (cursor != null && !cursor.isBlank()) {
            throw new IllegalArgumentException("purchase journal does not support scan cursors");
        }
        return scan(states, limit);
    }
}
