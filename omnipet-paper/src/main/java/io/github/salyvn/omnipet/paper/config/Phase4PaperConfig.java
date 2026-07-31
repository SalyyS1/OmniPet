package io.github.salyvn.omnipet.paper.config;

import java.util.Objects;
import java.util.regex.Pattern;

import io.github.salyvn.omnipet.core.storage.PetStorageLimits;

/** Detached operational configuration used by the Phase 4 Paper boundary. */
public record Phase4PaperConfig(Vault vault, ActiveSlots activeSlots) {
    public static final int MAX_OPERATOR_VAULT_CAPACITY =
            Math.min(PetStorageLimits.MAX_VAULT_CAPACITY, 10_000);
    public static final int MAX_LEGACY_PERMISSION_SCAN = MAX_OPERATOR_VAULT_CAPACITY;

    public Phase4PaperConfig {
        vault = Objects.requireNonNull(vault, "vault config");
        activeSlots = Objects.requireNonNull(activeSlots, "active slot config");
    }

    public record Vault(int baseCapacity, int maxCapacity, LegacyPermission legacyPermission) {
        public Vault {
            if (baseCapacity < 0 || baseCapacity > MAX_OPERATOR_VAULT_CAPACITY) {
                throw new IllegalArgumentException("storage.vault.baseCapacity is outside the supported range");
            }
            if (maxCapacity < 0 || maxCapacity > MAX_OPERATOR_VAULT_CAPACITY) {
                throw new IllegalArgumentException("storage.vault.maxCapacity is outside the supported range");
            }
            if (baseCapacity > maxCapacity) {
                throw new IllegalArgumentException("storage.vault.baseCapacity cannot exceed maxCapacity");
            }
            legacyPermission = Objects.requireNonNull(legacyPermission, "legacy permission config");
            if (legacyPermission.maxScan() > maxCapacity) {
                throw new IllegalArgumentException("storage.vault.legacyPermission.maxScan cannot exceed maxCapacity");
            }
        }
    }

    public record LegacyPermission(boolean enabled, String template, int maxScan) {
        private static final Pattern SAFE_PERMISSION_NODE =
                Pattern.compile("[a-z0-9]+(?:[._-][a-z0-9]+)*");

        public LegacyPermission {
            if (maxScan < 0 || maxScan > MAX_LEGACY_PERMISSION_SCAN) {
                throw new IllegalArgumentException(
                        "storage.vault.legacyPermission.maxScan is outside the supported range");
            }
            if (enabled && maxScan == 0) {
                throw new IllegalArgumentException(
                        "storage.vault.legacyPermission.maxScan must be positive when enabled");
            }
            template = validateTemplate(template, Math.max(1, maxScan));
        }

        public String permissionNode(int slot) {
            if (slot < 1 || slot > maxScan) {
                throw new IllegalArgumentException("legacy permission slot is outside the configured scan range");
            }
            return expand(template, slot);
        }

        private static String validateTemplate(String value, int maxScan) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("storage.vault.legacyPermission.template is required");
            }
            int placeholder = value.indexOf("%s");
            if (placeholder < 0 || placeholder != value.lastIndexOf("%s")) {
                throw new IllegalArgumentException("legacy permission template must contain exactly one %s placeholder");
            }
            String literal = value.substring(0, placeholder) + value.substring(placeholder + 2);
            if (literal.indexOf('%') >= 0) {
                throw new IllegalArgumentException("legacy permission template contains an unsupported placeholder");
            }
            String first = expand(value, 1);
            String second = expand(value, 2);
            validateNode(first);
            validateNode(second);
            validateNode(expand(value, maxScan));
            if (first.equals(second)) {
                throw new IllegalArgumentException("legacy permission template must produce distinct permission nodes");
            }
            return value;
        }

        private static void validateNode(String node) {
            if (node.length() > 128 || !SAFE_PERMISSION_NODE.matcher(node).matches()) {
                throw new IllegalArgumentException("legacy permission template produces an unsafe permission node");
            }
        }

        private static String expand(String template, int slot) {
            int placeholder = template.indexOf("%s");
            return template.substring(0, placeholder) + slot + template.substring(placeholder + 2);
        }
    }

    public record ActiveSlots(boolean multiPetEnabled, int base, int max) {
        public ActiveSlots {
            if (base < 1 || base > PetStorageLimits.MAX_ACTIVE_SLOT_COUNT) {
                throw new IllegalArgumentException("storage.activeSlots.base is outside the supported range");
            }
            if (max < 1 || max > PetStorageLimits.MAX_ACTIVE_SLOT_COUNT) {
                throw new IllegalArgumentException("storage.activeSlots.max is outside the supported range");
            }
            if (base > max) {
                throw new IllegalArgumentException("storage.activeSlots.base cannot exceed max");
            }
        }
    }
}
