package io.github.salyvn.omnipet.core.economy;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import io.github.salyvn.omnipet.core.persistence.YamlDocuments;

final class PurchaseJournalYamlCodec {
    private static final int SCHEMA_VERSION = 2;

    byte[] encode(SlotPurchaseTransaction transaction) {
        LinkedHashMap<String, Object> root = new LinkedHashMap<>();
        root.put("schemaVersion", SCHEMA_VERSION);
        root.put("transactionId", transaction.transactionId().toString());
        root.put("playerId", transaction.playerId().toString());
        root.put("expectedRevision", transaction.expectedRevision());
        root.put("slot", transaction.slot());
        root.put("provider", transaction.amount().provider().name());
        root.put("amount", transaction.amount().value().toPlainString());
        root.put("externalEntitlementRequired", transaction.externalEntitlementRequired());
        root.put("state", transaction.state().name());
        if (transaction.withdrawal() != null) root.put("withdrawal", encodeOperation(transaction.withdrawal()));
        if (transaction.refund() != null) root.put("refund", encodeOperation(transaction.refund()));
        root.put("detail", transaction.detail());
        return YamlDocuments.writeMap(root).getBytes(StandardCharsets.UTF_8);
    }

    SlotPurchaseTransaction decode(String yaml) {
        Map<String, Object> root = YamlDocuments.readMap(yaml);
        int schemaVersion = integer(root.get("schemaVersion"), "schemaVersion");
        if (schemaVersion < 1 || schemaVersion > SCHEMA_VERSION) {
            throw new IllegalArgumentException("unsupported purchase journal schema version");
        }
        EconomyOperationResult withdrawal = decodeOperation(root.get("withdrawal"), "withdrawal");
        EconomyOperationResult refund = decodeOperation(root.get("refund"), "refund");
        SlotPurchaseSagaState state = SlotPurchaseSagaState.valueOf(text(root.get("state"), "state"));
        boolean externalEntitlementRequired = schemaVersion >= 2
                && bool(root.get("externalEntitlementRequired"), "externalEntitlementRequired");
        String detail = optionalText(root.get("detail"));
        if (schemaVersion == 1
                && (state == SlotPurchaseSagaState.ENTITLEMENT_PERSISTED
                        || state == SlotPurchaseSagaState.ENTITLEMENT_SYNC_PENDING
                        || state == SlotPurchaseSagaState.COMPLETED)) {
            externalEntitlementRequired = true;
            state = SlotPurchaseSagaState.ENTITLEMENT_SYNC_PENDING;
            detail = SlotPurchaseSagaSupport.limitedDetail(
                    "legacy schema v1 completion requires entitlement verification"
                            + (detail.isBlank() ? "" : ": " + detail));
        }
        if (state == SlotPurchaseSagaState.FAILED
                && withdrawal != null
                && withdrawal.provenSuccess()
                && refund != null
                && !refund.provenSuccess()
                && !refund.ambiguous()) {
            state = SlotPurchaseSagaState.REFUND_PENDING;
        }
        return new SlotPurchaseTransaction(
                UUID.fromString(text(root.get("transactionId"), "transactionId")),
                UUID.fromString(text(root.get("playerId"), "playerId")),
                longValue(root.get("expectedRevision"), "expectedRevision"),
                integer(root.get("slot"), "slot"),
                new EconomyAmount(
                        EconomyProvider.valueOf(text(root.get("provider"), "provider")),
                        new BigDecimal(text(root.get("amount"), "amount"))),
                externalEntitlementRequired,
                state,
                withdrawal,
                refund,
                detail);
    }

    private static Map<String, Object> encodeOperation(EconomyOperationResult operation) {
        return Map.of("status", operation.status().name(), "evidence", operation.evidence());
    }

    private static EconomyOperationResult decodeOperation(Object raw, String label) {
        if (raw == null) return null;
        if (!(raw instanceof Map<?, ?> map)) throw new IllegalArgumentException(label + " must be a map");
        return new EconomyOperationResult(
                EconomyOperationResult.Status.valueOf(text(map.get("status"), label + ".status")),
                optionalText(map.get("evidence")));
    }

    private static int integer(Object raw, String label) {
        long value = longValue(raw, label);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(label + " is outside the integer range");
        }
        return (int) value;
    }

    private static long longValue(Object raw, String label) {
        try {
            if (raw instanceof Number number) return new BigDecimal(number.toString()).longValueExact();
            return Long.parseLong(text(raw, label));
        } catch (ArithmeticException | NumberFormatException failure) {
            throw new IllegalArgumentException(label + " must be an integer", failure);
        }
    }

    private static String text(Object raw, String label) {
        if (!(raw instanceof String value) || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required");
        }
        return value;
    }

    private static boolean bool(Object raw, String label) {
        if (!(raw instanceof Boolean value)) throw new IllegalArgumentException(label + " must be true or false");
        return value;
    }

    private static String optionalText(Object raw) {
        return raw == null ? "" : String.valueOf(raw);
    }
}
