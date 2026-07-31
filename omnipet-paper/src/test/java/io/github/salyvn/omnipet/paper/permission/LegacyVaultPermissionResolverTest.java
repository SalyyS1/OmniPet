package io.github.salyvn.omnipet.paper.permission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import io.github.salyvn.omnipet.paper.config.Phase4PaperConfig;
import io.github.salyvn.omnipet.paper.permission.LegacyVaultPermissionResolver.StopReason;

class LegacyVaultPermissionResolverTest {
    private final LegacyVaultPermissionResolver resolver = new LegacyVaultPermissionResolver();

    @Test
    void reportsNoGrantsAndFirstMissingNode() {
        LegacyVaultPermissionResolver.Resolution result = resolver.resolve(config(true, 5), ignored -> false);

        assertEquals(0, result.consecutiveGrantedSlots());
        assertEquals(1, result.checkedNodeCount());
        assertEquals("petstorage.slot.1", result.firstMissingNode());
        assertEquals(StopReason.FIRST_GAP, result.stopReason());
    }

    @Test
    void scansConsecutivelyAndStopsAtFirstGapWithoutCheckingLaterStrayGrants() {
        List<String> checked = new ArrayList<>();
        LegacyVaultPermissionResolver.Resolution result = resolver.resolve(config(true, 5), node -> {
            checked.add(node);
            return node.endsWith(".1") || node.endsWith(".3");
        });

        assertEquals(1, result.consecutiveGrantedSlots());
        assertEquals(List.of("petstorage.slot.1", "petstorage.slot.2"), checked);
        assertEquals("petstorage.slot.2", result.firstMissingNode());
        assertEquals(StopReason.FIRST_GAP, result.stopReason());
    }

    @Test
    void reachesConfiguredCapWithoutAllocatingAnEagerPermissionArray() {
        AtomicInteger checks = new AtomicInteger();
        int hardCap = Phase4PaperConfig.MAX_LEGACY_PERMISSION_SCAN;

        LegacyVaultPermissionResolver.Resolution result = resolver.resolve(
                config(true, hardCap),
                ignored -> {
                    checks.incrementAndGet();
                    return true;
                });

        assertEquals(hardCap, result.consecutiveGrantedSlots());
        assertEquals(hardCap, checks.get());
        assertNull(result.firstMissingNode());
        assertEquals(StopReason.SCAN_CAP_REACHED, result.stopReason());
        assertThrows(IllegalArgumentException.class, () -> config(true, hardCap + 1));
    }

    @Test
    void disabledResolverDoesNotProbePermissionPredicate() {
        AtomicInteger checks = new AtomicInteger();
        LegacyVaultPermissionResolver.Resolution result =
                resolver.resolve(config(false, 5), ignored -> checks.incrementAndGet() > 0);

        assertEquals(0, checks.get());
        assertEquals(0, result.checkedNodeCount());
        assertEquals(StopReason.DISABLED, result.stopReason());
    }

    @Test
    void disabledResolverAllowsZeroScanForLegacyZeroCapacity() {
        AtomicInteger checks = new AtomicInteger();
        LegacyVaultPermissionResolver.Resolution result =
                resolver.resolve(config(false, 0), ignored -> checks.incrementAndGet() > 0);

        assertEquals(0, checks.get());
        assertEquals(0, result.checkedNodeCount());
        assertEquals(StopReason.DISABLED, result.stopReason());
        assertThrows(IllegalArgumentException.class, () -> config(true, 0));
    }

    private static Phase4PaperConfig.LegacyPermission config(boolean enabled, int maxScan) {
        return new Phase4PaperConfig.LegacyPermission(enabled, "petstorage.slot.%s", maxScan);
    }
}
