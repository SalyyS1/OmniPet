package io.github.salyvn.omnipet.paper.incubation.action;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;

import io.github.salyvn.omnipet.core.incubation.EggItemIdentity;
import io.github.salyvn.omnipet.core.incubation.IncubationItemActionItemContract;
import io.github.salyvn.omnipet.core.incubation.IncubationItemActionType;

final class PaperIncubationItemActionSnapshot {
    static final int MAX_ITEM_BYTES = 8 * 1024;
    private static final int SCHEMA_VERSION = 1;
    private static final String SCHEMA_KEY = "paperActionItemSchema";
    private static final String ITEM_KEY = "paperActionItemBase64";

    private PaperIncubationItemActionSnapshot() {}

    static Map<String, Object> extensions(
            byte[] itemBytes,
            IncubationItemActionType type,
            long effectMillis) {
        byte[] checked = requirePayload(itemBytes);
        return IncubationItemActionItemContract.bind(Map.of(
                SCHEMA_KEY, SCHEMA_VERSION,
                ITEM_KEY, Base64.getEncoder().encodeToString(checked)), type, effectMillis);
    }

    static byte[] payload(EggItemIdentity identity) {
        if (identity == null) throw new IllegalArgumentException("incubation action item identity is required");
        Object schema = identity.extensions().get(SCHEMA_KEY);
        Object encoded = identity.extensions().get(ITEM_KEY);
        if (!(schema instanceof Number number) || number.intValue() != SCHEMA_VERSION) {
            throw new IllegalArgumentException("unsupported Paper incubation action snapshot schema");
        }
        if (!(encoded instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException("Paper incubation action snapshot is required");
        }
        try {
            return requirePayload(Base64.getDecoder().decode(text));
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("Paper incubation action snapshot is not valid Base64", invalid);
        }
    }

    static String fingerprint(byte[] itemBytes) {
        byte[] checked = requirePayload(itemBytes);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(checked));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static byte[] requirePayload(byte[] itemBytes) {
        if (itemBytes == null || itemBytes.length == 0) {
            throw new IllegalArgumentException("Paper incubation action snapshot cannot be empty");
        }
        if (itemBytes.length > MAX_ITEM_BYTES) {
            throw new IllegalArgumentException("Paper incubation action snapshot exceeds 8 KiB");
        }
        return itemBytes.clone();
    }
}
