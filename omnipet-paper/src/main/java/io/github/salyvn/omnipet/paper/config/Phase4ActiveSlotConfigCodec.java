package io.github.salyvn.omnipet.paper.config;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import io.github.salyvn.omnipet.core.economy.EconomyAmount;
import io.github.salyvn.omnipet.core.economy.EconomyProvider;
import io.github.salyvn.omnipet.core.storage.SlotEntitlementMode;
import io.github.salyvn.omnipet.core.storage.SlotEntitlementPolicy;
import io.github.salyvn.omnipet.core.storage.SlotEntitlementPrecedence;

final class Phase4ActiveSlotConfigCodec {
    private static final Set<String> ACTIVE_KEYS = Set.of(
            "multiPetEnabled", "base", "max", "entitlement", "unlocks");
    private static final Set<String> REQUIRED_ACTIVE_KEYS = Set.of("multiPetEnabled", "base", "max");
    private static final Set<String> ENTITLEMENT_KEYS = Set.of(
            "mode", "precedence", "luckPermsPermissionTemplate");
    private static final Set<String> UNLOCK_KEYS = Set.of("permission", "costs");

    Phase4PaperConfig.ActiveSlots parse(Map<String, Object> active) {
        requireKeys(active, REQUIRED_ACTIVE_KEYS, ACTIVE_KEYS, "storage.activeSlots");
        int max = integer(active.get("max"), "storage.activeSlots.max");
        Phase4PaperConfig.Entitlement entitlement = active.containsKey("entitlement")
                ? parseEntitlement(map(active.get("entitlement"), "storage.activeSlots.entitlement"))
                : Phase4PaperConfig.Entitlement.omniPetDefault();
        Map<Integer, Phase4PaperConfig.SlotUnlock> unlocks = active.containsKey("unlocks")
                ? parseUnlocks(map(active.get("unlocks"), "storage.activeSlots.unlocks"))
                : Map.of();
        return new Phase4PaperConfig.ActiveSlots(
                bool(active.get("multiPetEnabled"), "storage.activeSlots.multiPetEnabled"),
                integer(active.get("base"), "storage.activeSlots.base"),
                max,
                entitlement,
                unlocks);
    }

    Map<String, Object> encode(Phase4PaperConfig.ActiveSlots active) {
        LinkedHashMap<String, Object> entitlement = new LinkedHashMap<>();
        entitlement.put("mode", active.entitlement().policy().mode().name());
        entitlement.put("precedence", active.entitlement().policy().precedence().name());
        entitlement.put("luckPermsPermissionTemplate", active.entitlement().luckPermsPermissionTemplate());

        LinkedHashMap<String, Object> unlocks = new LinkedHashMap<>();
        new TreeMap<>(active.unlocks()).forEach((slot, unlock) -> {
            LinkedHashMap<String, Object> costs = new LinkedHashMap<>();
            unlock.costs().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> costs.put(entry.getKey().name(), entry.getValue().value()));
            LinkedHashMap<String, Object> encoded = new LinkedHashMap<>();
            encoded.put("permission", unlock.permission());
            encoded.put("costs", costs);
            unlocks.put(Integer.toString(slot), encoded);
        });

        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("multiPetEnabled", active.multiPetEnabled());
        result.put("base", active.base());
        result.put("max", active.max());
        result.put("entitlement", entitlement);
        result.put("unlocks", unlocks);
        return result;
    }

    private static Phase4PaperConfig.Entitlement parseEntitlement(Map<String, Object> values) {
        requireKeys(values, ENTITLEMENT_KEYS, ENTITLEMENT_KEYS, "storage.activeSlots.entitlement");
        SlotEntitlementMode mode = enumValue(
                SlotEntitlementMode.class,
                values.get("mode"),
                "storage.activeSlots.entitlement.mode");
        SlotEntitlementPrecedence precedence = enumValue(
                SlotEntitlementPrecedence.class,
                values.get("precedence"),
                "storage.activeSlots.entitlement.precedence");
        return new Phase4PaperConfig.Entitlement(
                new SlotEntitlementPolicy(mode, precedence),
                text(values.get("luckPermsPermissionTemplate"),
                        "storage.activeSlots.entitlement.luckPermsPermissionTemplate"));
    }

    private static Map<Integer, Phase4PaperConfig.SlotUnlock> parseUnlocks(Map<String, Object> values) {
        LinkedHashMap<Integer, Phase4PaperConfig.SlotUnlock> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            int slot;
            try {
                slot = Integer.parseInt(entry.getKey());
            } catch (NumberFormatException failure) {
                throw new IllegalArgumentException("active slot unlock key must be an integer", failure);
            }
            String path = "storage.activeSlots.unlocks." + entry.getKey();
            Map<String, Object> unlock = map(entry.getValue(), path);
            requireKeys(unlock, Set.of("costs"), UNLOCK_KEYS, path);
            String permission = unlock.containsKey("permission")
                    ? optionalText(unlock.get("permission"), path + ".permission")
                    : "";
            Map<String, Object> costs = map(unlock.get("costs"), path + ".costs");
            LinkedHashMap<EconomyProvider, EconomyAmount> amounts = new LinkedHashMap<>();
            costs.forEach((providerName, rawAmount) -> {
                EconomyProvider provider = enumValue(EconomyProvider.class, providerName, path + ".costs");
                amounts.put(provider, new EconomyAmount(provider, decimal(rawAmount, path + ".costs." + providerName)));
            });
            if (result.put(slot, new Phase4PaperConfig.SlotUnlock(permission, amounts)) != null) {
                throw new IllegalArgumentException("duplicate active slot unlock: " + slot);
            }
        }
        return result;
    }

    private static void requireKeys(
            Map<String, Object> values,
            Set<String> required,
            Set<String> allowed,
            String path) {
        for (String key : required) {
            if (!values.containsKey(key)) throw new IllegalArgumentException(path + "." + key + " is required");
        }
        for (String key : values.keySet()) {
            if (!allowed.contains(key)) throw new IllegalArgumentException("unknown config key: " + path + "." + key);
        }
    }

    private static Map<String, Object> map(Object value, String path) {
        if (!(value instanceof Map<?, ?> source)) throw new IllegalArgumentException(path + " must be a map");
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, entryValue) -> {
            if (!(key instanceof String textKey)) throw new IllegalArgumentException(path + " contains a non-text key");
            result.put(textKey, entryValue);
        });
        return result;
    }

    private static boolean bool(Object value, String path) {
        if (value instanceof Boolean result) return result;
        throw new IllegalArgumentException(path + " must be true or false");
    }

    private static int integer(Object value, String path) {
        try {
            if (value instanceof Byte number) return number.intValue();
            if (value instanceof Short number) return number.intValue();
            if (value instanceof Integer number) return number;
            if (value instanceof Long number) return Math.toIntExact(number);
            if (value instanceof BigInteger number) return number.intValueExact();
        } catch (ArithmeticException failure) {
            throw new IllegalArgumentException(path + " is outside the integer range", failure);
        }
        throw new IllegalArgumentException(path + " must be an integer");
    }

    private static BigDecimal decimal(Object value, String path) {
        try {
            if (value instanceof BigDecimal decimal) return decimal;
            if (value instanceof BigInteger integer) return new BigDecimal(integer);
            if (value instanceof Number number) return new BigDecimal(number.toString());
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException(path + " must be a decimal number", failure);
        }
        throw new IllegalArgumentException(path + " must be a decimal number");
    }

    private static String text(Object value, String path) {
        if (value instanceof String result && !result.isBlank()) return result;
        throw new IllegalArgumentException(path + " must be non-blank text");
    }

    private static String optionalText(Object value, String path) {
        if (value == null) return "";
        if (value instanceof String result) return result;
        throw new IllegalArgumentException(path + " must be text");
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, Object value, String path) {
        String text = value instanceof String string ? string : String.valueOf(value);
        try {
            return Enum.valueOf(type, text);
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException(path + " has unsupported value: " + text, failure);
        }
    }
}
