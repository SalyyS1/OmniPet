package io.github.salyvn.omnipet.paper.permission;

import java.util.Objects;
import java.util.function.Predicate;

import io.github.salyvn.omnipet.paper.config.Phase4PaperConfig;

/** Resolves legacy vault capacity by checking permission nodes one at a time until the first gap. */
public final class LegacyVaultPermissionResolver {
    public Resolution resolve(
            Phase4PaperConfig.LegacyPermission config,
            Predicate<String> hasPermission) {
        Objects.requireNonNull(config, "legacy permission config");
        Objects.requireNonNull(hasPermission, "permission predicate");
        if (!config.enabled()) return new Resolution(0, 0, null, StopReason.DISABLED);

        int scanLimit = Math.min(config.maxScan(), Phase4PaperConfig.MAX_LEGACY_PERMISSION_SCAN);
        for (int slot = 1; slot <= scanLimit; slot++) {
            String node = config.permissionNode(slot);
            if (!hasPermission.test(node)) {
                return new Resolution(slot - 1, slot, node, StopReason.FIRST_GAP);
            }
        }
        return new Resolution(scanLimit, scanLimit, null, StopReason.SCAN_CAP_REACHED);
    }

    public record Resolution(
            int consecutiveGrantedSlots,
            int checkedNodeCount,
            String firstMissingNode,
            StopReason stopReason) {
        public Resolution {
            if (consecutiveGrantedSlots < 0 || checkedNodeCount < 0) {
                throw new IllegalArgumentException("legacy permission diagnostics cannot be negative");
            }
            Objects.requireNonNull(stopReason, "legacy permission stop reason");
            switch (stopReason) {
                case DISABLED -> {
                    if (consecutiveGrantedSlots != 0 || checkedNodeCount != 0 || firstMissingNode != null) {
                        throw new IllegalArgumentException("disabled legacy permission diagnostics are inconsistent");
                    }
                }
                case FIRST_GAP -> {
                    if (firstMissingNode == null || checkedNodeCount != consecutiveGrantedSlots + 1) {
                        throw new IllegalArgumentException("first-gap legacy permission diagnostics are inconsistent");
                    }
                }
                case SCAN_CAP_REACHED -> {
                    if (firstMissingNode != null || checkedNodeCount != consecutiveGrantedSlots) {
                        throw new IllegalArgumentException("scan-cap legacy permission diagnostics are inconsistent");
                    }
                }
            }
        }
    }

    public enum StopReason {
        DISABLED,
        FIRST_GAP,
        SCAN_CAP_REACHED
    }
}
