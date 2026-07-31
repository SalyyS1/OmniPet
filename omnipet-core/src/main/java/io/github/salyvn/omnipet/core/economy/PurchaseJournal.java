package io.github.salyvn.omnipet.core.economy;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

/** Durable, atomically replacing journal required by the purchase saga. */
public interface PurchaseJournal {
    Optional<SlotPurchaseTransaction> find(UUID transactionId) throws IOException;

    /** Atomically inserts and returns the existing record when the ID is already present. */
    SlotPurchaseTransaction create(SlotPurchaseTransaction transaction) throws IOException;

    void save(SlotPurchaseTransaction transaction) throws IOException;
}
