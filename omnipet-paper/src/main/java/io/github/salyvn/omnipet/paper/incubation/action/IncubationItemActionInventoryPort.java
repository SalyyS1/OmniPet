package io.github.salyvn.omnipet.paper.incubation.action;

import java.io.IOException;
import java.util.UUID;

import io.github.salyvn.omnipet.core.incubation.EggEscrowItemObservation;
import io.github.salyvn.omnipet.core.incubation.EggItemIdentity;
import io.github.salyvn.omnipet.paper.incubation.EggInventoryMutationResult;

public interface IncubationItemActionInventoryPort {
    boolean isMainThread();
    EggEscrowItemObservation observe(UUID playerId, EggItemIdentity item) throws IOException;
    EggInventoryMutationResult removeOne(UUID playerId, EggItemIdentity item) throws IOException;
    EggInventoryMutationResult refundOne(UUID playerId, EggItemIdentity item) throws IOException;
}
