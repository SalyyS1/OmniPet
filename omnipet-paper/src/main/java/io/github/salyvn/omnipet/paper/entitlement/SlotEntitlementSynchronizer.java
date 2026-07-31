package io.github.salyvn.omnipet.paper.entitlement;

import java.util.Objects;
import java.util.UUID;

import io.github.salyvn.omnipet.core.storage.SlotEntitlementMode;
import io.github.salyvn.omnipet.paper.config.Phase4PaperConfig;

public final class SlotEntitlementSynchronizer {
    private final PaperLuckPermsEntitlementRegistry luckPerms;

    public SlotEntitlementSynchronizer(PaperLuckPermsEntitlementRegistry luckPerms) {
        this.luckPerms = Objects.requireNonNull(luckPerms, "LuckPerms registry");
    }

    public boolean available(Phase4PaperConfig.Entitlement entitlement) {
        return entitlement.policy().mode() == SlotEntitlementMode.OMNIPET
                || luckPerms.adapter().isPresent();
    }

    public String unavailableReason(Phase4PaperConfig.Entitlement entitlement) {
        return entitlement.policy().mode() == SlotEntitlementMode.OMNIPET
                ? ""
                : luckPerms.diagnostic();
    }

    public SlotEntitlementSyncResult grant(
            UUID playerId,
            int slot,
            Phase4PaperConfig.Entitlement entitlement) {
        if (entitlement.policy().mode() == SlotEntitlementMode.OMNIPET) {
            return new SlotEntitlementSyncResult(
                    SlotEntitlementSyncResult.Status.ALREADY_APPLIED,
                    "OmniPet entitlement is authoritative");
        }
        return luckPerms.adapter()
                .map(adapter -> adapter.grant(playerId, entitlement.permissionNode(slot)))
                .orElseGet(() -> new SlotEntitlementSyncResult(
                        SlotEntitlementSyncResult.Status.UNAVAILABLE,
                        luckPerms.diagnostic()));
    }
}
