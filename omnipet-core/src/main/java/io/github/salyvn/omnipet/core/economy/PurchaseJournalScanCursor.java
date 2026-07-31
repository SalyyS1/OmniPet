package io.github.salyvn.omnipet.core.economy;

import java.util.Locale;
import java.util.UUID;

record PurchaseJournalScanCursor(String prefix, String afterCompactId) {
    private static final String HEX = "0123456789abcdef";

    static PurchaseJournalScanCursor parse(String cursorText) {
        if (cursorText == null) return new PurchaseJournalScanCursor("", null);
        if (cursorText.isBlank() || cursorText.length() > 128) throw invalid();
        String[] parts = cursorText.toLowerCase(Locale.ROOT).split("\\.", -1);
        if (parts.length != 3 || !parts[0].equals("v1")) throw invalid();
        String prefix = parts[1].equals("root") ? "" : parts[1];
        if ((!prefix.isEmpty() && !isLowerHex(prefix)) || prefix.length() > 32) throw invalid();
        String after = parts[2].equals("start") ? null : parts[2];
        if (after != null && (after.length() != 32 || !isLowerHex(after) || !after.startsWith(prefix))) {
            throw invalid();
        }
        return new PurchaseJournalScanCursor(prefix, after);
    }

    String childToken() {
        String childPrefix = afterCompactId == null
                ? prefix + '0'
                : prefix + afterCompactId.charAt(prefix.length());
        return token(childPrefix, afterCompactId);
    }

    String continuationToken(String compactId) {
        return token(prefix, compactId);
    }

    String nextPrefixToken() {
        if (prefix.isEmpty()) return null;
        for (int index = prefix.length() - 1; index >= 0; index--) {
            int value = HEX.indexOf(prefix.charAt(index));
            if (value < HEX.length() - 1) {
                return token(prefix.substring(0, index) + HEX.charAt(value + 1), null);
            }
        }
        return null;
    }

    String uuidGlob() {
        StringBuilder glob = new StringBuilder(128);
        for (int index = 0; index < 32; index++) {
            if (index == 8 || index == 12 || index == 16 || index == 20) glob.append('-');
            if (index < prefix.length()) {
                glob.append(prefix.charAt(index));
            } else {
                glob.append("[0-9a-f]");
            }
        }
        return glob.append(".yml").toString();
    }

    static String compactId(String name) {
        if (name == null || name.length() != 40 || !name.endsWith(".yml")) return null;
        String idText = name.substring(0, 36);
        try {
            UUID id = UUID.fromString(idText);
            return id.toString().equals(idText) ? idText.replace("-", "") : null;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static String token(String prefix, String afterCompactId) {
        return "v1." + (prefix.isEmpty() ? "root" : prefix) + "."
                + (afterCompactId == null ? "start" : afterCompactId);
    }

    private static boolean isLowerHex(String value) {
        if (value.isEmpty()) return false;
        for (int index = 0; index < value.length(); index++) {
            if (HEX.indexOf(value.charAt(index)) < 0) return false;
        }
        return true;
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("journal scan cursor is invalid");
    }
}
