package io.github.salyvn.omnipet.paper.management;

import java.util.Base64;
import java.util.Map;

import org.bukkit.inventory.ItemStack;

import io.github.salyvn.omnipet.core.incubation.EggItemIdentity;

final class PaperCultivationItemSnapshot {
    private static final int MAX_BYTES = 8 * 1024;
    private static final String SCHEMA_KEY = "paperCultivationItemSchema";
    private static final String ITEM_KEY = "paperCultivationItemBase64";

    private PaperCultivationItemSnapshot() {}

    static Map<String, Object> extensions(byte[] payload) {
        byte[] checked = require(payload);
        return Map.of(
                SCHEMA_KEY, 1,
                ITEM_KEY, Base64.getEncoder().encodeToString(checked));
    }

    static ItemStack restore(EggItemIdentity identity) {
        Object schema = identity.extensions().get(SCHEMA_KEY);
        Object encoded = identity.extensions().get(ITEM_KEY);
        if (!(schema instanceof Number number) || number.intValue() != 1
                || !(encoded instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException("cultivation item snapshot is invalid");
        }
        ItemStack item = ItemStack.deserializeBytes(require(Base64.getDecoder().decode(text)));
        item.setAmount(1);
        return item;
    }

    private static byte[] require(byte[] payload) {
        if (payload == null || payload.length == 0 || payload.length > MAX_BYTES) {
            throw new IllegalArgumentException("cultivation item snapshot must be 1..8192 bytes");
        }
        return payload.clone();
    }
}
