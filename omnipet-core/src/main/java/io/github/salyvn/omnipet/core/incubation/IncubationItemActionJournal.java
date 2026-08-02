package io.github.salyvn.omnipet.core.incubation;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface IncubationItemActionJournal {
    Optional<IncubationItemActionTransaction> find(UUID actionToken) throws IOException;

    IncubationItemActionTransaction create(IncubationItemActionTransaction transaction) throws IOException;

    IncubationItemActionTransaction transition(
            UUID actionToken,
            Set<IncubationItemActionStage> expected,
            IncubationItemActionStage target) throws IOException;
}
