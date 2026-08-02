package io.github.salyvn.omnipet.core.progression;

import java.io.IOException;
import java.util.Optional;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface CultivationItemActionJournal {
    Optional<CultivationItemActionTransaction> find(UUID actionToken) throws IOException;

    CultivationItemActionTransaction create(CultivationItemActionTransaction transaction) throws IOException;

    CultivationItemActionTransaction transition(
            UUID actionToken,
            Set<CultivationItemActionStage> expected,
            CultivationItemActionStage target) throws IOException;

    List<CultivationItemActionTransaction> scan(
            UUID playerId,
            Set<CultivationItemActionStage> stages,
            int limit) throws IOException;
}
