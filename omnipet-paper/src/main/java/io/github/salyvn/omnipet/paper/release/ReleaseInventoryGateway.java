package io.github.salyvn.omnipet.paper.release;

import java.util.List;
import java.util.UUID;

@FunctionalInterface
public interface ReleaseInventoryGateway {
    ReleaseInventoryClaimResult claim(UUID playerId, UUID transactionId, List<PaperMaterialReward> rewards);
}
