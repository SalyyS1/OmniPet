package io.github.salyvn.omnipet.paper.permission;

import java.util.Objects;
import java.util.function.Predicate;

import io.github.salyvn.omnipet.core.storage.PetStorageLimits;
import io.github.salyvn.omnipet.paper.config.Phase4PaperConfig;

/** Builds core limits from a detached config snapshot and live, non-persisted permission observations. */
public final class PaperStorageLimitsResolver {
    private final Phase4PaperConfig config;
    private final LegacyVaultPermissionResolver legacyPermissions;

    public PaperStorageLimitsResolver(Phase4PaperConfig config) {
        this(config, new LegacyVaultPermissionResolver());
    }

    public PaperStorageLimitsResolver(
            Phase4PaperConfig config,
            LegacyVaultPermissionResolver legacyPermissions) {
        this.config = Objects.requireNonNull(config, "Phase 4 Paper config");
        this.legacyPermissions = Objects.requireNonNull(legacyPermissions, "legacy permission resolver");
    }

    public Resolution resolve(Predicate<String> hasPermission) {
        LegacyVaultPermissionResolver.Resolution legacy =
                legacyPermissions.resolve(config.vault().legacyPermission(), hasPermission);
        int legacyCapacity = Math.min(config.vault().maxCapacity(), legacy.consecutiveGrantedSlots());
        int configuredVaultCapacity = Math.max(config.vault().baseCapacity(), legacyCapacity);
        int externalActiveSlots = resolveExternalActiveSlots(hasPermission);
        PetStorageLimits limits = new PetStorageLimits(
                configuredVaultCapacity,
                config.activeSlots().base(),
                config.activeSlots().max(),
                config.activeSlots().multiPetEnabled(),
                externalActiveSlots,
                config.activeSlots().entitlement().policy());
        return new Resolution(limits, legacy);
    }

    public Phase4PaperConfig.ActiveSlots activeSlots() {
        return config.activeSlots();
    }

    private int resolveExternalActiveSlots(Predicate<String> hasPermission) {
        int slots = config.activeSlots().base();
        switch (config.activeSlots().entitlement().policy().mode()) {
            case OMNIPET -> {
                return slots;
            }
            case LUCKPERMS, HYBRID -> {
                for (int slot = slots + 1; slot <= config.activeSlots().max(); slot++) {
                    if (!hasPermission.test(config.activeSlots().entitlement().permissionNode(slot))) break;
                    slots = slot;
                }
                return slots;
            }
        }
        throw new IllegalStateException("unsupported slot entitlement mode");
    }

    public record Resolution(
            PetStorageLimits limits,
            LegacyVaultPermissionResolver.Resolution legacyPermission) {
        public Resolution {
            Objects.requireNonNull(limits, "storage limits");
            Objects.requireNonNull(legacyPermission, "legacy permission diagnostics");
        }
    }
}
