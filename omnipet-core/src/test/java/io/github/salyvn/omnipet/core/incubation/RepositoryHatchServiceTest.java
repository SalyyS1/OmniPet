package io.github.salyvn.omnipet.core.incubation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.salyvn.omnipet.core.domain.incubation.IncubationStatus;
import io.github.salyvn.omnipet.core.persistence.FilePlayerStateRepository;
import io.github.salyvn.omnipet.core.storage.PetStorageLimits;

class RepositoryHatchServiceTest {
    @TempDir
    Path temporary;

    @Test
    void repositoryMutationsPersistOneRevisionAndClaimAtomically() throws Exception {
        UUID playerId = UUID.randomUUID();
        UUID incubationId = UUID.randomUUID();
        UUID actionToken = UUID.randomUUID();
        RepositoryHatchService hatches = new RepositoryHatchService(
                new FilePlayerStateRepository(temporary.resolve("players")));
        PetStorageLimits limits = new PetStorageLimits(1, 1, true);

        HatchResult started = hatches.start(
                playerId,
                0,
                incubationId,
                IncubationTestFixtures.egg(10_000),
                IncubationTestFixtures.registry(),
                123,
                limits);
        HatchResult reduced = hatches.reduce(
                playerId, started.state().revision(), incubationId, 100, actionToken);
        HatchResult duplicate = hatches.reduce(
                playerId, reduced.state().revision(), incubationId, 100, actionToken);
        HatchResult completed = hatches.complete(
                playerId, duplicate.state().revision(), incubationId, UUID.randomUUID());
        HatchResult claimed = hatches.claim(
                playerId, completed.state().revision(), incubationId, limits);
        HatchResult repeated = hatches.claim(
                playerId, claimed.state().revision(), incubationId, limits);

        assertEquals(1, started.state().revision());
        assertEquals(2, reduced.state().revision());
        assertEquals(2, duplicate.state().revision());
        assertEquals(3, completed.state().revision());
        assertEquals(4, claimed.state().revision());
        assertEquals(4, repeated.state().revision());
        assertEquals(IncubationStatus.CLAIMED, repeated.state().incubation().status());
        assertEquals(1, repeated.state().pets().size());
    }
}
