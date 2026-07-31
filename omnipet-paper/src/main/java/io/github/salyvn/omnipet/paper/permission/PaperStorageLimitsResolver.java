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
        PetStorageLimits limits = new PetStorageLimits(
                configuredVaultCapacity,
                config.activeSlots().base(),
                config.activeSlots().max(),
                config.activeSlots().multiPetEnabled());
        return new Resolution(limits, legacy);
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
