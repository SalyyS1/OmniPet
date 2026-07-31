package io.github.salyvn.omnipet.core.incubation;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface EggEscrowJournal {
    Optional<EggEscrowTransaction> find(UUID transactionId) throws IOException;

    EggEscrowCreateResult create(EggEscrowTransaction transaction) throws IOException;

    EggEscrowTransitionResult transition(
            UUID transactionId,
            Set<EggEscrowStage> expectedStages,
            EggEscrowStage targetStage) throws IOException;

    List<EggEscrowTransaction> findByPlayer(
            UUID playerId,
            Set<EggEscrowStage> stages,
            int limit) throws IOException;
}
