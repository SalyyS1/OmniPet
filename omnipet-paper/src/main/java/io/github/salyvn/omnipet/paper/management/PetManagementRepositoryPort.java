package io.github.salyvn.omnipet.paper.management;

import java.io.IOException;
import java.util.UUID;

import io.github.salyvn.omnipet.core.domain.PlayerState;
import io.github.salyvn.omnipet.core.management.PetManagementResult;
import io.github.salyvn.omnipet.core.progression.ProgressionMutationContext;
import io.github.salyvn.omnipet.core.progression.RepositoryProgressionResult;
import io.github.salyvn.omnipet.core.progression.CultivationItemActionTransaction;
import io.github.salyvn.omnipet.core.release.ReleasePreview;
import io.github.salyvn.omnipet.core.release.ReleasePreviewResult;
import io.github.salyvn.omnipet.core.release.ReleaseResult;

public interface PetManagementRepositoryPort {
    PlayerState snapshot(UUID ownerId) throws IOException;

    PetManagementResult favorite(UUID ownerId, long revision, UUID petId, boolean favorite) throws IOException;

    PetManagementResult lock(UUID ownerId, long revision, UUID petId, boolean locked) throws IOException;

    PetManagementResult move(UUID ownerId, long revision, UUID petId, int targetIndex) throws IOException;

    RepositoryProgressionResult addExperience(
            UUID ownerId, long revision, UUID petId, double amount, ProgressionMutationContext context)
            throws IOException;

    default RepositoryProgressionResult addExperience(
            UUID ownerId, long revision, UUID petId, double amount, ProgressionMutationContext context,
            CultivationItemActionTransaction action) throws IOException {
        return addExperience(ownerId, revision, petId, amount, context);
    }

    RepositoryProgressionResult breakthrough(
            UUID ownerId, long revision, UUID petId, int requiredLevel, int requiredEvolution,
            ProgressionMutationContext context) throws IOException;

    default RepositoryProgressionResult breakthrough(
            UUID ownerId, long revision, UUID petId, int requiredLevel, int requiredEvolution,
            ProgressionMutationContext context, CultivationItemActionTransaction action) throws IOException {
        return breakthrough(ownerId, revision, petId, requiredLevel, requiredEvolution, context);
    }

    default boolean hasCultivationAction(
            UUID ownerId, UUID petId, CultivationItemActionTransaction action) throws IOException {
        return false;
    }

    default void acknowledgeCultivationAction(
            UUID ownerId, UUID petId, CultivationItemActionTransaction action) throws IOException {}

    ReleasePreviewResult previewRelease(UUID transactionId, UUID ownerId, UUID petId) throws IOException;

    ReleaseResult release(ReleasePreview preview) throws IOException;
}
