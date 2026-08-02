package io.github.salyvn.omnipet.paper.management;

import java.util.UUID;
import java.util.concurrent.CompletionStage;

import io.github.salyvn.omnipet.core.release.InternalOutboxResult;

@FunctionalInterface
public interface PetReleaseOutboxRecoveryPort {
    CompletionStage<InternalOutboxResult> recover(UUID ownerId, UUID transactionId);
}
