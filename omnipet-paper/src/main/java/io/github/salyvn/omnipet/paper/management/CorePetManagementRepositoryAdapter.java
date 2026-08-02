package io.github.salyvn.omnipet.paper.management;

import java.io.IOException;
import java.util.Objects;
import java.util.UUID;

import io.github.salyvn.omnipet.core.domain.PlayerState;
import io.github.salyvn.omnipet.core.management.PetManagementResult;
import io.github.salyvn.omnipet.core.management.RepositoryPetManagementService;
import io.github.salyvn.omnipet.core.persistence.PlayerStateRepository;
import io.github.salyvn.omnipet.core.progression.ProgressionMutationContext;
import io.github.salyvn.omnipet.core.progression.RepositoryProgressionResult;
import io.github.salyvn.omnipet.core.progression.RepositoryProgressionService;
import io.github.salyvn.omnipet.core.progression.CultivationItemActionTransaction;
import io.github.salyvn.omnipet.core.release.ReleasePreview;
import io.github.salyvn.omnipet.core.release.ReleasePreviewResult;
import io.github.salyvn.omnipet.core.release.ReleaseResult;
import io.github.salyvn.omnipet.core.release.ReleaseService;

public final class CorePetManagementRepositoryAdapter implements PetManagementRepositoryPort {
    private final PlayerStateRepository players;
    private final RepositoryPetManagementService management;
    private final RepositoryProgressionService progression;
    private final ReleaseService release;

    public CorePetManagementRepositoryAdapter(
            PlayerStateRepository players,
            RepositoryPetManagementService management,
            RepositoryProgressionService progression,
            ReleaseService release) {
        this.players = Objects.requireNonNull(players, "player repository");
        this.management = Objects.requireNonNull(management, "pet management service");
        this.progression = Objects.requireNonNull(progression, "pet progression service");
        this.release = Objects.requireNonNull(release, "pet release service");
    }

    @Override public PlayerState snapshot(UUID ownerId) throws IOException { return players.snapshot(ownerId); }
    @Override public PetManagementResult favorite(UUID ownerId, long revision, UUID petId, boolean value) throws IOException {
        return management.favorite(ownerId, revision, petId, value);
    }
    @Override public PetManagementResult lock(UUID ownerId, long revision, UUID petId, boolean value) throws IOException {
        return management.lock(ownerId, revision, petId, value);
    }
    @Override public PetManagementResult move(UUID ownerId, long revision, UUID petId, int index) throws IOException {
        return management.move(ownerId, revision, petId, index);
    }
    @Override public RepositoryProgressionResult addExperience(
            UUID ownerId, long revision, UUID petId, double amount, ProgressionMutationContext context)
            throws IOException {
        return progression.addExperience(ownerId, revision, petId, amount, context);
    }
    @Override public RepositoryProgressionResult addExperience(
            UUID ownerId, long revision, UUID petId, double amount, ProgressionMutationContext context,
            CultivationItemActionTransaction action) throws IOException {
        return progression.addExperience(ownerId, revision, petId, amount, context, action);
    }
    @Override public RepositoryProgressionResult breakthrough(
            UUID ownerId, long revision, UUID petId, int level, int evolution, ProgressionMutationContext context)
            throws IOException {
        return progression.breakthrough(ownerId, revision, petId, level, evolution, context);
    }
    @Override public RepositoryProgressionResult breakthrough(
            UUID ownerId, long revision, UUID petId, int level, int evolution, ProgressionMutationContext context,
            CultivationItemActionTransaction action) throws IOException {
        return progression.breakthrough(ownerId, revision, petId, level, evolution, context, action);
    }
    @Override public boolean hasCultivationAction(
            UUID ownerId, UUID petId, CultivationItemActionTransaction action) throws IOException {
        return progression.hasAction(ownerId, petId, action);
    }
    @Override public void acknowledgeCultivationAction(
            UUID ownerId, UUID petId, CultivationItemActionTransaction action) throws IOException {
        progression.acknowledgeAction(ownerId, petId, action);
    }
    @Override public ReleasePreviewResult previewRelease(UUID transactionId, UUID ownerId, UUID petId) throws IOException {
        return release.preview(transactionId, ownerId, petId);
    }
    @Override public ReleaseResult release(ReleasePreview preview) throws IOException { return release.release(preview); }
}
