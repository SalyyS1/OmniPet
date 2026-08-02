package io.github.salyvn.omnipet.paper.management;

import java.util.Objects;

import io.github.salyvn.omnipet.core.domain.PetInstance;
import io.github.salyvn.omnipet.core.domain.PlayerState;
import io.github.salyvn.omnipet.core.management.PetManagementMetadata;
import io.github.salyvn.omnipet.core.progression.PetProgressionProjection;
import io.github.salyvn.omnipet.core.progression.ProgressionState;
import io.github.salyvn.omnipet.core.release.ReleasePreview;

public record PetManagementViewModel(
        PetManagementSession session,
        PlayerState ownerState,
        PetInstance pet,
        int petIndex,
        boolean active,
        PetManagementMetadata metadata,
        ProgressionState progression,
        ReleasePreview releasePreview) {
    public PetManagementViewModel {
        Objects.requireNonNull(session, "management view session");
        Objects.requireNonNull(ownerState, "management view owner state");
        Objects.requireNonNull(pet, "management view pet");
        Objects.requireNonNull(metadata, "management view metadata");
        Objects.requireNonNull(progression, "management view progression");
        if (!ownerState.playerId().equals(session.ownerId())
                || ownerState.revision() != session.expectedRevision()
                || !pet.id().equals(session.petId())
                || petIndex < 0
                || petIndex >= ownerState.pets().size()
                || !ownerState.pets().get(petIndex).id().equals(pet.id())) {
            throw new IllegalArgumentException("management view identity or revision is stale");
        }
        if (releasePreview != null
                && (!releasePreview.playerId().equals(session.ownerId())
                || !releasePreview.petId().equals(session.petId())
                || releasePreview.expectedRevision() != session.expectedRevision())) {
            throw new IllegalArgumentException("release preview does not match management session");
        }
    }

    public static PetManagementViewModel create(
            PetManagementSession session,
            PlayerState state,
            ReleasePreview preview) {
        int index = -1;
        for (int candidate = 0; candidate < state.pets().size(); candidate++) {
            if (state.pets().get(candidate).id().equals(session.petId())) {
                index = candidate;
                break;
            }
        }
        if (index < 0) throw new IllegalArgumentException("management pet UUID is no longer owned");
        PetInstance pet = state.pets().get(index);
        return new PetManagementViewModel(
                session,
                state,
                pet,
                index,
                state.desiredActivePetIds().contains(pet.id()),
                PetManagementMetadata.read(pet),
                PetProgressionProjection.read(pet, 0, 0),
                preview);
    }
}
