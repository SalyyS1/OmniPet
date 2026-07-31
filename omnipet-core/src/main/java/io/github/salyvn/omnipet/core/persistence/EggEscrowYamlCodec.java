package io.github.salyvn.omnipet.core.persistence;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.github.salyvn.omnipet.core.domain.RawNodeValues;
import io.github.salyvn.omnipet.core.incubation.EggEscrowStage;
import io.github.salyvn.omnipet.core.incubation.EggEscrowTransaction;
import io.github.salyvn.omnipet.core.incubation.EggInventoryHand;
import io.github.salyvn.omnipet.core.incubation.EggItemIdentity;

public final class EggEscrowYamlCodec {
    public static final int CURRENT_SCHEMA_VERSION = 1;
    private static final List<String> TRANSACTION_KEYS = List.of(
            "schemaVersion", "transactionId", "playerId", "incubationId", "eggId",
            "expectedPlayerRevision", "item", "stage");
    private static final List<String> ITEM_KEYS = List.of(
            "inventorySlot", "hand", "materialKey", "itemNonce", "fingerprint", "expectedStackAmount");

    public EggEscrowTransaction decode(String yaml) {
        Map<String, Object> raw = YamlDocuments.readMap(yaml);
        int schema = integer(raw.getOrDefault("schemaVersion", 1), "schemaVersion");
        if (schema != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported egg escrow schema version: " + schema);
        }
        Map<String, Object> item = map(raw.get("item"), "item");
        EggItemIdentity identity = new EggItemIdentity(
                integer(item.get("inventorySlot"), "item.inventorySlot"),
                enumValue(item.get("hand"), EggInventoryHand.class, "item.hand"),
                text(item.get("materialKey"), "item.materialKey"),
                uuid(item.get("itemNonce"), "item.itemNonce"),
                text(item.get("fingerprint"), "item.fingerprint"),
                integer(item.get("expectedStackAmount"), "item.expectedStackAmount"),
                without(item, ITEM_KEYS));
        return new EggEscrowTransaction(
                uuid(raw.get("transactionId"), "transactionId"),
                uuid(raw.get("playerId"), "playerId"),
                uuid(raw.get("incubationId"), "incubationId"),
                text(raw.get("eggId"), "eggId"),
                integerLong(raw.get("expectedPlayerRevision"), "expectedPlayerRevision"),
                identity,
                enumValue(raw.get("stage"), EggEscrowStage.class, "stage"),
                without(raw, TRANSACTION_KEYS));
    }

    public byte[] encode(EggEscrowTransaction transaction) {
        if (transaction == null) throw new IllegalArgumentException("egg escrow transaction is required");
        LinkedHashMap<String, Object> output = new LinkedHashMap<>(RawNodeValues.mutableMap(transaction.extensions()));
        output.put("schemaVersion", CURRENT_SCHEMA_VERSION);
        output.put("transactionId", transaction.transactionId().toString());
        output.put("playerId", transaction.playerId().toString());
        output.put("incubationId", transaction.incubationId().toString());
        output.put("eggId", transaction.eggId());
        output.put("expectedPlayerRevision", transaction.expectedPlayerRevision());
        output.put("item", encodeItem(transaction.item()));
        output.put("stage", transaction.stage().name());
        return YamlDocuments.writeMap(output).getBytes(StandardCharsets.UTF_8);
    }

    private static Map<String, Object> encodeItem(EggItemIdentity item) {
        LinkedHashMap<String, Object> output = new LinkedHashMap<>(RawNodeValues.mutableMap(item.extensions()));
        output.put("inventorySlot", item.inventorySlot());
        output.put("hand", item.hand().name());
        output.put("materialKey", item.materialKey());
        output.put("itemNonce", item.itemNonce().toString());
        output.put("fingerprint", item.fingerprint());
        output.put("expectedStackAmount", item.expectedStackAmount());
        return output;
    }

    private static Map<String, Object> map(Object value, String path) {
        if (!(value instanceof Map<?, ?> map)) throw new IllegalArgumentException(path + " must be a map");
        return PlayerStateYamlCodec.stringMap(map, path);
    }

    private static Map<String, Object> without(Map<String, Object> value, List<String> keys) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>(value);
        keys.forEach(result::remove);
        return RawNodeValues.immutableMap(result);
    }

    private static String text(Object value, String path) {
        if (!(value instanceof String text) || text.isBlank()) throw new IllegalArgumentException(path + " must be text");
        return text;
    }

    private static UUID uuid(Object value, String path) {
        try { return UUID.fromString(text(value, path)); }
        catch (RuntimeException error) { throw new IllegalArgumentException(path + " must be a UUID", error); }
    }

    private static int integer(Object value, String path) {
        long result = integerLong(value, path);
        if (result < Integer.MIN_VALUE || result > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(path + " is outside integer range");
        }
        return (int) result;
    }

    private static long integerLong(Object value, String path) {
        if (!(value instanceof Number number) || number.doubleValue() != number.longValue()) {
            throw new IllegalArgumentException(path + " must be an integer");
        }
        return number.longValue();
    }

    private static <T extends Enum<T>> T enumValue(Object value, Class<T> type, String path) {
        try { return Enum.valueOf(type, text(value, path).toUpperCase(java.util.Locale.ROOT)); }
        catch (RuntimeException error) { throw new IllegalArgumentException(path + " is invalid", error); }
    }
}
