package io.github.salyvn.omnipet.paper.incubation;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;

import io.github.salyvn.omnipet.core.incubation.EggItemIdentity;

public final class PaperEggItemSnapshot {
    public static final int MAX_ITEM_BYTES = 8 * 1024;
    public static final String SCHEMA_KEY = "paperItemSchema";
    public static final String ITEM_KEY = "paperItemBase64";
    private static final int SCHEMA_VERSION = 1;

    private PaperEggItemSnapshot() {}

    public static Map<String, Object> extensions(byte[] itemBytes) {
        byte[] checked = requirePayload(itemBytes);
        return Map.of(
                SCHEMA_KEY, SCHEMA_VERSION,
                ITEM_KEY, Base64.getEncoder().encodeToString(checked));
    }

    public static byte[] payload(EggItemIdentity identity) {
        if (identity == null) throw new IllegalArgumentException("egg item identity is required");
        Object schema = identity.extensions().get(SCHEMA_KEY);
        Object encoded = identity.extensions().get(ITEM_KEY);
        if (!(schema instanceof Number number) || number.intValue() != SCHEMA_VERSION) {
            throw new IllegalArgumentException("unsupported Paper egg item snapshot schema");
        }
        if (!(encoded instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException("Paper egg item snapshot is required");
        }
        try {
            return requirePayload(Base64.getDecoder().decode(text));
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("Paper egg item snapshot is not valid Base64", invalid);
        }
    }

    public static String fingerprint(byte[] itemBytes) {
        byte[] checked = requirePayload(itemBytes);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(checked));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static byte[] requirePayload(byte[] itemBytes) {
        if (itemBytes == null || itemBytes.length == 0) {
            throw new IllegalArgumentException("Paper egg item snapshot cannot be empty");
        }
        if (itemBytes.length > MAX_ITEM_BYTES) {
            throw new IllegalArgumentException("Paper egg item snapshot exceeds 8 KiB");
        }
        return itemBytes.clone();
    }
}
