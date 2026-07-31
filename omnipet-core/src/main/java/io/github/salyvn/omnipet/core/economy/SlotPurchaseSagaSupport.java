package io.github.salyvn.omnipet.core.economy;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import io.github.salyvn.omnipet.core.domain.PlayerState;
import io.github.salyvn.omnipet.core.domain.SlotEntitlement;

final class SlotPurchaseSagaSupport {
    private static final String ENTITLEMENT_SOURCE = "ECONOMY";

    private final PurchaseJournal journal;
    private final Map<EconomyProvider, EconomyPort> ports;

    SlotPurchaseSagaSupport(PurchaseJournal journal, Map<EconomyProvider, EconomyPort> ports) {
        this.journal = java.util.Objects.requireNonNull(journal, "purchase journal");
        EnumMap<EconomyProvider, EconomyPort> validated = new EnumMap<>(EconomyProvider.class);
        if (ports != null) {
            ports.forEach((provider, port) -> {
                if (provider == null || port == null || provider != port.provider()) {
                    throw new IllegalArgumentException("economy port key must match its provider");
                }
                validated.put(provider, port);
            });
        }
        this.ports = Map.copyOf(validated);
    }

    boolean hasProvider(EconomyProvider provider) {
        return ports.containsKey(provider);
    }

    Optional<SlotPurchaseTransaction> find(UUID transactionId) throws IOException {
        return journal.find(transactionId);
    }

    void save(SlotPurchaseTransaction transaction) throws IOException {
        journal.save(transaction);
    }

    SlotPurchaseTransaction createUnchecked(SlotPurchaseTransaction transaction) {
        try {
            return journal.create(transaction);
        } catch (IOException failure) {
            throw new JournalAccessException(failure);
        }
    }

    void saveUnchecked(SlotPurchaseTransaction transaction) {
        try {
            journal.save(transaction);
        } catch (IOException failure) {
            throw new JournalAccessException(failure);
        }
    }

    EconomyOperationResult withdraw(SlotPurchaseTransaction transaction) {
        EconomyPort port = ports.get(transaction.amount().provider());
        if (port == null) return EconomyOperationResult.unavailable("configured provider is not loaded");
        try {
            EconomyOperationResult result = port.withdraw(requestFrom(transaction));
            return result == null ? EconomyOperationResult.unknown("provider returned no withdrawal result") : result;
        } catch (RuntimeException failure) {
            return EconomyOperationResult.unknown(shortDetail(failure, "provider withdrawal threw"));
        }
    }

    EconomyOperationResult refund(SlotPurchaseTransaction transaction) {
        EconomyPort port = ports.get(transaction.amount().provider());
        if (port == null) return EconomyOperationResult.unavailable("configured provider is not loaded");
        try {
            EconomyOperationResult result = port.refund(requestFrom(transaction));
            return result == null ? EconomyOperationResult.unknown("provider returned no refund result") : result;
        } catch (RuntimeException failure) {
            return EconomyOperationResult.unknown(shortDetail(failure, "provider refund threw"));
        }
    }

    static PlayerState grant(PlayerState current, SlotPurchaseTransaction transaction) {
        ArrayList<SlotEntitlement> entitlements = new ArrayList<>(current.slotEntitlements());
        entitlements.add(new SlotEntitlement(
                transaction.slot(),
                ENTITLEMENT_SOURCE,
                transaction.transactionId().toString(),
                Map.of("provider", transaction.amount().provider().name(), "amount", transaction.amount().value().toPlainString())));
        return current.withStorage(
                current.pets(),
                current.vaultCapacity(),
                transaction.slot(),
                current.desiredActivePetIds(),
                entitlements);
    }

    static boolean hasSlotEntitlement(PlayerState state, int slot) {
        return state.slotEntitlements().stream().anyMatch(entitlement -> entitlement.slot() == slot);
    }

    static boolean hasTransactionEntitlement(PlayerState state, UUID transactionId) {
        String referenceId = transactionId.toString();
        return state.slotEntitlements().stream().anyMatch(entitlement -> referenceId.equals(entitlement.referenceId()));
    }

    static SlotPurchaseQuote quoteFrom(SlotPurchaseTransaction transaction) {
        return new SlotPurchaseQuote(
                transaction.playerId(),
                transaction.expectedRevision(),
                new SlotUnlockRule(transaction.slot(), transaction.amount()));
    }

    static SlotPurchaseResult result(
            SlotPurchaseResult.Status status,
            SlotPurchaseTransaction transaction,
            String detail) {
        return new SlotPurchaseResult(status, transaction, detail);
    }

    static PurchaseAbort abort(SlotPurchaseResult.Status status, SlotPurchaseTransaction transaction, String detail) {
        return new PurchaseAbort(result(status, transaction, detail));
    }

    static PurchaseAbort abort(SlotPurchaseResult result) {
        return new PurchaseAbort(result);
    }

    static String limitedDetail(String detail) {
        return detail.length() <= 512 ? detail : detail.substring(0, 512);
    }

    private static EconomyRequest requestFrom(SlotPurchaseTransaction transaction) {
        return new EconomyRequest(transaction.transactionId(), transaction.playerId(), transaction.slot(), transaction.amount());
    }

    private static String shortDetail(RuntimeException failure, String fallback) {
        String detail = failure.getMessage() == null ? fallback : fallback + ": " + failure.getMessage();
        return limitedDetail(detail);
    }

    static final class PurchaseAbort extends RuntimeException {
        final SlotPurchaseResult result;

        private PurchaseAbort(SlotPurchaseResult result) {
            super(null, null, false, false);
            this.result = result;
        }
    }

    static final class JournalAccessException extends RuntimeException {
        final IOException failure;

        JournalAccessException(IOException failure) {
            super(null, null, false, false);
            this.failure = failure;
        }
    }
}
